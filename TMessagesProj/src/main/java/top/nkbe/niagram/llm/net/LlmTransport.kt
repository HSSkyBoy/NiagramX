package top.nkbe.niagram.llm.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import top.nkbe.niagram.utils.HttpClient
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.function.Predicate

internal object LlmTransport {

    @JvmField
    val HTTP_CLIENT: OkHttpClient = HttpClient.llmInstance

    @JvmField
    val TEST_HTTP_CLIENT: OkHttpClient = HTTP_CLIENT.newBuilder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private val optionalParametersDisabledModels = ConcurrentHashMap.newKeySet<String>()

    data class Credentials(
        val baseUrl: String,
        val apiKey: String,
        val error: String?
    ) {
        val isInvalid: Boolean
            get() = error != null

        fun baseUrl(): String = baseUrl
        fun apiKey(): String = apiKey
        fun error(): String? = error
    }

    fun interface OptionalParametersRequest {
        @Throws(Exception::class)
        fun execute(withOptionalParameters: Boolean): LlmResponse<String>
    }

    fun interface ResponseParser<T> {
        @Throws(Exception::class)
        fun parse(body: String): T?
    }

    fun interface TestRequest {
        @Throws(Exception::class)
        fun execute(model: String, messages: JSONArray): LlmResponse<String>
    }

    fun interface RequestFactory {
        @Throws(Exception::class)
        fun create(): Request
    }

    @JvmStatic
    fun prepareCredentials(baseUrl: String?, apiKey: String?): Credentials {
        val requestBaseUrl = trimTrailingSlash(baseUrl?.trim().orEmpty())
        if (requestBaseUrl.isEmpty()) {
            return Credentials("", "", "Empty base URL")
        }
        val key = apiKey?.trim().orEmpty()
        if (key.isEmpty()) {
            return Credentials(requestBaseUrl, "", getString(R.string.ApiKeyNotSet))
        }
        if (key.indexOf('\r') >= 0 || key.indexOf('\n') >= 0) {
            return Credentials(requestBaseUrl, "", "Invalid API key")
        }
        return Credentials(requestBaseUrl, key, null)
    }

    @JvmStatic
    fun execute(requestFactory: RequestFactory, client: OkHttpClient): LlmResponse<String> {
        val start = System.currentTimeMillis()
        return try {
            client.newCall(requestFactory.create()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val duration = System.currentTimeMillis() - start
                val code = response.code
                if (!response.isSuccessful) {
                    LlmResponse(null, formatHttpError(code, body), duration, code)
                } else {
                    LlmResponse(body, null, duration, code)
                }
            }
        } catch (e: Exception) {
            LlmResponse(null, e.toString(), System.currentTimeMillis() - start, 0)
        }
    }

    @JvmStatic
    fun jsonBody(json: String): RequestBody {
        return json.toRequestBody(HttpClient.MEDIA_TYPE_JSON)
    }

    @JvmStatic
    fun executeWithOptionalParameters(baseUrl: String, model: String?, request: OptionalParametersRequest): LlmResponse<String> {
        val key = baseUrl + "|" + (model?.trim().orEmpty())
        val withOptionalParameters = !optionalParametersDisabledModels.contains(key)
        return try {
            val response = request.execute(withOptionalParameters)
            if (!response.isSuccess && response.httpCode == 400 && withOptionalParameters) {
                optionalParametersDisabledModels.add(key)
                FileLog.d("HTTP 400 with optional parameters, retrying without them for model: ")
                request.execute(false)
            } else {
                response
            }
        } catch (e: Exception) {
            error(e.toString())
        }
    }

    @JvmStatic
    fun <T> parseResponse(
        response: LlmResponse<String>,
        parser: ResponseParser<T>,
        isEmpty: Predicate<T?>,
        emptyError: String
    ): LlmResponse<T> {
        if (!response.isSuccess) {
            return error(response)
        }
        val rawBody = response.data.orEmpty()
        return try {
            val data = parser.parse(rawBody)
            if (isEmpty.test(data)) {
                LlmResponse(null, emptyError + truncate(rawBody), response.durationMs, response.httpCode)
            } else {
                LlmResponse(data, null, response.durationMs, response.httpCode)
            }
        } catch (e: Exception) {
            LlmResponse(null, "Parse error:  ; raw=" + truncate(rawBody), response.durationMs, response.httpCode)
        }
    }

    @JvmStatic
    fun test(model: String?, request: TestRequest): LlmResponse<String> {
        val modelName = model?.trim().orEmpty()
        if (modelName.isEmpty()) {
            return error("Model is empty")
        }
        return try {
            val messages = JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put("content", "This is a test. Reply with a single word: OK")
            )
            request.execute(modelName, messages)
        } catch (e: Exception) {
            error(e.toString())
        }
    }

    @JvmStatic
    fun <T> error(error: String?): LlmResponse<T> {
        return LlmResponse(null, error, 0, 0)
    }

    @JvmStatic
    fun <T> error(response: LlmResponse<*>): LlmResponse<T> {
        return LlmResponse(null, response.error, response.durationMs, response.httpCode)
    }

    @JvmStatic
    fun truncate(value: String?): String {
        if (value == null) {
            return ""
        }
        val limit = 4096
        return if (value.length <= limit) {
            value
        } else {
            value.substring(0, limit) + "\n…(truncated)"
        }
    }

    private fun trimTrailingSlash(value: String): String {
        var end = value.length
        while (end > 0 && value[end - 1] == '/') {
            end--
        }
        return value.substring(0, end)
    }

    private fun formatHttpError(code: Int, body: String): String {
        return String.format(Locale.ROOT, "HTTP %d : %s", code, truncate(body))
    }
}
