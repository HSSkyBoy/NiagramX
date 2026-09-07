package top.nkbe.niagram;
import top.nkbe.niagram.config.NyaConfig;

public class DialogConfig {
    public static final String customForumTabPrefix = "customForumTabs_";

    public static String getCustomForumTabsKey(long dialogId) {
        return customForumTabPrefix + dialogId;
    }

    public static boolean isCustomForumTabsEnable(long dialogId) {
        return NyaConfig.getPreferences().getBoolean(getCustomForumTabsKey(dialogId), false);
    }

    public static boolean hasCustomForumTabsConfig(long dialogId) {
        return NyaConfig.getPreferences().contains(getCustomForumTabsKey(dialogId));
    }

    public static void setCustomForumTabsEnable(long dialogId, boolean enable) {
        NyaConfig.getPreferences().edit().putBoolean(getCustomForumTabsKey(dialogId), enable).apply();
    }

    public static void removeCustomForumTabsConfig(long dialogId) {
        NyaConfig.getPreferences().edit().remove(getCustomForumTabsKey(dialogId)).apply();
    }
}
