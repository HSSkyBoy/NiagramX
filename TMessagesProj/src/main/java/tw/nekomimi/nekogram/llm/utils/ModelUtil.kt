package tw.nekomimi.nekogram.llm.utils

import org.json.JSONObject
import tw.nekomimi.nekogram.llm.preset.PresetRegistry
import xyz.nextalone.nagram.ThinkingLevel
import java.util.Locale

object ModelUtil {

    private val nonTextGenerationModelKeywords = listOf(
        "-live-",
        "-research",
        "-search",
        "antigravity-",
        "aqa",
        "asr-",
        "audio",
        "bge-",
        "chirp-",
        "computer-use",
        "csm-",
        "deepgram", // provider
        "e5-",
        "embed",
        "embedding",
        "flux",
        "gemini-omni",
        "gte-",
        "hailuo",
        "happyhorse",
        "i2v",
        "image",
        "imagen",
        "imagine",
        "kling-v",
        "kokoro-",
        "krea-",
        "lyria",
        "minilm-",
        "minimax-h3",
        "moderation",
        "nano-banana",
        "orpheus-",
        "parakeet-",
        "perplexity", // provider
        "quiverai", // provider
        "r2v",
        "realtime",
        "recraft",
        "rerank",
        "riverflow",
        "robotics",
        "runway", // provider
        "seedance",
        "seedream",
        "sentence-transformers", // provider
        "sora",
        "speech",
        "stt",
        "t2v",
        "transcri",
        "tts",
        "veo-",
        "video",
        "voice",
        "voyage",
        "wan-",
        "whisper",
        "zonos"
    )

    @JvmStatic
    fun getBaseModelName(model: String?): String {
        if (model.isNullOrBlank()) {
            return ""
        }
        return model.trim().substringAfterLast('/').lowercase()
    }

    @JvmStatic
    fun isTextGenerationModel(model: String?): Boolean {
        if (model.isNullOrBlank()) {
            return true
        }
        val normalized = model.trim().lowercase(Locale.ROOT)
        return nonTextGenerationModelKeywords.none { normalized.contains(it) }
    }

    @JvmStatic
    fun isGeminiLegacy(model: String?): Boolean {
        val base = getBaseModelName(model)
        return base.startsWith("gemini-2") || base.startsWith("gemini-3-") || base.startsWith("gemini-3.1")
    }

    @JvmStatic
    fun isDeepSeekV4(model: String?): Boolean {
        return getBaseModelName(model).startsWith("deepseek-v4")
    }

    private fun isNonReasoningModel(model: String?): Boolean {
        val base = getBaseModelName(model)
        return (base.startsWith("gpt-5") && (base.contains("instant") || base.contains("chat")))
                || base.contains("non-reasoning")
    }

    @JvmStatic
    fun getReasoningEffort(model: String?): String {
        val base = getBaseModelName(model)
        return when {
            base.startsWith("gpt-oss") -> "low"
            base.startsWith("gpt-5.") -> "none"
            base.startsWith("gpt-5") -> "minimal"
            base.startsWith("gemini") && base.contains("pro") -> "low"
            base.startsWith("gemini") && base.contains("flash-lite") && !isGeminiLegacy(model) -> "minimal"
            base.startsWith("gemini") && (base.endsWith("latest") || !isGeminiLegacy(model)) -> "low"
            base.contains("gemma4") || base.contains("gemma-4") -> "minimal"
            base.startsWith("grok-4") -> "low"
            base.startsWith("glm-5.3") -> "low"
            base.startsWith("muse-spark") -> "minimal"
            else -> "none"
        }
    }

    @JvmStatic
    fun getGeminiThinkingConfig(model: String?): JSONObject {
        val base = getBaseModelName(model)
        if (base.startsWith("gemini-2")) {
            return JSONObject().put("thinkingBudget", if (base.contains("pro")) 128 else 0)
        }
        val level = when {
            base.contains("flash-latest") -> ThinkingLevel.LOW

            base.startsWith("gemini-3") &&
                (base.contains("pro") || base.startsWith("gemini-3.7")) ->
                    ThinkingLevel.LOW

            base.startsWith("gemini-3") ||
                base.contains("gemma4") || base.contains("gemma-4") ->
                    ThinkingLevel.MINIMAL

            else -> ThinkingLevel.LOW
        }
        return JSONObject().put("thinkingLevel", level)
    }

