package tw.nekomimi.nekogram.translate.source

import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.telegram.messenger.BuildVars
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.TranslateAlert2
import tw.nekomimi.nekogram.llm.LlmConfig
import tw.nekomimi.nekogram.llm.net.OpenAICompatClient
import tw.nekomimi.nekogram.llm.net.VertexGeminiClient
import tw.nekomimi.nekogram.llm.preset.PresetRegistry
import tw.nekomimi.nekogram.translate.HTMLKeeper
import tw.nekomimi.nekogram.translate.Translator
import tw.nekomimi.nekogram.translate.code2Locale
import tw.nekomimi.nekogram.utils.AndroidUtil
import xyz.nextalone.nagram.NaConfig
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

object LLMTranslator : Translator {

    private const val MAX_RETRY = 3
    private const val BASE_WAIT = 1000L
    private val contextMessageLimitOptions = intArrayOf(1, 3, 5, 7, 10)
    private val translationContextThreadLocal = ThreadLocal<String?>()

    private class TranslationContextElement(
        private val translationContext: String?
    ) : ThreadContextElement<String?> {
        companion object Key : CoroutineContext.Key<TranslationContextElement>

        override val key: CoroutineContext.Key<TranslationContextElement>
            get() = Key

        override fun updateThreadContext(context: CoroutineContext): String? {
            val oldState = translationContextThreadLocal.get()
            translationContextThreadLocal.set(translationContext)
            return oldState
        }

        override fun restoreThreadContext(context: CoroutineContext, oldState: String?) {
            translationContextThreadLocal.set(oldState)
        }
    }

    @JvmStatic
    fun getContextMessageLimit(): Int {
        val index = NaConfig.llmContextSize.Int()
        return contextMessageLimitOptions.getOrElse(index) { 5 }
    }

    suspend fun <T> withTranslationContext(context: String?, block: suspend () -> T): T {
        return withContext(TranslationContextElement(context)) { block() }
    }

    private fun currentTranslationContext(): String? = translationContextThreadLocal.get()

    private var apiKeys: List<String> = emptyList()
    private val apiKeyIndex = AtomicInteger(0)
    private var currentProvider = -1
    private var cachedKeyString: String? = null

    private fun updateApiKeys() {
        val llmProvider = NaConfig.llmProviderPreset.Int()
        val keyConfig = LlmConfig.getApiKeyConfigItem(llmProvider)
        val key = keyConfig.String()

        if (currentProvider == llmProvider && cachedKeyString == key) {
            return
        }

        apiKeys = if (!key.isNullOrBlank()) {
            key.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        } else {
            emptyList()
        }
        cachedKeyString = key
        currentProvider = llmProvider
        apiKeyIndex.set(0)
    }

    private fun getNextApiKey(): String? {
        updateApiKeys()
        if (apiKeys.isEmpty()) {
            return null
        }

        val index = apiKeyIndex.getAndIncrement() % apiKeys.size
        if (apiKeyIndex.get() >= apiKeys.size * 2) {
            apiKeyIndex.set(index + 1)
        }
        return apiKeys[index]
    }

    override suspend fun doTranslate(
        from: String,
        to: String,
        query: String,
        entities: ArrayList<TLRPC.MessageEntity>
    ): TLRPC.TL_textWithEntities {
        var retryCount = 0

        val originalText = TLRPC.TL_textWithEntities()
        originalText.text = query
        originalText.entities = entities

        val textToTranslate = if (entities.isNotEmpty()) HTMLKeeper.entitiesToHtml(
            query,
            entities,
            false
        ) else query

        while (retryCount < MAX_RETRY) {
            try {
                val translatedText = doLLMTranslate(to.code2Locale.displayName, textToTranslate)
                return if (entities.isNotEmpty()) {
                    val resultPair = HTMLKeeper.htmlToEntities(translatedText, entities, false)
                    val finalText = TLRPC.TL_textWithEntities().apply {
                        text = resultPair.first
                        this.entities = resultPair.second
                    }
                    TranslateAlert2.preprocess(originalText, finalText)
                } else {
                    TLRPC.TL_textWithEntities().apply {
                        text = translatedText
                    }
                }
            } catch (_: RateLimitException) {
                retryCount++
                val actualWaitTimeMillis = backoffDelayWithJitterMillis(retryCount)
                if (BuildVars.LOGS_ENABLED) {
                    AndroidUtil.showErrorDialog("Rate limited, retrying in ${actualWaitTimeMillis}ms, retry count: $retryCount")
                }
                delay(actualWaitTimeMillis.milliseconds)
            } catch (e: IOException) {
                retryCount++
                if (BuildVars.LOGS_ENABLED) {
                    AndroidUtil.showErrorDialog(e)
                }
                if (retryCount >= MAX_RETRY) {
                    if (BuildVars.LOGS_ENABLED) {
                        AndroidUtil.showErrorDialog("Max retry count reached due to network errors, falling back to GoogleAppTranslator")
                    }
                    return GoogleAppTranslator.doTranslate(from, to, query, entities)
                }
                val waitTimeMillis = backoffDelayMillis(retryCount)
                delay(waitTimeMillis.milliseconds)
            } catch (e: UnsupportedOperationException) {
                throw e
            } catch (e: Exception) {
                if (BuildVars.LOGS_ENABLED) {
                    AndroidUtil.showErrorDialog("Error during LLM translation, falling back to GoogleAppTranslator.\n$e")
                }
                return GoogleAppTranslator.doTranslate(from, to, query, entities)
            }
        }
        if (BuildVars.LOGS_ENABLED) {
            AndroidUtil.showErrorDialog("Max retry count reached, falling back to GoogleAppTranslator")
        }
        return GoogleAppTranslator.doTranslate(from, to, query, entities)
    }

