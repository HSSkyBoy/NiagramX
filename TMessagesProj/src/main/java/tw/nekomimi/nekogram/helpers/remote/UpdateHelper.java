package tw.nekomimi.nekogram.helpers.remote;

import android.os.Build;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import tw.nekomimi.nekogram.utils.HttpClient;
import xyz.nextalone.nagram.NaConfig;

public class UpdateHelper extends BaseRemoteHelper {

    public static final int UPDATE_OFF = 0;
    public static final int UPDATE_CHANNEL_RELEASE = 1;
    public static final int UPDATE_CHANNEL_BETA = 2;

    public static final String TELEGRAM_BETA_CHANNEL_USERNAME = "NiagramX";
    public static final String GITHUB_RELEASE_API_URL = "https://api.github.com/repos/HSSkyBoy/NiagramX/releases/latest";
    public static final String GITHUB_RELEASE_API_FALLBACK = "https://api.github.com/repos/HSSkyBoy/Nigram/releases/latest";

    private static final Pattern APK_FILENAME_PATTERN = Pattern.compile(".*v?([0-9.]+)-([a-f0-9]+)\\(([0-9]+)\\)-([a-zA-Z0-9_]+)\\.apk", Pattern.CASE_INSENSITIVE);

    private boolean updateAlways = false;

    public static UpdateHelper getInstance() {
        return InstanceHolder.instance;
    }

    public static void cleanAppUpdate() {
        if (SharedConfig.pendingAppUpdate != null && SharedConfig.pendingAppUpdate.document != null) {
            File path = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(SharedConfig.pendingAppUpdate.document, true);
            if (path != null && path.exists()) {
                Utilities.globalQueue.postRunnable(() -> {
                    try {
                        if (!path.delete()) path.deleteOnExit();
                    } catch (Exception ignored) {
                    }
                });
            }
        }
        ApkDownloader.cleanStaleFiles(null);
        SharedConfig.pendingAppUpdate = null;
        SharedConfig.saveConfig();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
    }

    @Override
    protected void onError(String text, Delegate delegate) {
        if (delegate != null) {
            AndroidUtilities.runOnUIThread(() -> delegate.onTLResponse(null, text));
        }
    }

    @Override
    protected String getTag() {
        return NaConfig.INSTANCE.getAutoUpdateChannel().Int() == UPDATE_CHANNEL_RELEASE ? "updateRelease" : "updateBeta";
    }

    public void checkNewVersionAvailable(Delegate delegate) {
        checkNewVersionAvailable(delegate, false);
    }

    public void checkNewVersionAvailable(Delegate delegate, boolean updateAlways) {
        this.updateAlways = updateAlways;
        int channel = NaConfig.INSTANCE.getAutoUpdateChannel().Int();
        if (channel == UPDATE_OFF && !updateAlways) {
            if (delegate != null) {
                AndroidUtilities.runOnUIThread(() -> delegate.onTLResponse(null, null));
            }
            return;
        }

        if (channel == UPDATE_CHANNEL_RELEASE) {
            checkGitHubReleaseUpdate(delegate);
        } else {
            checkTelegramBetaUpdate(delegate);
        }
    }

