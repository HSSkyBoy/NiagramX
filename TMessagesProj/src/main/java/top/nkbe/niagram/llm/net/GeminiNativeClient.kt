package top.nkbe.niagram.llm.net

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import top.nkbe.niagram.llm.preset.PresetRegistry
import top.nkbe.niagram.llm.utils.ModelUtil
import java.util.LinkedHashSet

object GeminiNativeClient {

    @JvmField
    val MODELS: List<String> = listOf(
        "gemini-3.5-flash-lite",
        "gemini-3.1-flash-lite",
        "gemini-2.5-flash-lite",
        "gemini-3.7-flash",
        "gemini-3.5-flash",
        "gemini-2.5-flash",
        "gemini-3.1-pro",
        "gemini-2.5-pro"
    )

    @JvmStatic
    fun getModels(): LlmResponse<List<String>> {
        return LlmResponse(MODELS, null, 0, 0)
    }

    @JvmStatic
    fun fetchModels(preset: Int, baseUrl: String?, apiKey: String?): LlmResponse<List<String>> {
        if (preset == PresetRegistry.GOOGLE_AGENT_PLATFORM) {
            return LlmResponse(MODELS, null, 0, 0)
        }
        val credentials = LlmTransport.prepareCredentials(baseUrl, apiKey)
        if (credentials.isInvalid) {
            return LlmTransport.error(credentials.error)
        }
        val response = LlmTransport.execute({
            Request.Builder()
                .url(credentials.baseUrl + "/models")
                .header("x-goog-api-key", credentials.apiKey)
                .get()
                .build()
        }, LlmTransport.HTTP_CLIENT)

        return LlmTransport.parseResponse(
            response,
            { body ->
                val models = parseGeminiModelIds(body)
                ModelUtil.stripModelsPrefix(models)
            },
            { it.isNullOrEmpty() },
            "No models found: "
        )
    }

    @JvmStatic
    fun testGenerateContent(preset: Int, baseUrl: String?, apiKey: String?, model: String?): LlmResponse<String> {
        return LlmTransport.test(model) { modelName, messages ->
            generateContent(preset, baseUrl, apiKey, modelName, messages, LlmTransport.TEST_HTTP_CLIENT)
        }
    }

    @JvmStatic
    fun generateContent(preset: Int, baseUrl: String?, apiKey: String?, model: String?, messages: JSONArray): LlmResponse<String> {
        return generateContent(preset, baseUrl, apiKey, model, messages, LlmTransport.HTTP_CLIENT)
    }

    private fun generateContent(
        preset: Int,
        baseUrl: String?,
        apiKey: String?,
        model: String?,
        messages: JSONArray,
        client: OkHttpClient
    ): LlmResponse<String> {
        val credentials = LlmTransport.prepareCredentials(baseUrl, apiKey)
        if (credentials.isInvalid) {
            return LlmTransport.error(credentials.error)
        }
        return try {
            val requestJson = buildRequest(preset, model, messages)
            val modelPath = normalizeModelPath(preset, model)
            val endpoint = credentials.baseUrl + "/" + modelPath + ":generateContent"

            val response = LlmTransport.execute({
                Request.Builder()
                    .url(endpoint)
                    .header("x-goog-api-key", credentials.apiKey)
                    .post(LlmTransport.jsonBody(requestJson))
                    .build()
            }, client)

            LlmTransport.parseResponse(
                response,
                { body ->
                    val content = parseFirstCandidateContent(body)
                    content?.trim()
                },
                { it.isNullOrEmpty() },
                "Empty content: "
            )
        } catch (e: Exception) {
            LlmTransport.error(e.toString())
        }
    }

    @Throws(Exception::class)
    private fun buildRequest(preset: Int, model: String?, messages: JSONArray): String {
        val systemParts = JSONArray()
        val contents = JSONArray()
        for (i in 0 until messages.length()) {
            val message = messages.optJSONObject(i) ?: continue
            val text = message.optString("content", "")
            if (text.isEmpty()) {
                continue
            }
            val role = message.optString("role", "user")
            if ("system" == role) {
                systemParts.put(JSONObject().put("text", text))
                continue
            }
            val contentRole = if ("assistant" == role) "model" else "user"
            val lastContent = if (contents.length() > 0) contents.optJSONObject(contents.length() - 1) else null
            if (lastContent != null && contentRole == lastContent.optString("role")) {
                lastContent.getJSONArray("parts").put(JSONObject().put("text", text))
            } else {
                contents.put(
                    JSONObject()
                        .put("role", contentRole)
                        .put("parts", JSONArray().put(JSONObject().put("text", text)))
                )
            }
        }

        val requestJson = JSONObject().put("contents", contents)
        if (systemParts.length() > 0) {
            requestJson.put("systemInstruction", JSONObject().put("parts", systemParts))
        }
        ModelUtil.applyReasoningParameters(requestJson, preset, model)
        return requestJson.toString()
    }

    private fun parseFirstCandidateContent(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            val json = JSONObject(body)
            val candidates = json.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val content = candidates.getJSONObject(0).optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            val text = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                if (part.optBoolean("thought", false)) {
                    continue
                }
                val value = part.optString("text", "")
                if (value.isNotEmpty()) {
                    text.append(value)
                }
            }
            text.toString()
        } catch (_: Exception) {
            null
        }
    }

    @Throws(Exception::class)
    private fun parseGeminiModelIds(body: String?): List<String> {
        val out = LinkedHashSet<String>()
        val trimmed = body?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return emptyList()
        }
        val json = JSONObject(trimmed)
        if (json.has("models") && json.get("models") is JSONArray) {
            val array = json.getJSONArray("models")
            for (i in 0 until array.length()) {
                val item = array.opt(i)
                if (item is JSONObject) {
                    val name = item.optString("name", "").trim()
                    if (name.isNotEmpty()) {
                        out.add(name)
                    }
                } else if (item is String) {
                    val name = item.trim()
                    if (name.isNotEmpty()) {
                        out.add(name)
                    }
                }
            }
        }
        return out.toList()
    }

    private fun normalizeModelPath(preset: Int, model: String?): String {
        val modelName = model?.trim().orEmpty()
        if (preset == PresetRegistry.GOOGLE_AI_STUDIO) {
            if (modelName.startsWith("models/")) {
                return modelName
            }
            return "models/$modelName"
        }
        // Vertex AI (GOOGLE_AGENT_PLATFORM)
        if (modelName.startsWith("publishers/")) {
            return modelName
        }
        if (modelName.startsWith("models/")) {
            return "publishers/google/" + modelName
        }
        if (modelName.startsWith("google/")) {
            return "publishers/google/models/" + modelName.substring("google/".length)
        }
        return "publishers/google/models/" + modelName
    }
}
