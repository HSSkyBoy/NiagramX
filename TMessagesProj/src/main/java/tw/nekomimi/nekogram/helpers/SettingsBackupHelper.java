package tw.nekomimi.nekogram.helpers;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.LinearLayout;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.radolyn.ayugram.messages.AyuSavePreferences;
import com.radolyn.ayugram.utils.AyuGhostPreferences;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.PushListenerController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import kotlin.text.StringsKt;
import tw.nekomimi.nekogram.DialogConfig;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.config.ConfigItem;
import tw.nekomimi.nekogram.utils.AlertUtil;
import tw.nekomimi.nekogram.utils.FileUtil;
import tw.nekomimi.nekogram.utils.GsonUtil;
import tw.nekomimi.nekogram.utils.ShareUtil;
import xyz.nextalone.nagram.NaConfig;
import xyz.nextalone.nagram.helper.BookmarksHelper;
import xyz.nextalone.nagram.helper.LocalPeerColorHelper;
import xyz.nextalone.nagram.helper.LocalPremiumStatusHelper;

public final class SettingsBackupHelper {
    public static String backupSettingsJson(boolean isCloud, int indentSpaces) throws JSONException {
        return backupSettingsJson(isCloud, indentSpaces, true);
    }

    public static String backupSettingsJson(boolean isCloud, int indentSpaces, boolean includeApiKeys) throws JSONException {

        JSONObject configJson = new JSONObject();

        ArrayList<String> userconfig = new ArrayList<>();
        userconfig.add("saveIncomingPhotos");
        userconfig.add("passcodeHash1");
        userconfig.add("passcodeHash");
        userconfig.add("passcodeType");
        userconfig.add("passcodeSalt");
        userconfig.add("passcodeRetryInMs");
        userconfig.add("autoLockIn");
        userconfig.add("useFingerprint");
        userconfig.add("allowScreenCapture");
        userconfig.add("proxyRotationEnabled");
        userconfig.add("proxyRotationTimeout");
        userconfig.add("storageCacheDir");
        spToJSON("userconfing", configJson, userconfig::contains, isCloud);

        ArrayList<String> mainconfigBlacklist = new ArrayList<>();
        mainconfigBlacklist.add("directShareHash");
        mainconfigBlacklist.add("directShareHash2");
        mainconfigBlacklist.add("cameraCache");
        mainconfigBlacklist.add("lastLogsCheckTime");
        mainconfigBlacklist.add("lastKeepMediaCheckTime");
        mainconfigBlacklist.add("floatingDebugActive");
        mainconfigBlacklist.add("appUpdateCheckTime");
        mainconfigBlacklist.add("appUpdate");
        mainconfigBlacklist.add("appUpdateBuild");

        spToJSON("mainconfig", configJson, key -> !mainconfigBlacklist.contains(key));
        if (!isCloud) {
            spToJSON("themeconfig", configJson, null);
            spToJSON("nekox_config", configJson, null);
            spToJSON("nekocloud", configJson, null);
        }
        spToJSON("nkmrcfg", configJson, null, includeApiKeys);

        return configJson.toString(indentSpaces);
    }

    private static void spToJSON(String sp, JSONObject object, Function<String, Boolean> filter) throws JSONException {
        spToJSON(sp, object, filter, true);
    }