    private void checkGitHubReleaseUpdate(Delegate delegate) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                OkHttpClient client = HttpClient.INSTANCE.getInstance();
                Request request = new Request.Builder()
                        .url(GITHUB_RELEASE_API_URL)
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("User-Agent", "NiagramX-Updater")
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        Request fallbackReq = new Request.Builder()
                                .url(GITHUB_RELEASE_API_FALLBACK)
                                .header("Accept", "application/vnd.github.v3+json")
                                .header("User-Agent", "NiagramX-Updater")
                                .build();
                        try (Response fbResponse = client.newCall(fallbackReq).execute()) {
                            if (!fbResponse.isSuccessful()) {
                                AndroidUtilities.runOnUIThread(() -> delegate.onTLResponse(null, "GitHub API HTTP " + response.code()));
                                return;
                            }
                            String body = fbResponse.body() != null ? fbResponse.body().string() : null;
                            parseGitHubRelease(body, delegate);
                        }
                        return;
                    }
                    String body = response.body() != null ? response.body().string() : null;
                    parseGitHubRelease(body, delegate);
                }
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> delegate.onTLResponse(null, e.getMessage()));
            }
        });
    }

    private void parseGitHubRelease(String jsonStr, Delegate delegate) {
        if (TextUtils.isEmpty(jsonStr)) {
            AndroidUtilities.runOnUIThread(() -> delegate.onTLResponse(null, "Empty response from GitHub"));
            return;
        }
        try {
            JSONObject json = new JSONObject(jsonStr);
            String tagName = json.optString("tag_name", "");
            String body = json.optString("body", "");
            JSONArray assets = json.optJSONArray("assets");

            boolean shouldUpdate = updateAlways || isNewerReleaseVersion(tagName);
            if (updateAlways) updateAlways = false;

            if (shouldUpdate && assets != null && assets.length() > 0) {
                Map<String, String> urls = new HashMap<>();
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    String name = asset.optString("name", "");
                    String downloadUrl = asset.optString("browser_download_url", "");
                    if (name.endsWith(".apk")) {
                        if (name.contains("arm64-v8a")) {
                            urls.put("arm64-v8a", downloadUrl);
                        } else if (name.contains("armeabi-v7a")) {
                            urls.put("armeabi-v7a", downloadUrl);
                        } else if (name.contains("x86_64")) {
                            urls.put("x86_64", downloadUrl);
                        } else if (name.contains("universal")) {
                            urls.put("universal", downloadUrl);
                        }
                    }
                }
                String chosenUrl = getPreferredUpdateUrl(urls);
                if (!TextUtils.isEmpty(chosenUrl)) {
                    TLRPC.TL_help_appUpdate update = new TLRPC.TL_help_appUpdate();
                    update.version = tagName;
                    update.text = body;
                    update.url = chosenUrl;
                    update.flags |= 4;
                    AndroidUtilities.runOnUIThread(() -> delegate.onTLResponse(update, null));
                    return;
                }
            }
            AndroidUtilities.runOnUIThread(() -> delegate.onTLResponse(null, null));
        } catch (Exception e) {
            FileLog.e(e);
            AndroidUtilities.runOnUIThread(() -> delegate.onTLResponse(null, e.getMessage()));
        }
    }

    private boolean isNewerReleaseVersion(String remoteTag) {
        if (TextUtils.isEmpty(remoteTag)) return false;
        String cleanRemote = remoteTag.startsWith("v") ? remoteTag.substring(1) : remoteTag;
        String cleanCurrent = BuildConfig.BUILD_VERSION_STRING.startsWith("v") ? BuildConfig.BUILD_VERSION_STRING.substring(1) : BuildConfig.BUILD_VERSION_STRING;
        return cleanRemote.compareTo(cleanCurrent) > 0 || !cleanCurrent.contains(cleanRemote);
    }

    private void checkTelegramBetaUpdate(Delegate delegate) {
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = TELEGRAM_BETA_CHANNEL_USERNAME;
        getConnectionsManager().sendRequest(req, (response1, error1) -> {
            if (error1 != null) {
                AndroidUtilities.runOnUIThread(() -> delegate.onTLResponse(null, error1.text));
                return;
            }
            if (!(response1 instanceof TLRPC.TL_contacts_resolvedPeer resolvedPeer) || resolvedPeer.chats == null || resolvedPeer.chats.isEmpty()) {
                AndroidUtilities.runOnUIThread(() -> delegate.onTLResponse(null, "CHANNEL_NOT_FOUND"));
                return;
            }
            getMessagesController().putUsers(resolvedPeer.users, false);
            getMessagesController().putChats(resolvedPeer.chats, false);
            getMessagesStorage().putUsersAndChats(resolvedPeer.users, resolvedPeer.chats, false, true);

            TLRPC.Chat chat = resolvedPeer.chats.get(0);
            TLRPC.TL_inputPeerChannel inputPeer = new TLRPC.TL_inputPeerChannel();
            inputPeer.channel_id = chat.id;
            inputPeer.access_hash = chat.access_hash;

            TLRPC.TL_messages_getHistory reqHistory = new TLRPC.TL_messages_getHistory();
            reqHistory.peer = inputPeer;
            reqHistory.limit = 30;
            reqHistory.offset_id = 0;

            getConnectionsManager().sendRequest(reqHistory, (response2, error2) -> {
                if (error2 != null) {
                    AndroidUtilities.runOnUIThread(() -> delegate.onTLResponse(null, error2.text));
                    return;
                }
                if (!(response2 instanceof TLRPC.messages_Messages res) || res.messages == null) {
                    AndroidUtilities.runOnUIThread(() -> delegate.onTLResponse(null, null));
                    return;
                }
                parseChannelBetaMessages(res.messages, delegate);
            });
        });
    }

    private void parseChannelBetaMessages(ArrayList<TLRPC.Message> messages, Delegate delegate) {
        if (messages == null || messages.isEmpty()) {
            AndroidUtilities.runOnUIThread(() -> delegate.onTLResponse(null, null));
            return;
        }

        // 1. Check for metadata messages (#updateBeta / #updateRelease JSON)
        String tag = "#" + getTag();
        for (TLRPC.Message message : messages) {
            if (!TextUtils.isEmpty(message.message) && message.message.startsWith(tag)) {
                try {
                    JSONObject json = new JSONObject(message.message.substring(tag.length()).trim());
                    ArrayList<JSONObject> responses = new ArrayList<>();
                    responses.add(json);
                    onLoadSuccess(responses, delegate);
                    return;
                } catch (JSONException e) {
                    FileLog.e(e);
                }
            }
        }

        // 2. Parse direct APK attachments from channel history
        long targetGroupId = 0;
        String latestCommit = null;
        String latestVersion = null;
        int latestVersionCode = 0;
        String changelog = "";
        ArrayList<TLRPC.MessageEntity> changelogEntities = null;
        Map<String, TLRPC.Document> abiDocuments = new HashMap<>();

        for (TLRPC.Message message : messages) {
            if (message.media != null && message.media.document != null) {
                String fileName = FileLoader.getDocumentFileName(message.media.document);
                if (fileName != null && fileName.endsWith(".apk")) {
                    Matcher matcher = APK_FILENAME_PATTERN.matcher(fileName);
                    if (matcher.matches()) {
                        String version = matcher.group(1);
                        String commit = matcher.group(2);
                        int versionCode = 0;
                        try {
                            versionCode = Integer.parseInt(matcher.group(3));
                        } catch (Exception ignored) {
                        }
                        String abi = matcher.group(4);

                        if (latestCommit == null) {
                            latestCommit = commit;
                            latestVersion = version;
                            latestVersionCode = versionCode;
                            targetGroupId = message.grouped_id;
                        }

                        boolean isSameRelease = (targetGroupId != 0 && message.grouped_id == targetGroupId) || (latestCommit != null && latestCommit.equalsIgnoreCase(commit));
                        if (isSameRelease) {
                            if (abi != null) {
                                abiDocuments.put(abi.toLowerCase(), message.media.document);
                            }
                            if (!TextUtils.isEmpty(message.message) && TextUtils.isEmpty(changelog)) {
                                changelog = message.message;
                                changelogEntities = message.entities;
                            }
                        }
                    }
                }
            } else if (targetGroupId != 0 && message.grouped_id == targetGroupId && !TextUtils.isEmpty(message.message) && TextUtils.isEmpty(changelog)) {
                changelog = message.message;
                changelogEntities = message.entities;
            }
        }

        if (latestCommit != null && !abiDocuments.isEmpty()) {
            boolean isNewer = isNewerBeta(latestCommit, latestVersionCode);
            if (updateAlways) {
                isNewer = true;
                updateAlways = false;
            }

            if (isNewer) {
                TLRPC.Document chosenDocument = getPreferredDocument(abiDocuments);
                if (chosenDocument != null) {
                    TLRPC.TL_help_appUpdate update = new TLRPC.TL_help_appUpdate();
                    update.version = "v" + latestVersion + "-" + latestCommit;
                    update.text = changelog;
                    update.entities = changelogEntities;
                    update.document = chosenDocument;
                    update.flags |= 2;
                    AndroidUtilities.runOnUIThread(() -> delegate.onTLResponse(update, null));
                    return;
                }
            }
        }

        AndroidUtilities.runOnUIThread(() -> delegate.onTLResponse(null, null));
    }

    private boolean isNewerBeta(String remoteCommit, int remoteVersionCode) {
        int currentVersionCode = BuildConfig.VERSION_CODE;
        String currentVersionString = BuildConfig.BUILD_VERSION_STRING;
        if (remoteVersionCode > currentVersionCode) {
            return true;
        }
        if (remoteCommit != null && !TextUtils.isEmpty(remoteCommit)) {
            return !currentVersionString.contains(remoteCommit);
        }
        return false;
    }

    private TLRPC.Document getPreferredDocument(Map<String, TLRPC.Document> map) {
        for (String abi : Build.SUPPORTED_ABIS) {
            String lowerAbi = abi.toLowerCase();
            if (map.containsKey(lowerAbi)) {
                return map.get(lowerAbi);
            }
        }
        if (map.containsKey("arm64-v8a")) return map.get("arm64-v8a");
        if (map.containsKey("universal")) return map.get("universal");
        if (map.containsKey("armeabi-v7a")) return map.get("armeabi-v7a");
        if (map.containsKey("x86_64")) return map.get("x86_64");
        return map.values().isEmpty() ? null : map.values().iterator().next();
    }

    private String getPreferredUpdateUrl(Map<String, String> map) {
        for (String abi : Build.SUPPORTED_ABIS) {
            String lowerAbi = abi.toLowerCase();
            if (map.containsKey(lowerAbi)) {
                return map.get(lowerAbi);
            }
        }
        if (map.containsKey("arm64-v8a")) return map.get("arm64-v8a");
        if (map.containsKey("universal")) return map.get("universal");
        if (map.containsKey("armeabi-v7a")) return map.get("armeabi-v7a");
        if (map.containsKey("x86_64")) return map.get("x86_64");
        return map.values().isEmpty() ? null : map.values().iterator().next();
    }

    private static final class InstanceHolder {
        private static final UpdateHelper instance = new UpdateHelper();
    }
}
