package top.nkbe.niagram.helpers;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ChatLockManager {

    private static final String PREF_NAME = "nigram_chat_locks";
    private static final String KEY_LOCKED_CHATS_PREFIX = "locked_chats_";

    private static final Set<Long> unlockedDialogs = Collections.synchronizedSet(new HashSet<>());

    private static SharedPreferences getPreferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    private static String getKey(int currentAccount) {
        return KEY_LOCKED_CHATS_PREFIX + currentAccount;
    }

    public static boolean isChatLocked(int currentAccount, long dialogId) {
        if (dialogId == 0) {
            return false;
        }
        Set<String> set = getPreferences().getStringSet(getKey(currentAccount), null);
        if (set == null || set.isEmpty()) {
            return false;
        }
        return set.contains(String.valueOf(dialogId));
    }

    public static boolean isChatLocked(long dialogId) {
        return isChatLocked(UserConfig.selectedAccount, dialogId);
    }

    public static boolean isChatUnlockedInSession(long dialogId) {
        return unlockedDialogs.contains(dialogId);
    }

    public static void setChatLocked(int currentAccount, long dialogId, boolean locked) {
        if (dialogId == 0) {
            return;
        }
        String key = getKey(currentAccount);
        Set<String> existing = getPreferences().getStringSet(key, null);
        Set<String> updated = new HashSet<>();
        if (existing != null) {
            updated.addAll(existing);
        }
        if (locked) {
            updated.add(String.valueOf(dialogId));
        } else {
            updated.remove(String.valueOf(dialogId));
            unlockedDialogs.remove(dialogId);
        }
        getPreferences().edit().putStringSet(key, updated).apply();
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    public static void setChatLocked(long dialogId, boolean locked) {
        setChatLocked(UserConfig.selectedAccount, dialogId, locked);
    }

    public static void lockAll() {
        unlockedDialogs.clear();
    }

    public static boolean canAuthenticate(Context context) {
        try {
            BiometricManager biometricManager = BiometricManager.from(context);
            int authenticators;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
            } else {
                authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
            }
            int canAuth = biometricManager.canAuthenticate(authenticators);
            return canAuth == BiometricManager.BIOMETRIC_SUCCESS || AndroidUtilities.isKeyguardSecure();
        } catch (Exception e) {
            FileLog.e(e);
            return AndroidUtilities.isKeyguardSecure();
        }
    }

    public static void unlockChat(Activity activity, long dialogId, Utilities.Callback<Boolean> callback) {
        if (!isChatLocked(dialogId) || isChatUnlockedInSession(dialogId)) {
            if (callback != null) {
                callback.run(true);
            }
            return;
        }

        FragmentActivity fragmentActivity = null;
        if (activity instanceof FragmentActivity) {
            fragmentActivity = (FragmentActivity) activity;
        } else if (LaunchActivity.instance != null) {
            fragmentActivity = LaunchActivity.instance;
        }

        if (fragmentActivity == null) {
            if (callback != null) {
                callback.run(false);
            }
            return;
        }

        final FragmentActivity finalActivity = fragmentActivity;
        try {
            BiometricPrompt.PromptInfo.Builder promptInfoBuilder = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(LocaleController.getString(R.string.ChatLocked))
                    .setSubtitle(LocaleController.getString(R.string.ChatLockedDescription));

            int authenticators;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
            } else {
                authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
            }

            try {
                promptInfoBuilder.setAllowedAuthenticators(authenticators);
            } catch (Exception e) {
                promptInfoBuilder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG);
                promptInfoBuilder.setNegativeButtonText(LocaleController.getString(R.string.Cancel));
            }

            BiometricPrompt.PromptInfo promptInfo = promptInfoBuilder.build();

            BiometricPrompt biometricPrompt = new BiometricPrompt(finalActivity, ContextCompat.getMainExecutor(finalActivity), new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    unlockedDialogs.add(dialogId);
                    if (callback != null) {
                        callback.run(true);
                    }
                }

                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    FileLog.d("ChatLockManager onAuthenticationError: " + errorCode + " / " + errString);
                    if (callback != null) {
                        callback.run(false);
                    }
                }

                @Override
                public void onAuthenticationFailed() {
                    FileLog.d("ChatLockManager onAuthenticationFailed");
                }
            });

            biometricPrompt.authenticate(promptInfo);
        } catch (Exception e) {
            FileLog.e(e);
            if (callback != null) {
                callback.run(false);
            }
        }
    }
}
