package top.nkbe.niagram.llm.ui

import android.content.Context
import android.util.TypedValue
import android.view.View
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.EditTextBoldCursor

object EditTextFactory {

    @JvmStatic
    fun createAndSetupEditText(
        context: Context,
        resourcesProvider: Theme.ResourcesProvider?,
        initialText: String?,
        hintText: String?,
        imeOptions: Int,
        requestFocus: Boolean
    ): EditTextBoldCursor {
        val editText = object : EditTextBoldCursor(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(dp(64f), View.MeasureSpec.EXACTLY))
            }
        }
        applyCommonStyle(editText, resourcesProvider, initialText, hintText, requestFocus)
        editText.isSingleLine = true
        editText.imeOptions = imeOptions
        editText.setPadding(0, 0, 0, 0)
        return editText
    }

    @JvmStatic
    fun createAndSetupMultilineEditText(
        context: Context,
        resourcesProvider: Theme.ResourcesProvider?,
        initialText: String?,
        hintText: String?,
        imeOptions: Int,
        requestFocus: Boolean
    ): EditTextBoldCursor {
        val editText = EditTextBoldCursor(context)
        editText.lineYFix = true
        applyCommonStyle(editText, resourcesProvider, initialText, hintText, requestFocus)
        editText.isSingleLine = false
        editText.minLines = 1
        editText.imeOptions = imeOptions
        editText.setPadding(0, 0, 0, dp(6f))
        return editText
    }

    private fun applyCommonStyle(
        editText: EditTextBoldCursor,
        resourcesProvider: Theme.ResourcesProvider?,
        initialText: String?,
        hintText: String?,
        requestFocus: Boolean
    ) {
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider))
        editText.setHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourcesProvider))
        editText.setHeaderHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider))
        editText.isFocusable = true
        editText.setLineColors(
            Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resourcesProvider),
            Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourcesProvider),
            Theme.getColor(Theme.key_text_RedRegular, resourcesProvider)
        )
        editText.background = null
        editText.setText(initialText ?: "")
        editText.setHintText(hintText)
        if (requestFocus) {
            AndroidUtilities.runOnUIThread({
                editText.requestFocus()
                editText.setSelection(editText.length())
                AndroidUtilities.showKeyboard(editText)
            }, 250)
        }
    }
}
