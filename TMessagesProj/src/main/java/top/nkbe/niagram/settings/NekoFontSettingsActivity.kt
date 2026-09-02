package top.nkbe.niagram.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.TextCheckCell
import org.telegram.ui.Cells.TextDetailSettingsCell
import org.telegram.ui.Cells.TextSettingsCell
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.SeekBarView
import top.nkbe.niagram.NekoConfig
import top.nkbe.niagram.helpers.FontHelper
import xyz.nextalone.nagram.NaConfig
import java.io.File

class NekoFontSettingsActivity : BaseFragment() {

    private var listView: RecyclerListView? = null
    private var listAdapter: ListAdapter? = null

    private var rowCount = 0
    private var headerTypefaceRow = -1
    private var useDefaultTypefaceRow = -1
    private var forceFontWeightFallbackRow = -1
    private var divider1Row = -1
    private var headerCategoriesRow = -1
    private var regularFontRow = -1
    private var boldFontRow = -1
    private var italicFontRow = -1
    private var monoFontRow = -1
    private var divider2Row = -1
    private var headerInputTextSizeRow = -1
    private var inputTextSizeSliderRow = -1
    private var divider3Row = -1

    private var pendingPickCategory: Int = -1

    override fun onFragmentCreate(): Boolean {
        super.onFragmentCreate()
        updateRows()
        return true
    }

    private fun updateRows() {
        rowCount = 0
        headerTypefaceRow = rowCount++
        useDefaultTypefaceRow = rowCount++
        forceFontWeightFallbackRow = rowCount++
        divider1Row = rowCount++
        headerCategoriesRow = rowCount++
        regularFontRow = rowCount++
        boldFontRow = rowCount++
        italicFontRow = rowCount++
        monoFontRow = rowCount++
        divider2Row = rowCount++
        headerInputTextSizeRow = rowCount++
        inputTextSizeSliderRow = rowCount++
        divider3Row = rowCount++
    }

