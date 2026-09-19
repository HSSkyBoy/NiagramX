package top.nkbe.niagram.vless

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.ProtocolException

/**
 * VLESS protocol byte serializer and response parser.
 * Wire format adheres strictly to Xray-core / v2fly / sing-box standards.
 */
object VlessProtocol {

    const val VERSION: Byte = 0x00

    const val CMD_TCP: Byte = 0x01
    const val CMD_UDP: Byte = 0x02
    const val CMD_MUX: Byte = 0x03

    const val ADDR_TYPE_IPV4: Byte = 0x01
    const val ADDR_TYPE_DOMAIN: Byte = 0x02
    const val ADDR_TYPE_IPV6: Byte = 0x03

    // SOCKS5 RFC 1928 Address Types
    const val SOCKS5_ATYP_IPV4: Byte = 0x01
    const val SOCKS5_ATYP_DOMAIN: Byte = 0x03
    const val SOCKS5_ATYP_IPV6: Byte = 0x04

    /**
     * Builds a VLESS Request Header from target host and port.
     */
    @JvmStatic
    fun buildRequestHeader(
        uuidBytes: ByteArray,
        targetHost: String,
        targetPort: Int,
        command: Byte = CMD_TCP
    ): ByteArray {
        require(uuidBytes.size == 16) { "UUID must be exactly 16 bytes" }
        require(targetPort in 1..65535) { "Invalid port: $targetPort" }

        val cleanHost = targetHost.trim().removePrefix("[").removeSuffix("]")
        val baos = ByteArrayOutputStream(64)
        val dos = DataOutputStream(baos)

        // 1. Version (1 byte)
        dos.writeByte(VERSION.toInt())

        // 2. User ID (16 bytes)
        dos.write(uuidBytes)

        // 3. Addon length (1 byte) - 0 indicates no addons (standard TLS mode)
        dos.writeByte(0)

        // 4. Command (1 byte) - 0x01 for TCP
        dos.writeByte(command.toInt())

        // 5. Port (2 bytes, Big-Endian)
        dos.writeShort(targetPort)

        // 6. Address Type & Address
        val inetAddr = try {
            if (VlessConfig.isIpAddress(cleanHost)) InetAddress.getByName(cleanHost) else null
        } catch (_: Exception) {
            null
        }

        when (inetAddr) {
            is Inet4Address -> {
                dos.writeByte(ADDR_TYPE_IPV4.toInt())
                dos.write(inetAddr.address)
            }
            is Inet6Address -> {
                dos.writeByte(ADDR_TYPE_IPV6.toInt())
                dos.write(inetAddr.address)
            }
            else -> {
                // Domain
                val domainBytes = cleanHost.toByteArray(Charsets.US_ASCII)
                require(domainBytes.size <= 255) { "Domain name too long: $cleanHost" }
                dos.writeByte(ADDR_TYPE_DOMAIN.toInt())
                dos.writeByte(domainBytes.size)
                dos.write(domainBytes)
            }
        }

        dos.flush()
        return baos.toByteArray()
    }

    /**
     * Builds a VLESS Request Header directly from SOCKS5 CONNECT parameters.
     * Note: In SOCKS5, DOMAIN is atyp 0x03 (1 byte length + ASCII), IPV6 is atyp 0x04 (16 bytes).
     * In VLESS, DOMAIN is 0x02, IPV6 is 0x03.
     */
    @JvmStatic
    fun buildRequestHeaderFromSocks5(
        uuidBytes: ByteArray,
        socks5Atyp: Byte,
        rawAddressBytes: ByteArray,
        targetPort: Int,
        command: Byte = CMD_TCP
    ): ByteArray {
        require(uuidBytes.size == 16) { "UUID must be exactly 16 bytes" }
        require(targetPort in 1..65535) { "Invalid port: $targetPort" }

        val baos = ByteArrayOutputStream(64)
        val dos = DataOutputStream(baos)

        // 1. Version
        dos.writeByte(VERSION.toInt())
        // 2. User ID
        dos.write(uuidBytes)
        // 3. Addon length
        dos.writeByte(0)
        // 4. Command
        dos.writeByte(command.toInt())
        // 5. Port
        dos.writeShort(targetPort)

        // 6. Address
        when (socks5Atyp) {
            SOCKS5_ATYP_IPV4 -> {
                require(rawAddressBytes.size == 4) { "IPv4 address must be 4 bytes" }
                dos.writeByte(ADDR_TYPE_IPV4.toInt())
                dos.write(rawAddressBytes)
            }
            SOCKS5_ATYP_DOMAIN -> {
                // In SOCKS5, rawAddressBytes already includes [1-byte length + ASCII bytes] OR only ASCII bytes
                // We handle both:
                dos.writeByte(ADDR_TYPE_DOMAIN.toInt())
                if (rawAddressBytes.isNotEmpty()) {
                    val firstByte = rawAddressBytes[0].toInt() and 0xFF
                    if (rawAddressBytes.size == firstByte + 1) {
                        // Already formatted as [length, string...]
                        dos.write(rawAddressBytes)
                    } else {
                        // Raw string bytes
                        dos.writeByte(rawAddressBytes.size)
                        dos.write(rawAddressBytes)
                    }
                } else {
                    dos.writeByte(0)
                }
            }
            SOCKS5_ATYP_IPV6 -> {
                require(rawAddressBytes.size == 16) { "IPv6 address must be 16 bytes" }
                dos.writeByte(ADDR_TYPE_IPV6.toInt())
                dos.write(rawAddressBytes)
            }
            else -> throw ProtocolException("Unsupported SOCKS5 ATYP: $socks5Atyp")
        }

        dos.flush()
        return baos.toByteArray()
    }

    /**
     * Reads and validates the VLESS Response Header from the remote server.
     * Throws [ProtocolException] if the version doesn't match.
     */
    @JvmStatic
    @Throws(Exception::class)
    fun readResponseHeader(inputStream: InputStream) {
        val dis = DataInputStream(inputStream)
        val version = dis.readByte()
        if (version != VERSION) {
            throw ProtocolException("Unexpected VLESS response version: $version (expected $VERSION)")
        }

        val addonsLen = dis.readByte().toInt() and 0xFF
        if (addonsLen > 0) {
            val skipped = dis.skipBytes(addonsLen)
            if (skipped < addonsLen) {
                // In case skipBytes skipped fewer bytes
                val remaining = addonsLen - skipped
                val buf = ByteArray(remaining)
                dis.readFully(buf)
            }
        }
    }
}
