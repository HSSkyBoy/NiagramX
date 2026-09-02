package top.nkbe.niagram.helpers

import android.app.Activity
import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.LayoutHelper
import xyz.nextalone.nagram.NaConfig
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

object FontHelper {

    const val CATEGORY_REGULAR = 0
    const val CATEGORY_BOLD = 1
    const val CATEGORY_ITALIC = 2
    const val CATEGORY_MONO = 3

    private val cachedTypefaces = ConcurrentHashMap<String, Typeface>()

    private val fontsDirectory: File
        get() {
            val dir = File(ApplicationLoader.applicationContext.filesDir, "custom_fonts")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    @JvmStatic
    fun isFontFile(file: File?): Boolean {
        if (file == null || !file.exists() || file.length() == 0L) return false
        val name = file.name.lowercase()
        return name.endsWith(".ttf") || name.endsWith(".otf")
    }

    @JvmStatic
    fun getFontFamilyName(file: File): String {
        var name = file.name
        val dot = name.lastIndexOf('.')
        if (dot != -1) name = name.substring(0, dot)
        if (name.startsWith("font_")) {
            val parts = name.split("_", limit = 3)
            if (parts.size >= 3) {
                name = parts[2]
            } else if (parts.size == 2) {
                name = parts[1]
            }
        }
        return name.ifEmpty { file.name }
    }

    @JvmStatic
    fun importFontFile(sourceFile: File): File? {
        return try {
            val md = MessageDigest.getInstance("MD5")
            FileInputStream(sourceFile).use { fis ->
                val buffer = ByteArray(4096)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    md.update(buffer, 0, read)
                }
            }
            val hash = md.digest().joinToString("") { "%02x".format(it) }
            val cleanName = sourceFile.name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val targetFile = File(fontsDirectory, "font_${hash.substring(0, 8)}_$cleanName")
            if (!targetFile.exists() || targetFile.length() != sourceFile.length()) {
                FileInputStream(sourceFile).use { fis ->
                    AndroidUtilities.copyFile(fis, targetFile)
                }
            }
            targetFile
        } catch (e: Exception) {
            FileLog.e("Failed to import font: " + sourceFile.absolutePath, e)
            null
        }
    }

    @JvmStatic
    fun hasCustomFont(category: Int): Boolean {
        val path = when (category) {
            CATEGORY_REGULAR -> NaConfig.customFontRegular.String()
            CATEGORY_BOLD -> NaConfig.customFontBold.String()
            CATEGORY_ITALIC -> NaConfig.customFontItalic.String()
            CATEGORY_MONO -> NaConfig.customFontMono.String()
            else -> null
        }
        return !path.isNullOrEmpty() && File(path).exists()
    }

    @JvmStatic
    fun hasAnyCustomFont(): Boolean {
        return hasCustomFont(CATEGORY_REGULAR) ||
                hasCustomFont(CATEGORY_BOLD) ||
                hasCustomFont(CATEGORY_ITALIC) ||
                hasCustomFont(CATEGORY_MONO)
    }

    @JvmStatic
    fun applyFont(category: Int, fontPath: String) {
        when (category) {
            CATEGORY_REGULAR -> NaConfig.customFontRegular.value = fontPath
            CATEGORY_BOLD -> NaConfig.customFontBold.value = fontPath
            CATEGORY_ITALIC -> NaConfig.customFontItalic.value = fontPath
            CATEGORY_MONO -> NaConfig.customFontMono.value = fontPath
        }
        cachedTypefaces.clear()
        AndroidUtilities.clearTypefaceCache()
        if (ApplicationLoader.applicationContext != null) {
            Theme.reloadAllResources(ApplicationLoader.applicationContext)
        }
    }

    @JvmStatic
    fun getCustomTypeface(category: Int): Typeface? {
        val path = when (category) {
            CATEGORY_REGULAR -> NaConfig.customFontRegular.String()
            CATEGORY_BOLD -> NaConfig.customFontBold.String()
            CATEGORY_ITALIC -> NaConfig.customFontItalic.String()
            CATEGORY_MONO -> NaConfig.customFontMono.String()
            else -> null
        }
        if (path.isNullOrEmpty()) return null
        val file = File(path)
        if (!file.exists()) return null
        return cachedTypefaces.getOrPut(path) {
            try {
                Typeface.createFromFile(file)
            } catch (e: Exception) {
                FileLog.e("Failed to load custom typeface from $path", e)
                null
            }
        }
    }

    @JvmStatic
    fun showFontPreviewDialog(activity: Activity?, fontFile: File) {
        if (activity == null || !fontFile.exists()) return
        val typeface = try {
            Typeface.createFromFile(fontFile)
        } catch (e: Exception) {
            FileLog.e("Invalid font file", e)
            BulletinFactory.global().createErrorBulletin(getString(R.string.FontInvalidFile)).show()
            return
        }

        val fontName = getFontFamilyName(fontFile)
        val builder = AlertDialog.Builder(activity)
        builder.setTitle(getString(R.string.FontPreviewTitle) + " - " + fontName)

        val scrollView = ScrollView(activity)
        val linearLayout = LinearLayout(activity)
        linearLayout.orientation = LinearLayout.VERTICAL
        linearLayout.setPadding(dp(20f), dp(10f), dp(20f), dp(10f))

        val titleView = TextView(activity)
        titleView.text = "The quick brown fox jumps over the lazy dog\n1234567890\n天地玄黃 宇宙洪荒 日月盈昃"
        titleView.textSize = 17f
        titleView.typeface = typeface
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
        linearLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0f, 0f, 16f))

        val smallView = TextView(activity)
        smallView.text = "NiagramX is a Telegram client mod."
        smallView.textSize = 14f
        smallView.typeface = typeface
        smallView.setTextColor(Theme.getColor(Theme.key_dialogTextGray))
        linearLayout.addView(smallView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0f, 0f, 10f))

        scrollView.addView(linearLayout)
        builder.setView(scrollView)
        builder.setPositiveButton(getString(R.string.FontApply)) { _, _ ->
            showCategorySelectDialog(activity, fontFile)
        }
        builder.setNegativeButton(getString(R.string.Cancel), null)
        builder.show()
    }

    @JvmStatic
    fun showCategorySelectDialog(activity: Activity?, fontFile: File) {
        if (activity == null) return
        val imported = importFontFile(fontFile) ?: fontFile
        val items = arrayOf(
            getString(R.string.FontCategoryRegular),
            getString(R.string.FontCategoryBold),
            getString(R.string.FontCategoryItalic),
            getString(R.string.FontCategoryMono)
        )
        AlertDialog.Builder(activity)
            .setTitle(getString(R.string.FontApplyAs))
            .setItems(items) { _, which ->
                applyFont(which, imported.absolutePath)
                BulletinFactory.global().createSuccessBulletin(getString(R.string.FontAppliedSuccess)).show()
            }
            .setNegativeButton(getString(R.string.Cancel), null)
            .show()
    }
}