    override fun createView(context: Context): View {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back)
        actionBar.setTitle(getString(R.string.FontSettings))
        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false)
        }
        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                if (id == -1) {
                    finishFragment()
                }
            }
        })

        fragmentView = FrameLayout(context).apply {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
        }
        val frameLayout = fragmentView as FrameLayout

        listView = RecyclerListView(context).apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            adapter = ListAdapter(context).also { listAdapter = it }
            itemAnimator = DefaultItemAnimator()
            setVerticalScrollBarEnabled(false)
        }
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

        listView?.setOnItemClickListener { view, position ->
            when (position) {
                useDefaultTypefaceRow -> {
                    NekoConfig.typeface.value = !NekoConfig.typeface.Bool()
                    AndroidUtilities.clearTypefaceCache()
                    if (ApplicationLoader.applicationContext != null) {
                        Theme.reloadAllResources(ApplicationLoader.applicationContext)
                    }
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewTheme, false, true, true)
                    (view as? TextCheckCell)?.setChecked(NekoConfig.typeface.Bool())
                    listAdapter?.notifyDataSetChanged()
                }
                forceFontWeightFallbackRow -> {
                    NekoConfig.forceFontWeightFallback.value = !NekoConfig.forceFontWeightFallback.Bool()
                    AndroidUtilities.clearTypefaceCache()
                    if (ApplicationLoader.applicationContext != null) {
                        Theme.reloadAllResources(ApplicationLoader.applicationContext)
                    }
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewTheme, false, true, true)
                    (view as? TextCheckCell)?.setChecked(NekoConfig.forceFontWeightFallback.Bool())
                }
                regularFontRow -> showFontOptionsDialog(FontHelper.CATEGORY_REGULAR)
                boldFontRow -> showFontOptionsDialog(FontHelper.CATEGORY_BOLD)
                italicFontRow -> showFontOptionsDialog(FontHelper.CATEGORY_ITALIC)
                monoFontRow -> showFontOptionsDialog(FontHelper.CATEGORY_MONO)
            }
        }

        return fragmentView
    }

    private fun showFontOptionsDialog(category: Int) {
        val items = arrayOf(
            getString(R.string.FontSelectFile),
            getString(R.string.FontResetToDefault)
        )
        AlertDialog.Builder(parentActivity)
            .setTitle(getCategoryTitle(category))
            .setItems(items) { _, which ->
                if (which == 0) {
                    pendingPickCategory = category
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    startActivityForResult(Intent.createChooser(intent, getString(R.string.FontSelectFile)), 1001)
                } else {
                    FontHelper.applyFont(category, "")
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewTheme, false, true, true)
                    listAdapter?.notifyDataSetChanged()
                    BulletinFactory.of(this).createSuccessBulletin(getString(R.string.FontAppliedSuccess)).show()
                }
            }
            .setNegativeButton(getString(R.string.Cancel), null)
            .show()
    }

    override fun onActivityResultFragment(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResultFragment(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == Activity.RESULT_OK && data?.data != null && pendingPickCategory != -1) {
            val uri = data.data ?: return
            val category = pendingPickCategory
            pendingPickCategory = -1
            try {
                val tempFile = File(parentActivity.cacheDir, "temp_font_" + System.currentTimeMillis() + ".ttf")
                parentActivity.contentResolver.openInputStream(uri)?.use { input ->
                    AndroidUtilities.copyFile(input, tempFile)
                }
                if (tempFile.exists()) {
                    val imported = FontHelper.importFontFile(tempFile) ?: tempFile
                    FontHelper.applyFont(category, imported.absolutePath)
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewTheme, false, true, true)
                    listAdapter?.notifyDataSetChanged()
                    BulletinFactory.of(this).createSuccessBulletin(getString(R.string.FontAppliedSuccess)).show()
                }
            } catch (e: Exception) {
                BulletinFactory.of(this).createErrorBulletin(getString(R.string.FontInvalidFile)).show()
            }
        }
    }

    private fun getCategoryTitle(category: Int): String {
        return when (category) {
            FontHelper.CATEGORY_REGULAR -> getString(R.string.FontCategoryRegular)
            FontHelper.CATEGORY_BOLD -> getString(R.string.FontCategoryBold)
            FontHelper.CATEGORY_ITALIC -> getString(R.string.FontCategoryItalic)
            FontHelper.CATEGORY_MONO -> getString(R.string.FontCategoryMono)
            else -> ""
        }
    }

    private fun getCategoryFontName(category: Int): String {
        val path = when (category) {
            FontHelper.CATEGORY_REGULAR -> NaConfig.customFontRegular.String()
            FontHelper.CATEGORY_BOLD -> NaConfig.customFontBold.String()
            FontHelper.CATEGORY_ITALIC -> NaConfig.customFontItalic.String()
            FontHelper.CATEGORY_MONO -> NaConfig.customFontMono.String()
            else -> null
        }
        if (path.isNullOrEmpty()) return getString(R.string.Default)
        val file = File(path)
        return if (file.exists()) FontHelper.getFontFamilyName(file) else getString(R.string.Default)
    }

    override fun onResume() {
        super.onResume()
        listAdapter?.notifyDataSetChanged()
    }

    private inner class ListAdapter(private val mContext: Context) : RecyclerListView.SelectionAdapter() {

        override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
            val position = holder.adapterPosition
            return position == useDefaultTypefaceRow ||
                    position == forceFontWeightFallbackRow ||
                    position == regularFontRow ||
                    position == boldFontRow ||
                    position == italicFontRow ||
                    position == monoFontRow
        }

        override fun getItemCount(): Int = rowCount

        override fun getItemViewType(position: Int): Int {
            return when (position) {
                headerTypefaceRow, headerCategoriesRow, headerInputTextSizeRow -> 0
                useDefaultTypefaceRow, forceFontWeightFallbackRow -> 1
                regularFontRow, boldFontRow, italicFontRow, monoFontRow -> 2
                inputTextSizeSliderRow -> 4
                else -> 5
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view: View = when (viewType) {
                0 -> HeaderCell(mContext).apply {
                    setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
                }
                1 -> TextCheckCell(mContext).apply {
                    setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
                }
                2 -> TextDetailSettingsCell(mContext).apply {
                    setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
                }
                4 -> buildSeekBarRow()
                else -> View(mContext).apply {
                    setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12f))
                }
            }
            return RecyclerListView.Holder(view)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder.itemViewType) {
                0 -> {
                    val headerCell = holder.itemView as HeaderCell
                    when (position) {
                        headerTypefaceRow -> headerCell.setText(getString(R.string.Appearance))
                        headerCategoriesRow -> headerCell.setText(getString(R.string.FontSettings))
                        headerInputTextSizeRow -> headerCell.setText(getString(R.string.InputFieldTextSize))
                    }
                }
                1 -> {
                    val checkCell = holder.itemView as TextCheckCell
                    when (position) {
                        useDefaultTypefaceRow -> checkCell.setTextAndCheck(
                            getString(R.string.TypefaceUseDefault),
                            NekoConfig.typeface.Bool(),
                            true
                        )
                        forceFontWeightFallbackRow -> checkCell.setTextAndCheck(
                            getString(R.string.ForceFontWeightFallback),
                            NekoConfig.forceFontWeightFallback.Bool(),
                            false
                        )
                    }
                }
                2 -> {
                    val cell = holder.itemView as TextDetailSettingsCell
                    when (position) {
                        regularFontRow -> cell.setTextAndValue(getString(R.string.FontCategoryRegular), getCategoryFontName(FontHelper.CATEGORY_REGULAR), true)
                        boldFontRow -> cell.setTextAndValue(getString(R.string.FontCategoryBold), getCategoryFontName(FontHelper.CATEGORY_BOLD), true)
                        italicFontRow -> cell.setTextAndValue(getString(R.string.FontCategoryItalic), getCategoryFontName(FontHelper.CATEGORY_ITALIC), true)
                        monoFontRow -> cell.setTextAndValue(getString(R.string.FontCategoryMono), getCategoryFontName(FontHelper.CATEGORY_MONO), false)
                    }
                }
                4 -> {
                    val frame = holder.itemView as FrameLayout
                    val seekBarView = frame.getChildAt(0) as? SeekBarView ?: return
                    val currentSize = NaConfig.inputFieldTextSize.Int()
                    val progress = if (currentSize <= 0) 0f else (currentSize - 12f) / 16f
                    seekBarView.setProgress(progress.coerceIn(0f, 1f))
                    seekBarView.delegate = SeekBarView.SeekBarViewDelegate { _, p ->
                        val size = if (p <= 0.05f) 0 else (12 + (p * 16).toInt())
                        NaConfig.inputFieldTextSize.value = size
                    }
                }
            }
        }

        private fun buildSeekBarRow(): FrameLayout {
            return FrameLayout(mContext).apply {
                setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
                val seekBar = SeekBarView(mContext)
                seekBar.setReportChanges(true)
                addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38f, Gravity.CENTER_VERTICAL, 16f, 4f, 16f, 4f))
            }
        }
    }
}
