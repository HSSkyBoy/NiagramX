package top.nkbe.niagram.vless

import java.io.Serializable
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Configuration model and parser for VLESS nodes.
 * Format: vless://[uuid]@[host]:[port]?[query]#[remarks]
 */
data class VlessConfig(
    val id: String = UUID.randomUUID().toString(),
    val uuid: String,
    val host: String,
    val port: Int = 443,
    val remarks: String = "",
    val security: String = "tls", // "tls" or "none"
    val sni: String = "",
    val flow: String = "",        // e.g. "xtls-rprx-vision"
    val alpn: String = "",        // e.g. "h2,http/1.1"
    val type: String = "tcp",     // "tcp"
    val fingerprint: String = ""  // e.g. "chrome"
) : Serializable {

    val uuidBytes: ByteArray by lazy {
        parseUuidToBytes(uuid)
    }

    /**
     * Effective SNI: returns [sni] if non-empty, otherwise returns [host] if host is not an IP.
     */
    val effectiveSni: String
        get() {
            if (sni.isNotBlank()) return sni.trim()
            val cleanHost = host.trim()
            // If host looks like an IPv4 or IPv6, SNI should not default to it (RFC 6066)
            if (isIpAddress(cleanHost)) return ""
            return cleanHost
        }

    val isTls: Boolean
        get() = security.equals("tls", ignoreCase = true)

    val isVision: Boolean
        get() = flow.equals("xtls-rprx-vision", ignoreCase = true)

    fun toUri(): String {
        val encodedRemarks = try {
            URLEncoder.encode(remarks, "UTF-8")
        } catch (_: Exception) {
            remarks
        }
        val queryParams = mutableListOf<String>()
        if (security.isNotBlank()) queryParams.add("security=${URLEncoder.encode(security, "UTF-8")}")
        if (sni.isNotBlank()) queryParams.add("sni=${URLEncoder.encode(sni, "UTF-8")}")
        if (flow.isNotBlank()) queryParams.add("flow=${URLEncoder.encode(flow, "UTF-8")}")
        if (alpn.isNotBlank()) queryParams.add("alpn=${URLEncoder.encode(alpn, "UTF-8")}")
        if (type.isNotBlank()) queryParams.add("type=${URLEncoder.encode(type, "UTF-8")}")
        if (fingerprint.isNotBlank()) queryParams.add("fp=${URLEncoder.encode(fingerprint, "UTF-8")}")

        val queryStr = if (queryParams.isNotEmpty()) "?" + queryParams.joinToString("&") else ""
        val fragmentStr = if (encodedRemarks.isNotBlank()) "#$encodedRemarks" else ""

        val formattedHost = if (host.contains(":") && !host.startsWith("[")) "[$host]" else host
        return "vless://$uuid@$formattedHost:$port$queryStr$fragmentStr"
    }

    companion object {
        private const val serialVersionUID = 1L

        @JvmStatic
        fun parseUuidToBytes(uuidStr: String): ByteArray {
            val clean = uuidStr.replace("-", "").trim()
            if (clean.length == 32) {
                val bytes = ByteArray(16)
                for (i in 0 until 16) {
                    bytes[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                }
                return bytes
            }
            return try {
                val u = UUID.fromString(uuidStr.trim())
                val bb = ByteBuffer.wrap(ByteArray(16))
                bb.putLong(u.mostSignificantBits)
                bb.putLong(u.leastSignificantBits)
                bb.array()
            } catch (e: Exception) {
                ByteArray(16)
            }
        }

        @JvmStatic
        fun isIpAddress(str: String): Boolean {
            val s = str.trim().removePrefix("[").removeSuffix("]")
            // Quick IPv4 check
            val parts = s.split(".")
            if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
                return true
            }
            // Quick IPv6 check
            if (s.contains(":")) {
                return true
            }
            return false
        }

        /**
         * Parses a standard `vless://` URL.
         * Example: vless://a1b2c3d4-0000-0000-0000-000000000000@example.com:443?security=tls&sni=example.com#MyNode
         */
        @JvmStatic
        fun parse(rawUri: String): VlessConfig? {
            val trimmed = rawUri.trim()
            if (!trimmed.startsWith("vless://", ignoreCase = true)) {
                return null
            }

            try {
                var content = trimmed.substring("vless://".length)
                var remarks = ""

                // Extract fragment (#Remarks)
                val hashIdx = content.indexOf('#')
                if (hashIdx >= 0) {
                    remarks = content.substring(hashIdx + 1)
                    content = content.substring(0, hashIdx)
                    remarks = try {
                        URLDecoder.decode(remarks, "UTF-8")
                    } catch (_: Exception) {
                        remarks
                    }
                }

                // Extract query parameters (?...)
                val queryParams = mutableMapOf<String, String>()
                val queryIdx = content.indexOf('?')
                if (queryIdx >= 0) {
                    val queryStr = content.substring(queryIdx + 1)
                    content = content.substring(0, queryIdx)
                    queryStr.split('&').forEach { param ->
                        val eqIdx = param.indexOf('=')
                        if (eqIdx > 0) {
                            val key = param.substring(0, eqIdx).trim().lowercase()
                            val value = try {
                                URLDecoder.decode(param.substring(eqIdx + 1).trim(), "UTF-8")
                            } catch (_: Exception) {
                                param.substring(eqIdx + 1).trim()
                            }
                            queryParams[key] = value
                        }
                    }
                }

                // Parse userinfo (UUID) and host:port
                val atIdx = content.lastIndexOf('@')
                if (atIdx <= 0) return null

                val uuid = content.substring(0, atIdx).trim()
                var hostPort = content.substring(atIdx + 1).trim()

                // Extract host and port, handling IPv6 like [2001:db8::1]:443
                val host: String
                val port: Int
                if (hostPort.startsWith("[")) {
                    val closeBracket = hostPort.indexOf(']')
                    if (closeBracket <= 0) return null
                    host = hostPort.substring(1, closeBracket)
                    val afterBracket = hostPort.substring(closeBracket + 1)
                    port = if (afterBracket.startsWith(":")) {
                        afterBracket.substring(1).toIntOrNull() ?: 443
                    } else {
                        443
                    }
                } else {
                    val colonIdx = hostPort.lastIndexOf(':')
                    if (colonIdx >= 0) {
                        host = hostPort.substring(0, colonIdx)
                        port = hostPort.substring(colonIdx + 1).toIntOrNull() ?: 443
                    } else {
                        host = hostPort
                        port = 443
                    }
                }

                if (uuid.isBlank() || host.isBlank() || port !in 1..65535) {
                    return null
                }

                val security = queryParams["security"] ?: "none"
                val sni = queryParams["sni"] ?: ""
                val flow = queryParams["flow"] ?: ""
                val alpn = queryParams["alpn"] ?: ""
                val type = queryParams["type"] ?: "tcp"
                val fingerprint = queryParams["fp"] ?: ""

                return VlessConfig(
                    uuid = uuid,
                    host = host,
                    port = port,
                    remarks = if (remarks.isNotBlank()) remarks else "$host:$port",
                    security = security,
                    sni = sni,
                    flow = flow,
                    alpn = alpn,
                    type = type,
                    fingerprint = fingerprint
                )
            } catch (e: Exception) {
                return null
            }
        }
    }
}
