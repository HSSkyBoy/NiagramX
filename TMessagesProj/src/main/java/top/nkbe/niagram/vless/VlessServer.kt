package top.nkbe.niagram.vless

import org.telegram.messenger.FileLog
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.concurrent.thread

/**
 * Lightweight pure Kotlin/Java VLESS Tunnel Server.
 * Runs a local SOCKS5 listener (127.0.0.1:localPort) and bridges Telegram's connections
 * to the remote VLESS server over standard TLS.
 *
 * Design principles:
 * 1. 0% CPU consumption during idle: ServerSocket.accept() and socket reads block strictly
 *    in OS kernel without any polling or busy loops.
 * 2. Instant cleanup: Closing the server closes all active client and remote sockets,
 *    immediately unblocking threads and freeing file descriptors.
 */
class VlessServer : Closeable {

    private val isRunningState = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val connectionCounter = AtomicInteger(0)

    val isRunning: Boolean
        get() = isRunningState.get()

    val localPort: Int
        get() = serverSocket?.localPort ?: 0

    val activeConnectionCount: Int
        get() = activeSockets.size / 2 // 2 sockets per tunnel (client + remote)

    /**
     * Starts the local SOCKS5 server bound to 127.0.0.1.
     * @param config The VLESS node configuration.
     * @param preferredPort Preferred local port (0 for random available port).
     * @return The bound local port.
     */
    @Synchronized
    fun start(config: VlessConfig, preferredPort: Int = 0): Int {
        if (isRunningState.get()) {
            stop()
        }

        val sSocket = ServerSocket()
        sSocket.reuseAddress = true
        try {
            sSocket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), preferredPort))
        } catch (e: Exception) {
            if (preferredPort != 0) {
                FileLog.d("VlessServer: preferred port $preferredPort occupied, falling back to random port")
                sSocket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
            } else {
                throw e
            }
        }
        serverSocket = sSocket
        isRunningState.set(true)

        val port = sSocket.localPort
        FileLog.d("VlessServer: started on 127.0.0.1:$port for target ${config.host}:${config.port}")

        val t = thread(name = "VlessServer-Accept", isDaemon = true) {
            acceptLoop(sSocket, config)
        }
        acceptThread = t

        return port
    }

    /**
     * Stops the server and closes all active client and remote sockets.
     */
    @Synchronized
    fun stop() {
        if (!isRunningState.compareAndSet(true, false)) {
            return
        }

        FileLog.d("VlessServer: stopping server...")

        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        acceptThread?.interrupt()
        acceptThread = null

        // Close all active sockets to terminate all blocking pipes immediately
        val socketsToClose = activeSockets.toList()
        activeSockets.clear()
        for (sock in socketsToClose) {
            try {
                sock.close()
            } catch (_: Exception) {}
        }

        FileLog.d("VlessServer: stopped and cleaned up ${socketsToClose.size} sockets")
    }

    override fun close() {
        stop()
    }

    private fun acceptLoop(sSocket: ServerSocket, config: VlessConfig) {
        while (isRunningState.get()) {
            try {
                val clientSocket = sSocket.accept()
                clientSocket.tcpNoDelay = true
                trackSocket(clientSocket)

                val connId = connectionCounter.incrementAndGet()
                thread(name = "VlessConn-$connId", isDaemon = true) {
                    handleConnection(clientSocket, config)
                }
            } catch (e: SocketException) {
                // Expected when ServerSocket is closed
                break
            } catch (e: Throwable) {
                if (isRunningState.get()) {
                    FileLog.e("VlessServer: accept error", e)
                }
                break
            }
        }
    }

    private fun trackSocket(socket: Socket) {
        activeSockets.add(socket)
    }

    private fun untrackSocket(socket: Socket) {
        activeSockets.remove(socket)
    }

    private fun handleConnection(clientSocket: Socket, config: VlessConfig) {
        var remoteSocket: Socket? = null
        try {
            val clientIn = DataInputStream(clientSocket.getInputStream())
            val clientOut = DataOutputStream(clientSocket.getOutputStream())

            // 1. SOCKS5 Handshake: Greeting
            val socksVersion = clientIn.readByte().toInt() and 0xFF
            if (socksVersion != 0x05) {
                return
            }
            val numMethods = clientIn.readByte().toInt() and 0xFF
            val methods = ByteArray(numMethods)
            clientIn.readFully(methods)

            // Reply: Version 5, Method 0 (NO_AUTHENTICATION_REQUIRED)
            clientOut.write(byteArrayOf(0x05, 0x00))
            clientOut.flush()

            // 2. SOCKS5 Handshake: Connection Request
            val reqVersion = clientIn.readByte().toInt() and 0xFF
            val cmd = clientIn.readByte().toInt() and 0xFF
            val rsv = clientIn.readByte()
            val atyp = clientIn.readByte()

            if (reqVersion != 0x05 || cmd != 0x01) { // 0x01 is CONNECT
                // Reply: Command not supported (0x07)
                clientOut.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                clientOut.flush()
                return
            }

            // Read target address bytes based on ATYP
            val rawAddrBytes = when (atyp) {
                VlessProtocol.SOCKS5_ATYP_IPV4 -> {
                    val addr = ByteArray(4)
                    clientIn.readFully(addr)
                    addr
                }
                VlessProtocol.SOCKS5_ATYP_DOMAIN -> {
                    val len = clientIn.readByte().toInt() and 0xFF
                    val domainBytes = ByteArray(len)
                    clientIn.readFully(domainBytes)
                    // Prepend length byte for consistency with buildRequestHeaderFromSocks5
                    val result = ByteArray(len + 1)
                    result[0] = len.toByte()
                    System.arraycopy(domainBytes, 0, result, 1, len)
                    result
                }
                VlessProtocol.SOCKS5_ATYP_IPV6 -> {
                    val addr = ByteArray(16)
                    clientIn.readFully(addr)
                    addr
                }
                else -> {
                    // Address type not supported (0x08)
                    clientOut.write(byteArrayOf(0x05, 0x08, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                    clientOut.flush()
                    return
                }
            }

            val targetPort = clientIn.readUnsignedShort()

            // 3. Connect to remote VLESS server
            val remote = dialRemote(config)
            remoteSocket = remote
            trackSocket(remote)

            val remoteOut = remote.getOutputStream()
            val remoteIn = remote.getInputStream()

            // 4. Send VLESS Request Header
            val vlessHeader = VlessProtocol.buildRequestHeaderFromSocks5(
                uuidBytes = config.uuidBytes,
                socks5Atyp = atyp,
                rawAddressBytes = rawAddrBytes,
                targetPort = targetPort,
                command = VlessProtocol.CMD_TCP
            )
            remoteOut.write(vlessHeader)
            remoteOut.flush()

            // 5. Read VLESS Response Header
            VlessProtocol.readResponseHeader(remoteIn)

            // 6. SOCKS5 Reply: Success (0x00)
            // 0x05, 0x00 (success), 0x00 (RSV), 0x01 (IPv4), BND.ADDR=0.0.0.0, BND.PORT=0
            clientOut.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            clientOut.flush()

            // 7. Full-Duplex Bidirectional Pipe
            bridgeSockets(clientSocket, remote)

        } catch (e: Exception) {
            if (isRunningState.get()) {
                // SOCKS5 reply failure if client socket is still open
                try {
                    val clientOut = clientSocket.getOutputStream()
                    clientOut.write(byteArrayOf(0x05, 0x05, 0x00, 0x01, 0, 0, 0, 0, 0, 0)) // 0x05 = Connection refused
                    clientOut.flush()
                } catch (_: Exception) {}
            }
        } finally {
            untrackSocket(clientSocket)
            try {
                clientSocket.close()
            } catch (_: Exception) {}

            if (remoteSocket != null) {
                untrackSocket(remoteSocket)
                try {
                    remoteSocket.close()
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Dials the remote VLESS endpoint, establishing standard TLS if configured.
     */
    private fun dialRemote(config: VlessConfig): Socket {
        val baseSocket = Socket()
        baseSocket.tcpNoDelay = true
        baseSocket.soTimeout = 0 // Keep-alive / no read timeout for long-lived DC connections
        baseSocket.connect(InetSocketAddress(config.host, config.port), CONNECT_TIMEOUT_MS)

        if (!config.isTls) {
            return baseSocket
        }

        val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val sslSocket = sslFactory.createSocket(
            baseSocket,
            config.host,
            config.port,
            true
        ) as SSLSocket

        // Inject SNI
        val sniHost = config.effectiveSni
        if (sniHost.isNotBlank()) {
            try {
                val params = sslSocket.sslParameters
                params.serverNames = listOf(SNIHostName(sniHost))
                sslSocket.sslParameters = params
            } catch (e: Throwable) {
                FileLog.e("VlessServer: failed to set SNI $sniHost", e)
            }
        }

        // Enable standard modern TLS protocols
        try {
            val supportedProtocols = sslSocket.supportedProtocols
            val targetProtocols = mutableListOf<String>()
            if ("TLSv1.3" in supportedProtocols) targetProtocols.add("TLSv1.3")
            if ("TLSv1.2" in supportedProtocols) targetProtocols.add("TLSv1.2")
            if (targetProtocols.isNotEmpty()) {
                sslSocket.enabledProtocols = targetProtocols.toTypedArray()
            }
        } catch (_: Throwable) {}

        sslSocket.startHandshake()
        return sslSocket
    }

    /**
     * Bidirectional blocking stream relay with direct byte copying.
     * When one direction terminates (EOF or error), both sockets are closed.
     */
    private fun bridgeSockets(client: Socket, remote: Socket) {
        val clientIn = client.getInputStream()
        val clientOut = client.getOutputStream()
        val remoteIn = remote.getInputStream()
        val remoteOut = remote.getOutputStream()

        val t1 = thread(name = "VlessRelay-Up", isDaemon = true) {
            pipeStream(clientIn, remoteOut)
            try {
                remote.shutdownOutput()
            } catch (_: Exception) {
                try { remote.close() } catch (_: Exception) {}
            }
        }

        val t2 = thread(name = "VlessRelay-Down", isDaemon = true) {
            pipeStream(remoteIn, clientOut)
            try {
                client.shutdownOutput()
            } catch (_: Exception) {
                try { client.close() } catch (_: Exception) {}
            }
        }

        t1.join()
        t2.join()
    }

    private fun pipeStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(BUFFER_SIZE)
        try {
            while (isRunningState.get()) {
                val bytesRead = input.read(buffer)
                if (bytesRead < 0) break
                output.write(buffer, 0, bytesRead)
                output.flush()
            }
        } catch (_: SocketException) {
            // Normal connection teardown
        } catch (_: IOException) {
            // Normal connection teardown
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val BUFFER_SIZE = 16 * 1024 // 16 KB buffer optimal for throughput vs heap
    }
}
