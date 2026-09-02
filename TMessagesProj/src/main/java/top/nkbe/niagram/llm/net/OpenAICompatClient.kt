package top.nkbe.niagram.llm.net

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import top.nkbe.niagram.llm.utils.ModelUtil
import xyz.nextalone.nagram.NaConfig
import java.util.LinkedHashSet
import java.util.Locale

object OpenAICompatClient {

    @JvmStatic
    fun fetchModels(baseUrl: String?, apiKey: String?): LlmResponse<List<String>> {
        val credentials = LlmTransport.prepareCredentials(baseUrl, apiKey)
        if (credentials.isInvalid) {
            return LlmTransport.error(credentials.error)
        }
        val response = LlmTransport.execute({
            Request.Builder()
                .url(credentials.baseUrl + "/models")
                .header("Authorization", "Bearer " + credentials.apiKey)
                .get()
                .build()
        }, LlmTransport.HTTP_CLIENT)

        return LlmTransport.parseResponse(
            response,
            { body ->
                val models = parseModelIds(body)
                if (isGeminiModelsEndpoint(credentials.baseUrl)) {
                    ModelUtil.stripModelsPrefix(models)
                } else {
                    models
                }
            },
            { it.isNullOrEmpty() },
            "No models found: "
        )
    }

    @JvmStatic
    fun testChatCompletions(preset: Int, baseUrl: String?, apiKey: String?, model: String?): LlmResponse<String> {
        return LlmTransport.test(model) { modelName, messages ->
            chatCompletions(preset, baseUrl, apiKey, modelName, messages, NaConfig.llmTemperature.Float(), LlmTransport.TEST_HTTP_CLIENT)
        }
    }

    @JvmStatic
    fun chatCompletions(preset: Int, baseUrl: String?, apiKey: String?, model: String?, messages: JSONArray): LlmResponse<String> {
        return chatCompletions(preset, baseUrl, apiKey, model, messages, NaConfig.llmTemperature.Float(), LlmTransport.HTTP_CLIENT)
    }

    private fun chatCompletions(
        preset: Int,
        baseUrl: String?,
        apiKey: String?,
        model: String?,
        messages: JSONArray,
        temperature: Float?,
        client: OkHttpClient
    ): LlmResponse<String> {
        val credentials = LlmTransport.prepareCredentials(baseUrl, apiKey)
        if (credentials.isInvalid) {
            return LlmTransport.error(credentials.error)
        }
        return LlmTransport.executeWithOptionalParameters(credentials.baseUrl, model) { withOptionalParameters ->
            chatCompletions(
                credentials,
                buildRequest(preset, model, messages, temperature, withOptionalParameters),
                client
            )
        }
    }

    private fun chatCompletions(credentials: LlmTransport.Credentials, requestJson: String, client: OkHttpClient): LlmResponse<String> {
        val response = LlmTransport.execute({
            Request.Builder()
                .url(credentials.baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + credentials.apiKey)
                .post(LlmTransport.jsonBody(requestJson))
                .build()
        }, client)

        return LlmTransport.parseResponse(
            response,
            { body ->
                val content = parseFirstMessageContent(body)
                content?.trim()
            },
            { it.isNullOrEmpty() },
            "Empty content: "
        )
    }

    @Throws(Exception::class)
    private fun buildRequest(
        preset: Int,
        model: String?,
        messages: JSONArray,
        temperature: Float?,
        withOptionalParameters: Boolean
    ): String {
        val requestJson = JSONObject()
            .put("model", model)
            .put("messages", messages)
        if (withOptionalParameters) {
            if (temperature != null && ModelUtil.supportsTemperature(model)) {
                requestJson.put("temperature", temperature)
            }
            ModelUtil.applyReasoningParameters(requestJson, preset, model)
        }
        return requestJson.toString()
    }

    private fun parseFirstMessageContent(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            val json = JSONObject(body)
            val choices = json.optJSONArray("choices") ?: return null
            if (choices.length() == 0) return null
            val first = choices.optJSONObject(0) ?: return null
            val message = first.optJSONObject("message") ?: return null
            if (message.has("content")) {
                val contentObj = message.get("content")
                if (contentObj is String) {
                    return contentObj
                } else if (contentObj is JSONArray) {
                    val sb = StringBuilder()
                    for (i in 0 until contentObj.length()) {
                        val part = contentObj.opt(i)
                        if (part is JSONObject) {
                            val type = part.optString("type", "text")
                            if ("text" == type || "output_text" == type) {
                                sb.append(part.optString("text", ""))
                            }
                        } else if (part is String) {
                            sb.append(part)
                        }
                    }
                    if (sb.isNotEmpty()) {
                        return sb.toString()
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    @Throws(Exception::class)
    private fun parseModelIds(body: String?): List<String> {
        val out = LinkedHashSet<String>()
        val trimmed = body?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return emptyList()
        }

        if (trimmed.startsWith("[")) {
            val array = JSONArray(trimmed)
            extractModelIdsFromArray(array, out)
        } else {
            val json = JSONObject(trimmed)
            if (json.has("data") && json.get("data") is JSONArray) {
                extractModelIdsFromArray(json.getJSONArray("data"), out)
            } else if (json.has("models") && json.get("models") is JSONArray) {
                extractModelIdsFromArray(json.getJSONArray("models"), out)
            } else if (json.has("data") && json.get("data") is JSONObject) {
                val data = json.getJSONObject("data")
                if (data.has("id")) {
                    val id = data.optString("id", "").trim()
                    if (id.isNotEmpty()) out.add(id)
                }
            }
        }

        return out.toList()
    }

    private fun extractModelIdsFromArray(array: JSONArray, out: LinkedHashSet<String>) {
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            if (item is JSONObject) {
                val id = item.optString("id", "").trim()
                if (id.isNotEmpty()) {
                    out.add(id)
                }
            } else if (item is String) {
                val id = item.trim()
                if (id.isNotEmpty()) {
                    out.add(id)
                }
            }
        }
    }

    private fun isGeminiModelsEndpoint(baseUrl: String?): Boolean {
        return baseUrl != null && baseUrl.lowercase(Locale.ROOT).contains("generativelanguage.googleapis.com")
    }
}