    private static void spToJSON(String sp, JSONObject object, Function<String, Boolean> filter, boolean includeApiKeys) throws JSONException {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(sp, Activity.MODE_PRIVATE);
        JSONObject jsonConfig = new JSONObject();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            String key = entry.getKey();
            if ("nkmrcfg".equals(sp) && isDeviceSpecificPushKey(key)) {
                continue;
            }
            if (!includeApiKeys && (key.endsWith("Key") || key.contains("Token") || key.contains("AccountID"))) {
                continue;
            }
            if (filter != null && !filter.apply(key)) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof java.util.Set) {
                org.json.JSONArray array = new org.json.JSONArray();
                for (Object item : (java.util.Set<?>) value) {
                    array.put(item);
                }
                jsonConfig.put(key + "_string_set", array);
                continue;
            } else if (value instanceof Long) {
                key = key + "_long";
            } else if (value instanceof Float) {
                key = key + "_float";
            }
            jsonConfig.put(key, value);
        }
        if (jsonConfig.length() > 0) {
            object.put(sp, jsonConfig);
        }
    }

    public static void importSettings(Context context, File settingsFile) {
        AlertUtil.showConfirm(context,
                getString(R.string.ImportSettingsAlert),
                R.drawable.msg_photo_settings_solar,
                getString(R.string.Import),
                true,
                () -> importSettingsConfirmed(context, settingsFile));
    }

    public static void importSettingsConfirmed(Context context, File settingsFile) {
        try {
            JsonObject configJson = GsonUtil.toJsonObject(FileUtil.readUtf8String(settingsFile));
            importSettings(configJson);

            AlertDialog restart = new AlertDialog(context, 0);
            restart.setTitle(getString(R.string.NiagramX));
            restart.setMessage(getString(R.string.RestartAppToTakeEffect));
            restart.setPositiveButton(getString(R.string.OK), (__, ___) -> AppRestartHelper.triggerRebirth(context, new Intent(context, LaunchActivity.class)));
            restart.show();
        } catch (Exception e) {
            AlertUtil.showSimpleAlert(context, e);
        }
    }

    @SuppressLint("ApplySharedPref")
    public static void importSettings(JsonObject configJson) throws JSONException {
        Map<String, Integer> configTypes = new HashMap<>();
        try {
            configTypes.putAll(NekoConfig.getConfigTypes());
            configTypes.putAll(NaConfig.INSTANCE.getConfigTypes());
        } catch (Throwable ignore) {
        }
        String[] preservePrefixes = {
                AyuGhostPreferences.ghostReadExclusionPrefix,
                AyuGhostPreferences.ghostTypingExclusionPrefix,
                AyuSavePreferences.saveExclusionPrefix,
                LocalNameHelper.chatNameOverridePrefix,
                LocalNameHelper.userNameOverridePrefix,
                DialogConfig.customForumTabPrefix,
                LocalPeerColorHelper.KEY_PREFIX,
                LocalPremiumStatusHelper.KEY_PREFIX,
                BookmarksHelper.KEY_PREFIX
        };

        for (Map.Entry<String, JsonElement> element : configJson.entrySet()) {
            String spName = element.getKey();
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(spName, Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            for (Map.Entry<String, JsonElement> config : ((JsonObject) element.getValue()).entrySet()) {
                String key = config.getKey();
                if ("nkmrcfg".equals(spName) && isDeviceSpecificPushKey(key)) {
                    continue;
                }
                if (config.getValue().isJsonArray()) {
                    com.google.gson.JsonArray array = config.getValue().getAsJsonArray();
                    java.util.HashSet<String> set = new java.util.HashSet<>();
                    for (JsonElement item : array) {
                        if (item.isJsonPrimitive()) {
                            set.add(item.getAsString());
                        }
                    }
                    String actualKey = key;
                    if (key.endsWith("_string_set")) {
                        actualKey = StringsKt.substringBeforeLast(key, "_string_set", key);
                    }
                    editor.putStringSet(actualKey, set);
                    continue;
                }
                if (!config.getValue().isJsonPrimitive()) {
                    continue;
                }
                JsonPrimitive value = config.getValue().getAsJsonPrimitive();
                if ("nkmrcfg".equals(spName)) {
                    boolean shouldSkip = true;
                    for (String prefix : preservePrefixes) {
                        if (key.startsWith(prefix)) {
                            shouldSkip = false;
                            break;
                        }
                    }
                    if (shouldSkip) {
                        String actualKey = key;
                        if (key.endsWith("_long")) {
                            actualKey = StringsKt.substringBeforeLast(key, "_long", key);
                        } else if (key.endsWith("_float")) {
                            actualKey = StringsKt.substringBeforeLast(key, "_float", key);
                        }
                        Integer type = configTypes.get(actualKey);
                        shouldSkip = type == null || !isCompatibleConfigValue(key, value, type);
                    }
                    if (shouldSkip) {
                        continue;
                    }
                }
                if (value.isBoolean()) {
                    editor.putBoolean(key, value.getAsBoolean());
                } else if (value.isNumber()) {
                    boolean isLong = false;
                    boolean isFloat = false;
                    if (key.endsWith("_long")) {
                        key = StringsKt.substringBeforeLast(key, "_long", key);
                        isLong = true;
                    } else if (key.endsWith("_float")) {
                        key = StringsKt.substringBeforeLast(key, "_float", key);
                        isFloat = true;
                    }
                    if (isLong) {
                        editor.putLong(key, value.getAsLong());
                    } else if (isFloat) {
                        editor.putFloat(key, value.getAsFloat());
                    } else {
                        editor.putInt(key, value.getAsInt());
                    }
                } else {
                    editor.putString(key, value.getAsString());
                }
            }
            editor.commit();
        }
        PushListenerController.reconcilePushRegistration();
    }

    private static boolean isCompatibleConfigValue(String key, JsonPrimitive value, int type) {
        if (key.equals(NaConfig.INSTANCE.getPushServiceType().getKey())) {
            return value.isNumber() && value.getAsInt() >= 0 && value.getAsInt() <= 3;
        }
        if (type == ConfigItem.configTypeBool || type == ConfigItem.configTypeBoolLinkInt) {
            return value.isBoolean();
        }
        if (type == ConfigItem.configTypeInt) {
            return value.isNumber() && !key.endsWith("_long") && !key.endsWith("_float");
        }
        if (type == ConfigItem.configTypeLong) {
            return value.isNumber() && key.endsWith("_long");
        }
        if (type == ConfigItem.configTypeFloat) {
            return value.isNumber() && key.endsWith("_float");
        }
        return value.isString();
    }

    public static void backupSettings(Context context, Theme.ResourcesProvider resourceProvider) {
        if (context == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(R.string.BackupSettings));

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        CheckBoxCell checkBoxCell = new CheckBoxCell(context, CheckBoxCell.TYPE_CHECK_BOX_DEFAULT, resourceProvider);
        checkBoxCell.setBackground(Theme.getSelectorDrawable(false));
        checkBoxCell.setText(getString(R.string.ExportSettingsIncludeApiKeys), "", true, false);
        checkBoxCell.setPadding(LocaleController.isRTL ? dp(16) : dp(8), 0, LocaleController.isRTL ? dp(8) : dp(16), 0);
        checkBoxCell.setChecked(true, false);
        checkBoxCell.setOnClickListener(v -> {
            CheckBoxCell cell = (CheckBoxCell) v;
            cell.setChecked(!cell.isChecked(), true);
        });
        linearLayout.addView(checkBoxCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        builder.setView(linearLayout);
        builder.setPositiveButton(getString(R.string.ExportTheme), (dialog, which) -> {
            boolean includeApiKeys = checkBoxCell.isChecked();
            try {
                File cacheFile = new File(AndroidUtilities.getCacheDir(), new Date() + ".nekox-settings.json");
                FileUtil.writeUtf8String(SettingsBackupHelper.backupSettingsJson(false, 4, includeApiKeys), cacheFile);
                ShareUtil.shareFile(context, cacheFile);
            } catch (Exception e) {
                AlertUtil.showSimpleAlert(context, e);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private static boolean isDeviceSpecificPushKey(String key) {
        return key.equals(NaConfig.INSTANCE.getPushServiceTypeUnifiedSimple().getKey())
                || key.equals(NaConfig.INSTANCE.getPushServiceTypeUnifiedWebPushPrivateKey().getKey())
                || key.equals(NaConfig.INSTANCE.getPushServiceTypeUnifiedWebPushPublicKey().getKey())
                || key.equals(NaConfig.INSTANCE.getPushServiceTypeUnifiedWebPushAuthSecret().getKey());
    }
}
