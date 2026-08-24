package tw.nekomimi.nekogram.llm.net

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import tw.nekomimi.nekogram.llm.utils.ModelUtil

object VertexGeminiClient {

    @JvmField
    val MODELS: List<String> = listOf(
        "gemini-3.5-flash-lite",
        "gemini-3.1-flash-lite",
        "gemini-2.5-flash-lite",
        "gemini-3.7-flash",
        "gemini-3.6-flash",
        "gemini-3.5-flash",
        "gemini-2.5-flash"
    )

    @JvmStatic
    fun getModels(): LlmResponse<List<String>> {
        return LlmResponse(MODELS, null, 0, 0)
    }

    @JvmStatic
    fun testGenerateContent(baseUrl: String?, apiKey: String?, model: String?): LlmResponse<String> {
        return LlmTransport.test(model) { modelName, messages ->
            generateContent(baseUrl, apiKey, modelName, messages, LlmTransport.TEST_HTTP_CLIENT)
        }
    }

    @JvmStatic
    fun generateContent(baseUrl: String?, apiKey: String?, model: String?, messages: JSONArray): LlmResponse<String> {
        return generateContent(baseUrl, apiKey, model, messages, LlmTransport.HTTP_CLIENT)
    }

    private fun generateContent(baseUrl: String?, apiKey: String?, model: String?, messages: JSONArray, client: OkHttpClient): LlmResponse<String> {
        val credentials = LlmTransport.prepareCredentials(baseUrl, apiKey)
        if (credentials.isInvalid) {
            return LlmTransport.error(credentials.error)
        }
        return try {
            generateContent(credentials, model, buildRequest(credentials.baseUrl, model, messages), client)
        } catch (e: Exception) {
            LlmTransport.error(e.toString())
        }
    }

    private fun generateContent(credentials: LlmTransport.Credentials, model: String?, requestJson: String, client: OkHttpClient): LlmResponse<String> {
        val modelPath = normalizeModelPath(model)
        val endpoint = credentials.baseUrl + "/" + modelPath + ":generateContent"

        val response = LlmTransport.execute({
            Request.Builder()
                .url(endpoint)
                .header("x-goog-api-key", credentials.apiKey)
                .post(LlmTransport.jsonBody(requestJson))
                .build()
        }, client)

        return LlmTransport.parseResponse(
            response,
            { body ->
                val content = parseFirstCandidateContent(body)
                content?.trim()
            },
            { it.isNullOrEmpty() },
            "Empty content: "
        )
    }

    @Throws(Exception::class)
    private fun buildRequest(baseUrl: String, model: String?, messages: JSONArray): String {
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
        ModelUtil.applyReasoningParameters(requestJson, baseUrl, model)
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

    private fun normalizeModelPath(model: String?): String {
        val modelName = model?.trim().orEmpty()
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