    @JvmStatic
    fun applyReasoningParameters(requestJson: JSONObject, url: String?, model: String?) {
        if (isNonReasoningModel(model)) {
            return
        }
        val providerPreset = when (url) {
            PresetRegistry.getPresetBaseUrl(PresetRegistry.GOOGLE_AI_STUDIO) -> PresetRegistry.GOOGLE_AI_STUDIO
            PresetRegistry.getPresetBaseUrl(PresetRegistry.GOOGLE_AGENT_PLATFORM) -> PresetRegistry.GOOGLE_AGENT_PLATFORM
            PresetRegistry.getPresetBaseUrl(PresetRegistry.OPENROUTER) -> PresetRegistry.OPENROUTER
            PresetRegistry.getPresetBaseUrl(PresetRegistry.VERCEL_AI_GATEWAY) -> PresetRegistry.VERCEL_AI_GATEWAY
            else -> null
        }
        if (providerPreset == PresetRegistry.GOOGLE_AGENT_PLATFORM) {
            val generationConfig = requestJson.optJSONObject("generationConfig") ?: JSONObject().also {
                requestJson.put("generationConfig", it)
            }
            generationConfig.put("thinkingConfig", getGeminiThinkingConfig(model))
            return
        }
        applyReasoningParametersInternal(requestJson, providerPreset, model)
    }

    private fun applyReasoningParametersInternal(requestJson: JSONObject, providerPreset: Int?, model: String?) {
        if (providerPreset != null && applyReasoningParametersRouter(requestJson, providerPreset, model)) {
            return
        }
        applyReasoningParametersOriginal(requestJson, model)
    }

    private fun applyReasoningParametersOriginal(requestJson: JSONObject, model: String?) {
        if (isDeepSeekV4(model)) {
            requestJson.put("thinking", JSONObject().put("type", "disabled"))
        } else {
            requestJson.put("reasoning_effort", getReasoningEffort(model))
        }
    }

    private fun applyReasoningParametersRouter(requestJson: JSONObject, providerPreset: Int, model: String?): Boolean {
        val provider = getModelProvider(model) ?: return false
        return when (providerPreset) {
            PresetRegistry.OPENROUTER -> {
                requestJson.put("reasoning", JSONObject().put("effort", getReasoningEffort(model)))
                true
            }
            PresetRegistry.VERCEL_AI_GATEWAY -> {
                putProviderOptions(
                    requestJson,
                    "gateway",
                    JSONObject().put("sort", "ttft")
                )
                when (provider) {
                    "deepseek" -> {
                        if (isDeepSeekV4(model)) {
                            putProviderOptions(
                                requestJson,
                                "deepseek",
                                JSONObject().put("thinking", JSONObject().put("type", "disabled"))
                            )
                            return true
                        }
                    }
                }
                requestJson.put("reasoning", JSONObject().put("effort", getReasoningEffort(model)))
                true
            }
            else -> false
        }
    }

    private fun getModelProvider(model: String?): String? {
        if (model.isNullOrBlank() || !model.contains('/')) {
            return null
        }
        return model.trim().substringBefore('/').lowercase()
    }

    private fun putProviderOptions(requestJson: JSONObject, provider: String, options: JSONObject) {
        val providerOptions = requestJson.optJSONObject("providerOptions") ?: JSONObject().also {
            requestJson.put("providerOptions", it)
        }
        providerOptions.put(provider, options)
    }

    @JvmStatic
    fun supportsTemperature(model: String?): Boolean {
        val base = getBaseModelName(model)
        return !base.startsWith("gpt-5") && (!base.startsWith("gemini") || isGeminiLegacy(model))
    }

    @JvmStatic
    fun stripModelsPrefix(models: List<String?>?): List<String> {
        if (models.isNullOrEmpty()) {
            return emptyList()
        }
        val out = LinkedHashSet<String>()
        for (model in models) {
            if (model == null) {
                continue
            }
            var id = model.trim()
            if (id.startsWith("models/")) {
                id = id.substring("models/".length)
            }
            if (id.isNotEmpty()) {
                out.add(id)
            }
        }
        return out.toList()
    }

    @JvmStatic
    fun isOpenRouterFreeModel(modelId: String?): Boolean {
        if (modelId.isNullOrBlank()) {
            return false
        }
        return modelId.trim().endsWith(":free", ignoreCase = true)
    }
}
