package tw.nekomimi.nekogram.ui.components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.Components.LayoutHelper;

@SuppressLint("ViewConstructor")
public class AnimatedTitleView extends FrameLayout {

    private SimpleTextView titleView;
    private SimpleTextView outgoingTitleView;
    private int textColor;

    public AnimatedTitleView(Context context, CharSequence title, int textColor) {
        super(context);
        this.textColor = textColor;
        titleView = createTitleView(title, null);
        addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    public void setTitle(CharSequence title, Drawable rightDrawable) {
        clearOutgoingTitle();
        titleView.animate().setListener(null);
        titleView.animate().cancel();
        titleView.setAlpha(1f);
        titleView.setTranslationX(0);
        titleView.setText(title);
        titleView.setRightDrawable(rightDrawable);
        titleView.setContentDescription(title);
    }

    public void setTitleAnimatedX(CharSequence title, Drawable rightDrawable, boolean forward, long duration) {
        if (TextUtils.equals(titleView.getText(), title) && titleView.getRightDrawable() == rightDrawable) {
            return;
        }
        clearOutgoingTitle();
        titleView.animate().setListener(null);
        titleView.animate().cancel();
        titleView.setAlpha(1f);
        titleView.setTranslationX(0);
        outgoingTitleView = titleView;
        titleView = createTitleView(title, rightDrawable);
        addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        titleView.setAlpha(0);
        titleView.setTranslationX(forward ? dp(20) : -dp(20));
        titleView.animate().alpha(1f).translationX(0).setDuration(duration).start();

        SimpleTextView outgoingView = outgoingTitleView;
        ViewPropertyAnimator animator = outgoingView.animate().alpha(0);
        animator.translationX(forward ? -dp(20) : dp(20));
        animator.setDuration(duration).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                removeView(outgoingView);
                if (outgoingTitleView == outgoingView) {
                    outgoingTitleView = null;
                }
            }
        }).start();
    }

    public void setRightDrawable(Drawable rightDrawable) {
        titleView.setRightDrawable(rightDrawable);
    }

    public void setTextColor(int color) {
        textColor = color;
        titleView.setTextColor(color);
        if (outgoingTitleView != null) {
            outgoingTitleView.setTextColor(color);
        }
    }

    public int getTextHeight() {
        return titleView.getTextHeight();
    }

    private SimpleTextView createTitleView(CharSequence title, Drawable rightDrawable) {
        SimpleTextView textView = new SimpleTextView(getContext());
        textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        textView.setTextColor(textColor);
        textView.setEllipsizeByGradient(true);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setPadding(0, dp(8), 0, dp(8));
        textView.setTextSize(!AndroidUtilities.isTablet() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 18 : 20);
        textView.setDrawablePadding(dp(4));
        textView.setRightDrawableTopPadding(-dp(1));
        textView.setText(title);
        textView.setRightDrawable(rightDrawable);
        textView.setContentDescription(title);
        textView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        textView.setFocusableInTouchMode(true);
        return textView;
    }

    private void clearOutgoingTitle() {
        if (outgoingTitleView == null) {
            return;
        }
        outgoingTitleView.animate().setListener(null);
        outgoingTitleView.animate().cancel();
        removeView(outgoingTitleView);
        outgoingTitleView = null;
    }
}