    @Throws(IOException::class, RateLimitException::class, UnsupportedOperationException::class)
    private fun doLLMTranslate(to: String, query: String): String {
        val apiKey = getNextApiKey() ?: throw UnsupportedOperationException(getString(R.string.ApiKeyNotSet))
        val apiKeyForLog = apiKey.takeLast(2)
        FileLog.d("createPost: Bearer $apiKeyForLog")

        val llmProviderPreset = NaConfig.llmProviderPreset.Int()
        val apiUrl = LlmConfig.getEffectiveBaseUrl(llmProviderPreset)
        val model = LlmConfig.getEffectiveModelName(llmProviderPreset)

        val configuredSystemPrompt = NaConfig.llmSystemPrompt.String()
        val hasCustomSystemPrompt = !configuredSystemPrompt.isNullOrEmpty()
        val sysPrompt = if (hasCustomSystemPrompt) {
            buildSystemPromptWithCustomInstructions(configuredSystemPrompt!!)
        } else {
            generateSystemPrompt()
        }
        val userPrompt = NaConfig.llmUserPrompt.String()?.takeIf { it.isNotEmpty() }
            ?.replace("@text", query)
            ?.replace("@toLang", to)
            ?: generatePrompt(query, to)

        val contextPrompt = currentTranslationContext()
            ?.takeIf { NaConfig.llmUseContext.Bool() }
            ?.takeIf { it.isNotBlank() }
            ?.let { buildContextPrompt(it) }

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", sysPrompt)
            })
            if (contextPrompt != null) {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", contextPrompt)
                })
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", userPrompt)
            })
        }
        FileLog.d("Requesting LLM API with model: $model, messages: $messages")

        val response = if (llmProviderPreset == PresetRegistry.GOOGLE_AGENT_PLATFORM) {
            VertexGeminiClient.generateContent(apiUrl, apiKey, model, messages)
        } else {
            OpenAICompatClient.chatCompletions(apiUrl, apiKey, model, messages)
        }

        if (!response.isSuccess) {
            val code = response.httpCode()
            val error = response.error() ?: getString(R.string.UnknownError)
            val apiKeyNotSet = getString(R.string.ApiKeyNotSet)
            when {
                code == 429 -> throw RateLimitException("LLM API rate limit exceeded")
                code in 400..499 || (code == 0 && error == apiKeyNotSet) -> throw UnsupportedOperationException(error)
                else -> throw IOException(error)
            }
        }

        val rawResult = response.data()
            ?.takeIf { it.isNotEmpty() }
            ?: throw IOException("LLM API returned empty content")
        return cleanTranslationResult(rawResult)
    }

    private fun cleanTranslationResult(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("<TEXT>", ignoreCase = true) && text.endsWith("</TEXT>", ignoreCase = true)) {
            text = text.substring(6, text.length - 7).trim()
        }
        if (text.startsWith("```") && text.endsWith("```")) {
            val lines = text.lines()
            if (lines.size >= 2) {
                text = lines.subList(1, lines.size - 1).joinToString("\n").trim()
            }
        }
        return text
    }

    private fun backoffDelayMillis(retryCount: Int): Long {
        val exponent = (retryCount - 1).coerceAtLeast(0)
        return BASE_WAIT * (1L shl exponent)
    }

    private fun backoffDelayWithJitterMillis(retryCount: Int): Long {
        val waitTimeMillis = backoffDelayMillis(retryCount)
        val jitterBound = waitTimeMillis / 2
        if (jitterBound <= 0L) {
            return waitTimeMillis
        }
        return waitTimeMillis + Random.nextLong(jitterBound)
    }

    private fun generatePrompt(query: String, to: String): String {
        return "Translate to $to:\n$query"
    }

    private fun buildContextPrompt(context: String): String {
        return "[Context for reference]\n$context"
    }

    private const val CORE_SYSTEM_PROMPT = """You are a master native multilingual translator, dedicated to Master NkBe.
Translate chat messages into the target language with high fluency, natural conversational flow, and authentic tone (slang, idioms, emotions, formality).
Rules:
1. Output ONLY the direct translation. Never add explanations, notes, or prefixes like "Translation:".
2. Preserve all tags (HTML/Markdown), @mentions, #tags, URLs, emojis, numbers, and code blocks exactly as in the source.
3. If the input is already in the target language or has no translatable natural text, return it as-is."""

    private fun generateSystemPrompt(): String {
        return CORE_SYSTEM_PROMPT
    }

    private fun buildSystemPromptWithCustomInstructions(customPrompt: String): String {
        return """
        $CORE_SYSTEM_PROMPT

        Custom Instructions:
        $customPrompt
        """.trimIndent()
    }

    class RateLimitException(message: String) : Exception(message)
}
