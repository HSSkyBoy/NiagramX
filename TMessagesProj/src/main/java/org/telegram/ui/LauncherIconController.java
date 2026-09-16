package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;

public class LauncherIconController {
    public static void tryFixLauncherIconIfNeeded() {
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (isEnabled(icon)) {
                return;
            }
        }

        setIcon(LauncherIcon.DEFAULT);
    }

    public static boolean isEnabled(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        int i = ctx.getPackageManager().getComponentEnabledSetting(icon.getComponentName(ctx));
        return i == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || i == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == LauncherIcon.DEFAULT;
    }

    public static LauncherIcon getCurrentIcon() {
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (isEnabled(icon)) {
                return icon;
            }
        }
        return LauncherIcon.DEFAULT;
    }

    public static void setIcon(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        PackageManager pm = ctx.getPackageManager();
        pm.setComponentEnabledSetting(icon.getComponentName(ctx), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        for (LauncherIcon i : LauncherIcon.values()) {
            if (i != icon) {
                pm.setComponentEnabledSetting(i.getComponentName(ctx), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
        }
        if (LaunchActivity.instance != null) {
            LaunchActivity.instance.updateTaskDescription();
        }
    }

    public enum LauncherIcon {
        DEFAULT("DefaultIcon", R.mipmap.icon_9_launcher_background, R.mipmap.icon_9_launcher_foreground, R.string.AppIconNekoColorful, R.mipmap.icon_9_launcher),
        NEKO("NekoIcon", R.mipmap.icon_7_launcher_background, R.mipmap.icon_7_launcher_foreground, R.string.AppIconNeko, R.mipmap.icon_7_launcher),
        NEKO_DARK("NekoDarkIcon", R.mipmap.icon_2_background_sa, R.mipmap.icon_8_launcher_foreground, R.string.AppIconNekoDark, R.mipmap.icon_8_launcher),
        MUSHEEN("MusheenIcon", R.color.ic_launcher_background, R.mipmap.icon_10_launcher_foreground, R.string.AppIconMusheen, R.mipmap.icon_10_launcher),
        SPACE("SpaceIcon", R.mipmap.icon_11_launcher_background, R.mipmap.icon_11_launcher_foreground, R.string.AppIconSpace, R.mipmap.icon_11_launcher),
        CLOUD("CloudIcon", R.color.ic_launcher_background, R.mipmap.icon_12_launcher_foreground, R.string.AppIconCloud, R.mipmap.icon_12_launcher),
        NEON("NeonIcon", R.mipmap.icon_13_launcher_background, R.mipmap.icon_13_launcher_foreground, R.string.AppIconNeon, R.mipmap.icon_13_launcher),
        MATERIAL("MaterialIcon", R.mipmap.icon_14_launcher_background, R.mipmap.icon_14_launcher_foreground, R.string.AppIconMaterial, R.mipmap.icon_14_launcher),
        FORKGRAM("ForkGramIcon", R.mipmap.ic_launcher_fork, 0, R.string.AppIconForkGram, R.mipmap.ic_launcher_fork),
        TELEGRAM("TelegramIcon", R.drawable.icon_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconTelegramOriginal, R.mipmap.ic_launcher_dr),
        VINTAGE("VintageIcon", R.drawable.icon_6_background_sa, R.mipmap.icon_6_foreground_sa, R.string.AppIconVintage, R.mipmap.icon_6_launcher),
        AQUA("AquaIcon", R.drawable.icon_4_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconAqua, R.mipmap.icon_4_launcher),
        PREMIUM("PremiumIcon", R.drawable.icon_3_background_sa, R.mipmap.icon_3_foreground_sa, R.string.AppIconPremium, true, R.mipmap.icon_3_launcher),
        TURBO("TurboIcon", R.drawable.icon_5_background_sa, R.mipmap.icon_5_foreground_sa, R.string.AppIconTurbo, true, R.mipmap.icon_5_launcher),
        NOX("NoxIcon", R.mipmap.icon_2_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconNox, true, R.mipmap.icon_2_launcher);

        public final String key;
        public final int background;
        public final int foreground;
        public final int title;
        public final boolean premium;
        public final int iconRes;

        private ComponentName componentName;

        public ComponentName getComponentName(Context ctx) {
            if (componentName == null) {
                componentName = new ComponentName(ctx.getPackageName(), "org.telegram.messenger." + key);
            }
            return componentName;
        }

        LauncherIcon(String key, int background, int foreground, int title, int iconRes) {
            this(key, background, foreground, title, false, iconRes);
        }

        LauncherIcon(String key, int background, int foreground, int title, boolean premium, int iconRes) {
            this.key = key;
            this.background = background;
            this.foreground = foreground;
            this.title = title;
            this.premium = premium;
            this.iconRes = iconRes;
        }


    }
}
