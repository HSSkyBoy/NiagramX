package top.nkbe.niagram.llm.net

import android.util.LruCache
import java.security.MessageDigest

object TranslationCache {

    private const val MAX_SIZE = 256

    // Key format: "$model|$toLang|${md5(text)}|${md5(context ?: "")}"
    private val cache = object : LruCache<String, String>(MAX_SIZE) {}

    @JvmStatic
    fun get(text: String, toLang: String, model: String, context: String?): String? {
        val key = buildKey(text, toLang, model, context)
        return cache.get(key)
    }

    @JvmStatic
    fun put(text: String, toLang: String, model: String, context: String?, result: String) {
        val key = buildKey(text, toLang, model, context)
        cache.put(key, result)
    }

    @JvmStatic
    fun clear() {
        cache.evictAll()
    }

    private fun buildKey(text: String, toLang: String, model: String, context: String?): String {
        val textHash = md5(text)
        val contextHash = if (!context.isNullOrBlank()) md5(context) else ""
        return "$model|$toLang|$textHash|$contextHash"
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}
