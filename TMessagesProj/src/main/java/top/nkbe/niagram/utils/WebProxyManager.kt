package top.nkbe.niagram.utils

import okhttp3.Authenticator as OkHttpAuthenticator
import okhttp3.Credentials
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import org.telegram.messenger.FileLog
import org.telegram.messenger.SharedConfig
import top.nkbe.niagram.NekoConfig
import java.io.IOException
import java.net.Authenticator as JavaAuthenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

object WebProxyManager {

    private val javaAuthInstalled = java.util.concurrent.atomic.AtomicBoolean(false)

    init {
        ensureJavaAuthenticator()
    }

    private fun ensureJavaAuthenticator() {
        if (javaAuthInstalled.compareAndSet(false, true)) {
            try {
                JavaAuthenticator.setDefault(object : JavaAuthenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication? {
                        if (requestorType == RequestorType.PROXY) {
                            val creds = getEffectiveProxyCredentials()
                            if (creds != null && creds.first.isNotEmpty()) {
                                return PasswordAuthentication(creds.first, creds.second.toCharArray())
                            }
                        }
                        return super.getPasswordAuthentication()
                    }
                })
            } catch (e: Throwable) {
                FileLog.e("WebProxyManager: failed to set default Java Authenticator", e)
            }
        }
    }

    @JvmStatic
    fun getEffectiveProxy(): Proxy {
        return when (NekoConfig.webProxyMode.Int()) {
            NekoConfig.WEB_PROXY_MODE_FOLLOW_TELEGRAM -> {
                if (SharedConfig.isProxyEnabled() && SharedConfig.currentProxy != null) {
                    val info = SharedConfig.currentProxy
                    // SOCKS5 proxies have empty secret; MTProto proxies have non-empty secret.
                    if (info.secret.isNullOrEmpty() && !info.address.isNullOrEmpty() && info.port > 0) {
                        try {
                            Proxy(Proxy.Type.SOCKS, InetSocketAddress(info.address, info.port))
                        } catch (e: Throwable) {
                            FileLog.e("WebProxyManager: invalid TG SOCKS5 proxy", e)
                            Proxy.NO_PROXY
                        }
                    } else {
                        // MTProto proxy cannot handle arbitrary HTTP/HTTPS traffic
                        Proxy.NO_PROXY
                    }
                } else {
                    Proxy.NO_PROXY
                }
            }
            NekoConfig.WEB_PROXY_MODE_CUSTOM -> {
                val host = NekoConfig.webProxyHost.String()?.trim()
                val port = NekoConfig.webProxyPort.String()?.trim()?.toIntOrNull() ?: 0
                if (!host.isNullOrEmpty() && port in 1..65535) {
                    try {
                        val proxyType = if (NekoConfig.webProxyType.Int() == NekoConfig.WEB_PROXY_TYPE_SOCKS5) {
                            Proxy.Type.SOCKS
                        } else {
                            Proxy.Type.HTTP
                        }
                        Proxy(proxyType, InetSocketAddress(host, port))
                    } catch (e: Throwable) {
                        FileLog.e("WebProxyManager: invalid custom proxy address", e)
                        Proxy.NO_PROXY
                    }
                } else {
                    Proxy.NO_PROXY
                }
            }
            else -> Proxy.NO_PROXY
        }
    }

    @JvmStatic
    fun getEffectiveProxyCredentials(): Pair<String, String>? {
        return when (NekoConfig.webProxyMode.Int()) {
            NekoConfig.WEB_PROXY_MODE_FOLLOW_TELEGRAM -> {
                if (SharedConfig.isProxyEnabled() && SharedConfig.currentProxy != null) {
                    val info = SharedConfig.currentProxy
                    if (info.secret.isNullOrEmpty() && !info.username.isNullOrEmpty()) {
                        Pair(info.username ?: "", info.password ?: "")
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
            NekoConfig.WEB_PROXY_MODE_CUSTOM -> {
                val user = NekoConfig.webProxyUsername.String()?.trim() ?: ""
                val pass = NekoConfig.webProxyPassword.String()?.trim() ?: ""
                if (user.isNotEmpty()) {
                    Pair(user, pass)
                } else {
                    null
                }
            }
            else -> null
        }
    }

    @JvmStatic
    fun createDynamicProxySelector(): ProxySelector {
        ensureJavaAuthenticator()
        return object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> {
                val proxy = getEffectiveProxy()
                return listOf(proxy)
            }

            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                FileLog.e("WebProxyManager: proxy connection failed for URI: $uri, target: $sa", ioe)
            }
        }
    }

    @JvmStatic
    val okHttpProxyAuthenticator: OkHttpAuthenticator = OkHttpAuthenticator { _: Route?, response: Response ->
        if (response.code == 407) {
            val creds = getEffectiveProxyCredentials()
            if (creds != null && creds.first.isNotEmpty()) {
                val credentialHeader = Credentials.basic(creds.first, creds.second)
                return@OkHttpAuthenticator response.request.newBuilder()
                    .header("Proxy-Authorization", credentialHeader)
                    .build()
            }
        }
        null
    }
}
