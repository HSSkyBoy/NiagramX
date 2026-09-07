package org.telegram.messenger;

import android.content.SharedPreferences;
import android.os.SystemClock;

import org.telegram.tgnet.ConnectionsManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ProxyRotationController implements NotificationCenter.NotificationCenterDelegate {
    private final static ProxyRotationController INSTANCE = new ProxyRotationController();

    public final static int DEFAULT_TIMEOUT_INDEX = 1;
    public final static List<Integer> ROTATION_TIMEOUTS = Arrays.asList(
            5, 10, 15, 30, 60
    );

    private boolean isCurrentlyChecking;
    private Runnable checkProxyAndSwitchRunnable = () -> {
        isCurrentlyChecking = true;

        int currentAccount = UserConfig.selectedAccount;
        boolean startedCheck = false;
        for (int i = 0; i < SharedConfig.proxyList.size(); i++) {
            SharedConfig.ProxyInfo proxyInfo = SharedConfig.proxyList.get(i);
            if (proxyInfo.checking || SystemClock.elapsedRealtime() - proxyInfo.availableCheckTime < 2 * 60 * 1000) {
                continue;
            }
            startedCheck = true;
            proxyInfo.checking = true;
            proxyInfo.proxyCheckPingId = ConnectionsManager.getInstance(currentAccount).checkProxy(proxyInfo.address, proxyInfo.port, proxyInfo.username, proxyInfo.password, proxyInfo.secret, time -> AndroidUtilities.runOnUIThread(() -> {
                proxyInfo.availableCheckTime = SystemClock.elapsedRealtime();
                proxyInfo.checking = false;
                if (time == -1) {
                    proxyInfo.available = false;
                    proxyInfo.ping = 0;
                } else {
                    proxyInfo.ping = time;
                    proxyInfo.available = true;
                }
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyCheckDone, proxyInfo);
            }));
        }

        if (!startedCheck) {
            isCurrentlyChecking = false;
            switchToAvailable();
        }
    };

    public static void init() {
        INSTANCE.initInternal();
    }

    public static void checkAndAccelerate(boolean force) {
        INSTANCE.performCheck(force);
    }

    public static boolean switchToFastestProxy() {
        return INSTANCE.switchToFastest(true);
    }

    private void performCheck(boolean force) {
        int currentAccount = UserConfig.selectedAccount;
        long now = SystemClock.elapsedRealtime();
        isCurrentlyChecking = true;
        boolean startedCheck = false;

        for (int i = 0; i < SharedConfig.proxyList.size(); i++) {
            SharedConfig.ProxyInfo proxyInfo = SharedConfig.proxyList.get(i);
            if (proxyInfo.checking || (!force && now - proxyInfo.availableCheckTime < 30 * 1000L)) {
                continue;
            }
            startedCheck = true;
            proxyInfo.checking = true;
            proxyInfo.proxyCheckPingId = ConnectionsManager.getInstance(currentAccount).checkProxy(proxyInfo.address, proxyInfo.port, proxyInfo.username, proxyInfo.password, proxyInfo.secret, time -> AndroidUtilities.runOnUIThread(() -> {
                proxyInfo.availableCheckTime = SystemClock.elapsedRealtime();
                proxyInfo.checking = false;
                if (time == -1) {
                    proxyInfo.available = false;
                    proxyInfo.ping = 0;
                } else {
                    proxyInfo.ping = time;
                    proxyInfo.available = true;
                }
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyCheckDone, proxyInfo);
            }));
        }

        if (!startedCheck) {
            isCurrentlyChecking = false;
            onCheckFinished();
        }
    }

    private void onCheckFinished() {
        if (SharedConfig.proxyAutoSpeedAcceleration) {
            switchToFastest(false);
        } else if (SharedConfig.proxyRotationEnabled) {
            switchToAvailable();
        }
    }

    public boolean switchToFastest(boolean forceSwitch) {
        if (!SharedConfig.isProxyEnabled() || SharedConfig.proxyList.isEmpty()) {
            return false;
        }

        SharedConfig.ProxyInfo bestProxy = null;
        long minPing = Long.MAX_VALUE;

        for (SharedConfig.ProxyInfo info : SharedConfig.proxyList) {
            if (info.available && info.ping > 0) {
                if (info.ping < minPing) {
                    minPing = info.ping;
                    bestProxy = info;
                }
            }
        }

        if (bestProxy == null) {
            return false;
        }

        SharedConfig.ProxyInfo current = SharedConfig.currentProxy;
        boolean needSwitch = forceSwitch || (current == null) || (!current.available) || (current.ping <= 0);
        if (!needSwitch && current != bestProxy) {
            if (current.ping - bestProxy.ping >= 80) {
                needSwitch = true;
            }
        }

        if (needSwitch && bestProxy != current) {
            applyProxy(bestProxy);
            return true;
        }
        return false;
    }

    @SuppressWarnings("ComparatorCombinators")
    private void switchToAvailable() {
        isCurrentlyChecking = false;

        if (!SharedConfig.proxyRotationEnabled) {
            return;
        }

        List<SharedConfig.ProxyInfo> sortedList = new ArrayList<>(SharedConfig.proxyList);
        Collections.sort(sortedList, (o1, o2) -> Long.compare(o1.ping, o2.ping));
        for (SharedConfig.ProxyInfo info : sortedList) {
            if (info == SharedConfig.currentProxy || info.checking || !info.available) {
                continue;
            }
            applyProxy(info);
            break;
        }
    }

    private void applyProxy(SharedConfig.ProxyInfo info) {
        SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
        editor.putString("proxy_ip", info.address);
        editor.putString("proxy_pass", info.password);
        editor.putString("proxy_user", info.username);
        editor.putInt("proxy_port", info.port);
        editor.putString("proxy_secret", info.secret);
        editor.putBoolean("proxy_enabled", true);

        if (!info.secret.isEmpty()) {
            editor.putBoolean("proxy_enabled_calls", false);
        }
        editor.apply();

        SharedConfig.currentProxy = info;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyChangedByRotation);
        ConnectionsManager.setProxySettings(true, SharedConfig.currentProxy.address, SharedConfig.currentProxy.port, SharedConfig.currentProxy.username, SharedConfig.currentProxy.password, SharedConfig.currentProxy.secret);
    }

    private void initInternal() {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            NotificationCenter.getInstance(i).addObserver(this, NotificationCenter.didUpdateConnectionState);
        }
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxyCheckDone);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.proxyCheckDone) {
            if (!SharedConfig.isProxyEnabled() || SharedConfig.proxyList.size() <= 1) {
                return;
            }

            if (SharedConfig.proxyAutoSpeedAcceleration) {
                switchToFastest(false);
            } else if (SharedConfig.proxyRotationEnabled && isCurrentlyChecking) {
                switchToAvailable();
            }
        } else if (id == NotificationCenter.proxySettingsChanged) {
            AndroidUtilities.cancelRunOnUIThread(checkProxyAndSwitchRunnable);
        } else if (id == NotificationCenter.didUpdateConnectionState && account == UserConfig.selectedAccount) {
            if (!SharedConfig.isProxyEnabled() || SharedConfig.proxyList.size() <= 1) {
                return;
            }

            int state = ConnectionsManager.getInstance(account).getConnectionState();

            if (state == ConnectionsManager.ConnectionStateConnectingToProxy) {
                if (!isCurrentlyChecking) {
                    AndroidUtilities.runOnUIThread(checkProxyAndSwitchRunnable, ROTATION_TIMEOUTS.get(SharedConfig.proxyRotationTimeout) * 1000L);
                }
            } else if (state == ConnectionsManager.ConnectionStateConnected) {
                AndroidUtilities.cancelRunOnUIThread(checkProxyAndSwitchRunnable);
                if (SharedConfig.proxyAutoSpeedAcceleration) {
                    performCheck(false);
                }
            } else {
                AndroidUtilities.cancelRunOnUIThread(checkProxyAndSwitchRunnable);
            }
        }
    }
}
