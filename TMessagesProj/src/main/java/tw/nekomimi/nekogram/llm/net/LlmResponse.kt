package tw.nekomimi.nekogram.llm.net

data class LlmResponse<T>(
    @JvmField val data: T? = null,
    @JvmField val error: String? = null,
    @JvmField val durationMs: Long = 0,
    @JvmField val httpCode: Int = 0
) {
    val isSuccess: Boolean
        get() = error == null

    fun data(): T? = data
    fun error(): String? = error
    fun durationMs(): Long = durationMs
    fun httpCode(): Int = httpCode
}
