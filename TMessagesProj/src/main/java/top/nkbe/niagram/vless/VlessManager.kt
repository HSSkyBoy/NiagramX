package top.nkbe.niagram.vless

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.SharedConfig

/**
 * Manager responsible for:
 * 1. Persisting VLESS configurations in SharedPreferences.
 * 2. Managing the lifecycle of VlessServer.
 * 3. Synchronizing VLESS tunnel state with Telegram's SharedConfig.currentProxy.
 */
object VlessManager : NotificationCenter.NotificationCenterDelegate {

    private const val PREFS_NAME = "vless_config_prefs"
    private const val KEY_NODES = "nodes_json"
    private const val KEY_ACTIVE_NODE_ID = "active_node_id"
    const val VLESS_PROXY_USER_TAG = "VLESS"
    const val DEFAULT_LOCAL_PORT = 10853

    private val gson = Gson()
    private val vlessServer = VlessServer()

    private var initialized = false

    val isRunning: Boolean
        get() = vlessServer.isRunning

    val localPort: Int
        get() = vlessServer.localPort

    private val prefs: SharedPreferences by lazy {
        ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun init() {
        if (initialized) return
        initialized = true

        AndroidUtilities.runOnUIThread {
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged)
        }

        // Auto-resume tunnel if Telegram proxy is currently enabled and points to VLESS
        checkAndResumeTunnel()
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (id == NotificationCenter.proxySettingsChanged) {
            val current = SharedConfig.currentProxy
            val enabled = SharedConfig.isProxyEnabled()

            if (!enabled || current == null || !isVlessProxy(current)) {
                // User disabled proxy or switched to another proxy
                if (vlessServer.isRunning) {
                    FileLog.d("VlessManager: proxy turned off or non-VLESS proxy selected, stopping server")
                    vlessServer.stop()
                }
            } else if (isVlessProxy(current) && (!vlessServer.isRunning || vlessServer.localPort != current.port)) {
                // User re-enabled VLESS proxy or selected another VLESS node
                val node = getNodeById(current.password) ?: getActiveNode()
                if (node != null) {
                    startTunnel(node, preferredPort = if (current.port > 0) current.port else DEFAULT_LOCAL_PORT)
                }
            }
        }
    }

    fun getNodes(): List<VlessConfig> {
        val json = prefs.getString(KEY_NODES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<VlessConfig>>() {}.type
            gson.fromJson<List<VlessConfig>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            FileLog.e("VlessManager: failed to parse nodes json", e)
            emptyList()
        }
    }

    @Synchronized
    fun saveNodes(nodes: List<VlessConfig>) {
        val json = gson.toJson(nodes)
        prefs.edit().putString(KEY_NODES, json).apply()
    }

    @Synchronized
    fun addOrUpdateNode(config: VlessConfig): VlessConfig {
        val nodes = getNodes().toMutableList()
        val index = nodes.indexOfFirst { it.id == config.id || (it.host == config.host && it.port == config.port && it.uuid == config.uuid) }
        if (index >= 0) {
            nodes[index] = config
        } else {
            nodes.add(0, config)
        }
        saveNodes(nodes)
        return config
    }

    @Synchronized
    fun deleteNode(nodeId: String) {
        val nodes = getNodes().toMutableList()
        nodes.removeAll { it.id == nodeId }
        saveNodes(nodes)

        if (getActiveNodeId() == nodeId) {
            setActiveNodeId(null)
            if (vlessServer.isRunning) {
                stopTunnel()
            }
        }
    }

    fun getNodeById(nodeId: String?): VlessConfig? {
        if (nodeId.isNullOrBlank()) return null
        return getNodes().find { it.id == nodeId }
    }

    fun getActiveNodeId(): String? {
        return prefs.getString(KEY_ACTIVE_NODE_ID, null)
    }

    private fun setActiveNodeId(id: String?) {
        prefs.edit().putString(KEY_ACTIVE_NODE_ID, id).apply()
    }

    fun getActiveNode(): VlessConfig? {
        val id = getActiveNodeId() ?: return null
        return getNodeById(id)
    }

    /**
     * Checks if a [SharedConfig.ProxyInfo] represents a VLESS tunnel managed by [VlessManager].
     */
    @JvmStatic
    fun isVlessProxy(proxyInfo: SharedConfig.ProxyInfo?): Boolean {
        if (proxyInfo == null) return false
        return proxyInfo.username == VLESS_PROXY_USER_TAG ||
                (proxyInfo.address == "127.0.0.1" && proxyInfo.port == vlessServer.localPort && vlessServer.localPort > 0)
    }

    /**
     * Retrieves the friendly display name for a VLESS proxy item in ProxyList.
     */
    @JvmStatic
    fun getNodeRemark(proxyInfo: SharedConfig.ProxyInfo?): String? {
        if (!isVlessProxy(proxyInfo)) return null
        val nodeId = proxyInfo?.password
        val node = getNodeById(nodeId) ?: getActiveNode()
        return node?.remarks
    }

    /**
     * Starts the VLESS tunnel for the given node and configures Telegram's currentProxy.
     */
    @Synchronized
    fun startTunnel(config: VlessConfig, preferredPort: Int = DEFAULT_LOCAL_PORT): Int {
        try {
            val port = vlessServer.start(config, preferredPort)
            setActiveNodeId(config.id)

            // Register with Telegram's proxy manager
            val proxyInfo = SharedConfig.ProxyInfo(
                "127.0.0.1",
                port,
                VLESS_PROXY_USER_TAG,
                config.id, // Store config ID in password for retrieval
                ""         // Empty secret denotes SOCKS5
            )

            AndroidUtilities.runOnUIThread {
                val addedInfo = SharedConfig.addProxy(proxyInfo)
                SharedConfig.currentProxy = addedInfo
                SharedConfig.setProxyEnable(true)
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged)
            }

            FileLog.d("VlessManager: tunnel started on port $port for node ${config.remarks}")
            return port
        } catch (e: Exception) {
            FileLog.e("VlessManager: failed to start tunnel", e)
            throw e
        }
    }

    /**
     * Stops the VLESS tunnel and disables Telegram proxy if currentProxy is VLESS.
     */
    @Synchronized
    fun stopTunnel() {
        vlessServer.stop()
        setActiveNodeId(null)

        AndroidUtilities.runOnUIThread {
            if (isVlessProxy(SharedConfig.currentProxy)) {
                SharedConfig.setProxyEnable(false)
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged)
            }
        }
    }

    private fun checkAndResumeTunnel() {
        try {
            if (SharedConfig.isProxyEnabled() && isVlessProxy(SharedConfig.currentProxy)) {
                val activeNode = getActiveNode()
                if (activeNode != null && !vlessServer.isRunning) {
                    startTunnel(activeNode)
                }
            }
        } catch (e: Exception) {
            FileLog.e("VlessManager: error during checkAndResumeTunnel", e)
        }
    }
}
