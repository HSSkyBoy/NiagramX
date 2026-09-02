package top.nkbe.niagram.helpers;

import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.ProfileActivity.sendLogs;

import android.app.Activity;
import android.net.Uri;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.ArrayList;
import java.util.Map;

import top.nkbe.niagram.settings.BaseNekoSettingsActivity;
import top.nkbe.niagram.settings.BaseNekoXSettingsActivity;
import top.nkbe.niagram.settings.NekoAboutActivity;
import top.nkbe.niagram.settings.NekoChatSettingsActivity;
import top.nkbe.niagram.settings.NekoEmojiSettingsActivity;
import top.nkbe.niagram.settings.NekoExperimentalSettingsActivity;
import top.nkbe.niagram.settings.NekoGeneralSettingsActivity;
import top.nkbe.niagram.settings.NekoPasscodeSettingsActivity;
import top.nkbe.niagram.settings.NekoSettingsActivity;
import top.nkbe.niagram.settings.NekoTranslatorSettingsActivity;
import top.nkbe.niagram.settings.SpyModeActivity;

public class SettingsHelper {

    public static void processDeepLink(Activity activity, Uri uri, Callback callback, Runnable unknown) {
        if (uri == null) {
            unknown.run();
            return;
        }
        var segments = uri.getPathSegments();
        if (segments == null || segments.size() < 2) {
            callback.presentFragment(new NekoSettingsActivity());
            return;
        }
        BaseNekoSettingsActivity neko_fragment = null;
        BaseNekoXSettingsActivity nekox_fragment = null;
        BaseFragment fragment;
        if ("settings".equals(segments.get(1)) || "s".equals(segments.get(1))) {
            fragment = new NekoSettingsActivity();
        } else if (PasscodeHelper.getSettingsKey().equals(segments.get(1))) {
            fragment = neko_fragment = new NekoPasscodeSettingsActivity();
        } else {
            switch (segments.get(1)) {
                case "about":
                    fragment = new NekoAboutActivity();
                    break;
                case "chat":
                case "chats":
                case "c":
                    fragment = nekox_fragment = new NekoChatSettingsActivity();
                    break;
                case "experimental":
                case "e":
                    fragment = nekox_fragment = new NekoExperimentalSettingsActivity();
                    break;
                case "spy":
                case "spymode":
                    fragment = nekox_fragment = new SpyModeActivity();
                    break;
                case "emoji":
                    fragment = neko_fragment = new NekoEmojiSettingsActivity();
                    break;
                case "general":
                case "g":
                    fragment = nekox_fragment = new NekoGeneralSettingsActivity();
                    break;
                case "translator":
                case "translate":
                case "t":
                    fragment = nekox_fragment = new NekoTranslatorSettingsActivity();
                    break;
                case "send_logs":
                    sendLogs(activity, false);
                    return;
                default:
                    unknown.run();
                    return;
            }
        }
        callback.presentFragment(fragment);
        var row = uri.getQueryParameter("r");
        if (TextUtils.isEmpty(row)) {
            row = uri.getQueryParameter("row");
        }
        var value = uri.getQueryParameter("v");
        if (TextUtils.isEmpty(value)) {
            value = uri.getQueryParameter("value");
        }
        if (!TextUtils.isEmpty(row)) {
            var rowFinal = row;
            if (neko_fragment != null) {
                BaseNekoSettingsActivity finalNeko_fragment = neko_fragment;
                AndroidUtilities.runOnUIThread(() -> finalNeko_fragment.scrollToRow(rowFinal, unknown));
            } else if (nekox_fragment != null) {
                BaseNekoXSettingsActivity finalNekoX_fragment = nekox_fragment;
                if (!TextUtils.isEmpty(value)) {
                    String finalValue = value;
                    AndroidUtilities.runOnUIThread(() -> finalNekoX_fragment.importToRow(rowFinal, finalValue, unknown));
                } else {
                    AndroidUtilities.runOnUIThread(() -> finalNekoX_fragment.scrollToRow(rowFinal, unknown));
                }
            }
        }
    }

    public interface Callback {
        void presentFragment(BaseFragment fragment);
    }

    public static ArrayList<SettingsSearchResult> onCreateSearchArray(Callback callback) {
        ArrayList<SettingsSearchResult> items = new ArrayList<>();
        ArrayList<BaseNekoXSettingsActivity> fragments = new ArrayList<>();
        fragments.add(new NekoGeneralSettingsActivity());
        fragments.add(new NekoChatSettingsActivity());
        fragments.add(new SpyModeActivity());
        fragments.add(new NekoExperimentalSettingsActivity());
        fragments.add(new NekoTranslatorSettingsActivity());

        String n_title = getString(R.string.NekoSettings);
        for (BaseNekoXSettingsActivity fragment: fragments) {
            int uid = fragment.getBaseGuid();
            int drawable = fragment.getDrawable();
            String f_title = fragment.getTitle();
            for (Map.Entry<Integer, String> entry : fragment.getRowMapReverse().entrySet()) {
                Integer i = entry.getKey();
                String key = entry.getValue();
                if (key.equals(String.valueOf(i))) {
                    continue;
                }
                int guid = uid + i;
                String title = getString(key);
                if (title == null || title.isEmpty()) {
                    continue;
                }
                Runnable open = () -> {
                    callback.presentFragment(fragment);
                    AndroidUtilities.runOnUIThread(() -> fragment.scrollToRow(key, null));
                };
                SettingsSearchResult result = new SettingsSearchResult(
                        guid, title, n_title, f_title, drawable, open
                );
                items.add(result);
            }
        }
        return items;
    }
}
