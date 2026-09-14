@file:Suppress("UNCHECKED_CAST")

package top.nkbe.niagram.utils

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Environment
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.graphics.createBitmap
import androidx.core.view.setPadding
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.WriterException
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.TelegramQRCodeWriter
import org.telegram.messenger.browser.Browser
import top.nkbe.niagram.config.NyaConfig
import top.nkbe.niagram.helpers.WebSocketHelper
import top.nkbe.niagram.ui.BottomBuilder
import top.nkbe.niagram.utils.AlertUtil.showToast
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit


object ProxyUtil {

    private var networkCallbackRegistered = false

    @JvmStatic
    fun registerNetworkCallback() {
        top.nkbe.niagram.vless.VlessManager.init()
        if (networkCallbackRegistered) return
        networkCallbackRegistered = true

        val connectivityManager = ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCallback: ConnectivityManager.NetworkCallback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val networkCapabilities =
                        connectivityManager.getNetworkCapabilities(network) ?: return
                    val vpn = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

                    if (vpn) {
                        if (NyaConfig.disableProxyWhenVpnEnabled.Bool()) {
                            WebSocketHelper.stopServer()
                            if (SharedConfig.isProxyEnabled()) {
                                SharedConfig.setProxyEnable(false)
                                AndroidUtilities.runOnUIThread {
                                    NotificationCenter.getGlobalInstance()
                                        .postNotificationName(NotificationCenter.proxySettingsChanged)
                                }
                            }
                        }
                    } else {
                        if (NyaConfig.disableProxyWhenVpnEnabled.Bool() && !SharedConfig.isProxyEnabled()) {
                            if (SharedConfig.currentProxy == null && !SharedConfig.proxyList.isEmpty()) {
                                SharedConfig.setCurrentProxy(SharedConfig.proxyList[0])
                            }
                            if (SharedConfig.currentProxy != null) {
                                SharedConfig.setProxyEnable(true)
                                AndroidUtilities.runOnUIThread {
                                    NotificationCenter.getGlobalInstance()
                                        .postNotificationName(NotificationCenter.proxySettingsChanged)
                                }
                            }
                        } else if (SharedConfig.currentProxy == null) {
                            if (!SharedConfig.proxyList.isEmpty()) {
                                SharedConfig.setCurrentProxy(SharedConfig.proxyList[0])
                            }
                        }
                    }

                    if (SharedConfig.isProxyEnabled() && SharedConfig.proxyAutoSpeedAcceleration) {
                        AndroidUtilities.runOnUIThread {
                            org.telegram.messenger.ProxyRotationController.checkAndAccelerate(false)
                        }
                    }
                }
            }

        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } catch (_: Exception) {}
    }

    private var geoIpChecked = false

    @JvmStatic
    fun checkAndActivateMainlandProxy() {
        if (geoIpChecked) return
        geoIpChecked = true

        if (!NyaConfig.autoActivateMainlandProxy.Bool()) return

        val connectivityManager = ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork != null) {
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                return
            }
        }

        Thread {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(3, TimeUnit.SECONDS)
                    .readTimeout(3, TimeUnit.SECONDS)
                    .build()
                val request = Request.Builder()
                    .url("http://ip-api.com/json/?fields=countryCode,region,status")
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val json = JSONObject(body)
                        val countryCode = json.optString("countryCode", "")
                        val region = json.optString("region", "")
                        val isMainlandOrHainan = "CN".equals(countryCode, ignoreCase = true) || "HI".equals(region, ignoreCase = true) || "HAINAN".equals(region, ignoreCase = true)
                        if (isMainlandOrHainan) {
                            FileLog.d("Detected Mainland / Hainan IP: $countryCode, region: $region. Activating built-in proxy...")
                            AndroidUtilities.runOnUIThread {
                                SharedConfig.loadProxyList()
                                var builtInInfo: SharedConfig.ProxyInfo? = null
                                for (info in SharedConfig.proxyList) {
                                    if (WebSocketHelper.proxyServer == info.address) {
                                        builtInInfo = info
                                        break
                                    }
                                }
                                if (builtInInfo != null) {
                                    SharedConfig.setCurrentProxy(builtInInfo)
                                    SharedConfig.setProxyEnable(true)
                                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged)
                                    FileLog.d("Built-in tcp2ws proxy activated successfully.")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                FileLog.e("Failed to check GeoIP: ${e.message}")
            }
        }.start()
    }

    @JvmStatic
    fun getOwnerActivity(ctx: Context): Activity {

        if (ctx is Activity) return ctx

        if (ctx is ContextWrapper) return getOwnerActivity(ctx.baseContext)

        error("unable cast ${ctx.javaClass.name} to activity")

    }

    @JvmStatic
    @JvmOverloads
    fun showQrDialog(ctx: Context, text: String, icon: ((Int) -> Bitmap)? = null): AlertDialog {

        val code = createQRCode(text, icon = icon)

        ctx.setTheme(R.style.Theme_TMessages)

        return AlertDialog.Builder(ctx).setView(LinearLayout(ctx).apply {

            gravity = Gravity.CENTER
            setBackgroundColor(Color.TRANSPARENT)

            addView(LinearLayout(ctx).apply {
                val root = this

                gravity = Gravity.CENTER
                setBackgroundColor(Color.WHITE)
                setPadding(AndroidUtilities.dp(16f))

                val width = AndroidUtilities.dp(260f)

                addView(ImageView(ctx).apply {

                    setImageBitmap(code)

                    scaleType = ImageView.ScaleType.FIT_XY

                    setOnLongClickListener {

                        val builder = BottomBuilder(ctx)

                        builder.addItems(arrayOf(

                                getString(R.string.SaveToGallery),
                                getString(R.string.Cancel)

                        ), intArrayOf(

                                R.drawable.msg_gallery,
                                R.drawable.msg_cancel

                        )) { i, _, _ ->

                            if (i == 0) {

                                if (ctx.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

                                    getOwnerActivity(ctx).requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 4)

                                    return@addItems

                                }

                                val saveTo = File(Environment.getExternalStorageDirectory(), "${Environment.DIRECTORY_PICTURES}/share_${text.hashCode()}.jpg")

                                saveTo.parentFile?.mkdirs()

                                runCatching {

                                    saveTo.createNewFile()

                                    saveTo.outputStream().use {

                                        loadBitmapFromView(root).compress(Bitmap.CompressFormat.JPEG, 100, it)

                                    }

                                    AndroidUtilities.addMediaToGallery(saveTo.path)
                                    showToast(getString(R.string.PhotoSavedHint))

                                }.onFailure {
                                    FileLog.e(it)
                                    showToast(it)
                                }

                            }

                        }

                        builder.show()

                        return@setOnLongClickListener true

                    }

                }, LinearLayout.LayoutParams(width, width))

            }, LinearLayout.LayoutParams(-2, -2).apply {

                gravity = Gravity.CENTER

            })

        }).create().apply {

            show()
            window?.setBackgroundDrawableResource(android.R.color.transparent)

        }

    }

    private fun loadBitmapFromView(v: View): Bitmap {
        val b = createBitmap(v.width, v.height)
        val c = Canvas(b)
        v.layout(v.left, v.top, v.right, v.bottom)
        v.draw(c)
        return b
    }

    @JvmStatic
    fun createQRCode(text: String, size: Int = 768, icon: ((Int) -> Bitmap)? = null): Bitmap {
        return try {
            val hints = HashMap<EncodeHintType, Any>()
            hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
            TelegramQRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints, null, null, icon)
        } catch (e: WriterException) {
            FileLog.e(e)
            createBitmap(size, size)
        }
    }

    val qrReader = QRCodeReader()

    @JvmStatic
    fun tryReadQR(ctx: Activity, bitmap: Bitmap) {

        val intArray = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(intArray, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, intArray)

        try {

            val result = try {
                qrReader.decode(BinaryBitmap(GlobalHistogramBinarizer(source)), mapOf(
                        DecodeHintType.TRY_HARDER to true
                ))
            } catch (_: NotFoundException) {
                qrReader.decode(BinaryBitmap(GlobalHistogramBinarizer(source.invert())), mapOf(
                        DecodeHintType.TRY_HARDER to true
                ))
            }

            showLinkAlert(ctx, result.text)

        } catch (_: Throwable) {

            showToast(getString(R.string.NoQrFound))

        }

    }

    @JvmStatic
    @JvmOverloads
    fun showLinkAlert(ctx: Activity, text: String, tryInternal: Boolean = true) {

        val builder = BottomBuilder(ctx)

        if (tryInternal) {
            runCatching {
                if (Browser.isInternalUrl(text, booleanArrayOf(false))) {
                    Browser.openUrl(ctx, text)
                    return
                }
            }
        }

        builder.addTitle(text)

        builder.addItems(arrayOf(
                getString(R.string.Open),
                getString(R.string.Copy),
                getString(R.string.ShareQRCode)
        ), intArrayOf(
                R.drawable.web_browser,
                R.drawable.msg_copy,
                R.drawable.msg_qrcode
        )) { which, _, _ ->
            when (which) {
                0 -> Browser.openUrl(ctx, text)
                1 -> {
                    AndroidUtilities.addToClipboard(text)
                    showToast(getString(R.string.LinkCopied))
                }
                else -> showQrDialog(ctx, text)
            }
        }

        builder.show()

    }

    @JvmStatic
    fun importFromClipboard(ctx: Activity) {

        val text = (ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString()

        val proxies = mutableListOf<SharedConfig.ProxyInfo>()

        var error = false

        text?.trim()?.split('\n')?.map { it.split(" ") }?.forEach { it ->

            it.forEach { line ->

                if (line.startsWith("tg://proxy") ||
                    line.startsWith("tg://socks") ||
                    line.startsWith("https://t.me/proxy") ||
                    line.startsWith("https://t.me/socks")) {

                    runCatching { proxies.add(SharedConfig.ProxyInfo.fromUrl(line)) }.onFailure {

                        error = true

                        showToast(getString(R.string.BrokenLink) + ": ${it.message ?: it.javaClass.simpleName}")

                    }

                } else if (line.startsWith("vless://", ignoreCase = true)) {

                    val vlessConfig = top.nkbe.niagram.vless.VlessConfig.parse(line)
                    if (vlessConfig != null) {
                        top.nkbe.niagram.vless.VlessManager.addOrUpdateNode(vlessConfig)
                        val pInfo = SharedConfig.ProxyInfo(
                            "127.0.0.1",
                            top.nkbe.niagram.vless.VlessManager.DEFAULT_LOCAL_PORT,
                            top.nkbe.niagram.vless.VlessManager.VLESS_PROXY_USER_TAG,
                            vlessConfig.id,
                            ""
                        )
                        proxies.add(pInfo)
                    } else {
                        error = true
                        showToast(getString(R.string.BrokenLink) + ": Invalid VLESS URL")
                    }

                }

            }

        }

        runCatching {

            if (proxies.isEmpty() && !error) {

                String(Base64.decode(text, Base64.NO_PADDING)).trim().split('\n').map { it.split(" ") }.forEach { str ->

                    str.forEach { line ->

                        if (line.startsWith("tg://proxy") ||
                            line.startsWith("tg://socks") ||
                            line.startsWith("https://t.me/proxy") ||
                            line.startsWith("https://t.me/socks")) {

                            runCatching { proxies.add(SharedConfig.ProxyInfo.fromUrl(line)) }.onFailure {

                                error = true

                                showToast(getString(R.string.BrokenLink) + ": ${it.message ?: it.javaClass.simpleName}")

                            }

                        } else if (line.startsWith("vless://", ignoreCase = true)) {

                            val vlessConfig = top.nkbe.niagram.vless.VlessConfig.parse(line)
                            if (vlessConfig != null) {
                                top.nkbe.niagram.vless.VlessManager.addOrUpdateNode(vlessConfig)
                                val pInfo = SharedConfig.ProxyInfo(
                                    "127.0.0.1",
                                    top.nkbe.niagram.vless.VlessManager.DEFAULT_LOCAL_PORT,
                                    top.nkbe.niagram.vless.VlessManager.VLESS_PROXY_USER_TAG,
                                    vlessConfig.id,
                                    ""
                                )
                                proxies.add(pInfo)
                            } else {
                                error = true
                                showToast(getString(R.string.BrokenLink) + ": Invalid VLESS URL")
                            }

                        }

                    }

                }

            }

        }

        if (proxies.isEmpty()) {

            if (!error) showToast(getString(R.string.BrokenLink))

            return

        } else if (!error) {

            AlertUtil.showSimpleAlert(ctx, getString(R.string.ImportedProxies) + "\n\n" + proxies.joinToString("\n") {
                val remark = top.nkbe.niagram.vless.VlessManager.getNodeRemark(it)
                if (remark != null) "VLESS: $remark" else "${it.address}:${it.port}"
            })

        }

        proxies.forEach {

            SharedConfig.addProxy(it)

        }

        AndroidUtilities.runOnUIThread {

            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged)

        }

    }

    @JvmStatic
    fun isIpv6Address(value: String): Boolean {
        var addr = value
        if (addr.indexOf("[") == 0 && addr.lastIndexOf("]") > 0) {
            addr = addr.drop(1)
            addr = addr.dropLast(addr.count() - addr.lastIndexOf("]"))
        }
        val regV6 = Regex("^([0-9A-Fa-f]{1,4})?(:[0-9A-Fa-f]{1,4})*::([0-9A-Fa-f]{1,4})?(:[0-9A-Fa-f]{1,4})*|([0-9A-Fa-f]{1,4})(:[0-9A-Fa-f]{1,4}){7}$")
        return regV6.matches(addr)
    }
}
