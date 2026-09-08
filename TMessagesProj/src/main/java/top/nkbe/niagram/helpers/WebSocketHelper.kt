/*
 * Copyright (C) 2019-2024 qwq233 <qwq233@qwq2333.top>
 * https://github.com/qwq233/Nullgram
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this software.
 *  If not, see <https://www.gnu.org/licenses/>
 */
package top.nkbe.niagram.helpers

import org.tcp2ws.tcp2wsServer
import org.telegram.messenger.BuildVars
import org.telegram.messenger.FileLog
import java.net.ServerSocket

object WebSocketHelper {
    const val proxyServer = "ck2ut7v3g5zudnjw.top"

    private var socksPort = -1
    private var tcp2wsStarted = false
    private var tcp2wsServer: tcp2wsServer? = null

    private const val NULLGRAM_VERSION_NAME = "v12.2.10-2d6df6a"
    private const val NULLGRAM_VERSION_CODE = "1645201751"
    private val userAgent = "Nullgram $NULLGRAM_VERSION_NAME ($NULLGRAM_VERSION_CODE)"
    private val connHash = "381d52f35f552e10ad1701445dba9cd14acb7e43"

    @JvmStatic
    var wsEnableTLS: Boolean = false

    @JvmStatic
    fun getSocksPort(): Int {
        return getSocksPort(6356)
    }

    @JvmStatic
    fun wsReloadConfig() {
        if (tcp2wsServer != null) {
            try {
                tcp2wsServer?.setCdnDomain(proxyServer)
                    ?.setTls(wsEnableTLS)
                    ?.setUserAgent((System.getProperty("http.agent") ?: "") + " " + userAgent)
                    ?.setConnHash(connHash)
            } catch (e: Exception) {
                FileLog.e(e)
            }
        }
    }

    @JvmStatic
    @Synchronized
    fun stopServer() {
        if (tcp2wsStarted && tcp2wsServer != null) {
            try {
                tcp2wsServer?.stop()
            } catch (e: Exception) {
                FileLog.e(e)
            }
            tcp2wsStarted = false
            tcp2wsServer = null
        }
    }

    @JvmStatic
    @Synchronized
    fun getSocksPort(port: Int): Int {
        return if (tcp2wsStarted && socksPort != -1) {
            socksPort
        } else try {
            if (port != -1) {
                socksPort = port
            } else {
                val socket = ServerSocket(0)
                socksPort = socket.localPort
                socket.close()
            }
            if (!tcp2wsStarted) {
                FileLog.d("Starting tcp2ws on port $socksPort with UA: ${System.getProperty("http.agent")} $userAgent")
                val server = tcp2wsServer()
                server.setCdnDomain(proxyServer)
                server.setTls(wsEnableTLS)
                server.setUserAgent((System.getProperty("http.agent") ?: "") + " " + userAgent)
                server.setConnHash(connHash)
                server.start(socksPort)
                tcp2wsServer = server
                tcp2wsStarted = true
            }
            socksPort
        } catch (e: Exception) {
            FileLog.e(e)
            if (port != -1) {
                getSocksPort(-1)
            } else {
                -1
            }
        }
    }
}
