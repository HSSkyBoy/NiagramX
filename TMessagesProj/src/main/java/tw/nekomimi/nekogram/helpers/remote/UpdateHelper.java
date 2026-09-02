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

    private static final Pattern APK_FILENAME_PATTERN = Pattern.compile(
            ".*?v?([0-9.]+)(?:-([a-f0-9]+))?(?:[.(]([0-9]+)[.)])?-([a-zA-Z0-9_.-]+)\\.apk",
            Pattern.CASE_INSENSITIVE
    );


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
        notifyDelegate(delegate, null, text);
    }

    @Override
    protected String getTag() {
        return NaConfig.INSTANCE.getAutoUpdateChannel().Int() == UPDATE_CHANNEL_RELEASE ? "updateRelease" : "updateBeta";
    }

    public void checkNewVersionAvailable(Delegate delegate) {
        checkNewVersionAvailable(delegate, false);
    }

    public void checkNewVersionAvailable(Delegate delegate, boolean updateAlways) {
        int channel = NaConfig.INSTANCE.getAutoUpdateChannel().Int();
        if (channel == UPDATE_OFF && !updateAlways) {
            notifyDelegate(delegate, null, null);
            return;
        }

        if (channel == UPDATE_CHANNEL_RELEASE) {
            checkGitHubReleaseUpdate(delegate, updateAlways);
        } else {
            checkTelegramBetaUpdate(delegate, updateAlways);
        }
    }

    private void checkGitHubReleaseUpdate(Delegate delegate, boolean updateAlways) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                OkHttpClient client = HttpClient.INSTANCE.getInstance();
                String body = fetchGitHubReleaseBody(client, GITHUB_RELEASE_API_URL);
                if (body == null) {
                    body = fetchGitHubReleaseBody(client, GITHUB_RELEASE_API_FALLBACK);
                }
                if (body == null) {
                    notifyDelegate(delegate, null, "GitHub API request failed");
                    return;
                }
                parseGitHubRelease(body, delegate, updateAlways);
            } catch (Exception e) {
                FileLog.e(e);
                notifyDelegate(delegate, null, e.getMessage());
            }
        });
    }

    private String fetchGitHubReleaseBody(OkHttpClient client, String url) {
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "NiagramX-Updater")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                FileLog.d("UpdateHelper: GitHub API " + url + " returned HTTP " + response.code());
                return null;
            }
            return response.body() != null ? response.body().string() : null;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private void parseGitHubRelease(String jsonStr, Delegate delegate, boolean updateAlways) {
        if (TextUtils.isEmpty(jsonStr)) {
            notifyDelegate(delegate, null, "Empty response from GitHub");
            return;
        }
        try {
            JSONObject json = new JSONObject(jsonStr);
            String tagName = json.optString("tag_name", "");
            String body = json.optString("body", "");
            JSONArray assets = json.optJSONArray("assets");

            boolean shouldUpdate = updateAlways || isNewerReleaseVersion(tagName);

            if (shouldUpdate && assets != null && assets.length() > 0) {
                Map<String, String> urlsByAbi = collectApkUrlsByAbi(assets);
                String chosenUrl = pickByAbiPreference(urlsByAbi);
                if (!TextUtils.isEmpty(chosenUrl)) {
                    TLRPC.TL_help_appUpdate update = new TLRPC.TL_help_appUpdate();
                    update.version = tagName;
                    update.text = body;
                    update.url = chosenUrl;
                    update.flags |= 4;
                    notifyDelegate(delegate, update, null);
                    return;
                }
            }
            notifyDelegate(delegate, null, null);
        } catch (Exception e) {
            FileLog.e(e);
            notifyDelegate(delegate, null, e.getMessage());
        }
    }

    public static String extractAbi(String fileName) {
        if (TextUtils.isEmpty(fileName)) return null;
        String lower = fileName.toLowerCase();
        if (lower.contains("arm64-v8a")) return "arm64-v8a";
        if (lower.contains("x86_64")) return "x86_64";
        if (lower.contains("universal")) return "universal";
        return null;
    }

    private Map<String, String> collectApkUrlsByAbi(JSONArray assets) throws JSONException {
        Map<String, String> urls = new HashMap<>();
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            String name = asset.optString("name", "");
            String downloadUrl = asset.optString("browser_download_url", "");
            if (!name.endsWith(".apk")) continue;

            String abi = extractAbi(name);
            if (abi != null) {
                urls.put(abi, downloadUrl);
            }
        }
        return urls;
    }

    private boolean isNewerReleaseVersion(String remoteTag) {
        if (TextUtils.isEmpty(remoteTag)) return false;

        String currentVersionString = BuildConfig.BUILD_VERSION_STRING;
        if (TextUtils.isEmpty(currentVersionString)) return true;

        String cleanRemote = stripLeadingV(remoteTag);
        String cleanCurrent = stripLeadingV(currentVersionString);

        if (cleanCurrent.equals(cleanRemote) || cleanCurrent.contains(cleanRemote)) {
            return false;
        }

        String[] remoteParts = cleanRemote.split("[.-]");
        String[] currentParts = cleanCurrent.split("[.-]");

        int length = Math.min(remoteParts.length, currentParts.length);
        for (int i = 0; i < length; i++) {
            String r = remoteParts[i];
            String c = currentParts[i];
            if (r.isEmpty() || c.isEmpty()) continue;

            Integer remoteNum = tryParseInt(r);
            Integer currentNum = tryParseInt(c);
            int cmp;
            if (remoteNum != null && currentNum != null) {
                cmp = Integer.compare(remoteNum, currentNum);
            } else {
                cmp = r.compareTo(c);
            }
            if (cmp != 0) return cmp > 0;
        }
        return remoteParts.length > currentParts.length;
    }

    private static String stripLeadingV(String s) {
        return (s.length() > 0 && (s.charAt(0) == 'v' || s.charAt(0) == 'V')) ? s.substring(1) : s;
    }

    private static Integer tryParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void checkTelegramBetaUpdate(Delegate delegate, boolean updateAlways) {
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = TELEGRAM_BETA_CHANNEL_USERNAME;
        getConnectionsManager().sendRequest(req, (response1, error1) -> {
            if (error1 != null) {
                notifyDelegate(delegate, null, error1.text);
                return;
            }
            if (!(response1 instanceof TLRPC.TL_contacts_resolvedPeer resolvedPeer) || resolvedPeer.chats == null || resolvedPeer.chats.isEmpty()) {
                notifyDelegate(delegate, null, "CHANNEL_NOT_FOUND");
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
                    notifyDelegate(delegate, null, error2.text);
                    return;
                }
                if (!(response2 instanceof TLRPC.messages_Messages res) || res.messages == null) {
                    notifyDelegate(delegate, null, null);
                    return;
                }
                parseChannelBetaMessages(res.messages, delegate, updateAlways);
            });
        });
    }

    private void parseChannelBetaMessages(ArrayList<TLRPC.Message> messages, Delegate delegate, boolean updateAlways) {
        if (messages == null || messages.isEmpty()) {
            notifyDelegate(delegate, null, null);
            return;
        }

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

        ApkReleaseGroup latestRelease = findLatestApkReleaseGroup(messages);
        if (latestRelease == null || latestRelease.abiDocuments.isEmpty()) {
            notifyDelegate(delegate, null, null);
            return;
        }

        boolean isNewer = updateAlways || isNewerBeta(latestRelease.commit, latestRelease.versionCode);
        if (!isNewer) {
            notifyDelegate(delegate, null, null);
            return;
        }

        TLRPC.Document chosenDocument = pickByAbiPreference(latestRelease.abiDocuments);
        if (chosenDocument == null) {
            notifyDelegate(delegate, null, null);
            return;
        }

        TLRPC.TL_help_appUpdate update = new TLRPC.TL_help_appUpdate();
        update.version = "v" + latestRelease.version + "-" + latestRelease.commit;
        update.text = latestRelease.changelog;
        update.entities = latestRelease.changelogEntities;
        update.document = chosenDocument;
        update.flags |= 2;
        notifyDelegate(delegate, update, null);
    }

    private ApkReleaseGroup findLatestApkReleaseGroup(ArrayList<TLRPC.Message> messages) {
        ApkReleaseGroup group = null;

        for (TLRPC.Message message : messages) {
            if (message.media != null && message.media.document != null) {
                String fileName = FileLoader.getDocumentFileName(message.media.document);
                if (fileName == null || !fileName.endsWith(".apk")) continue;

                Matcher matcher = APK_FILENAME_PATTERN.matcher(fileName);
                if (!matcher.matches()) continue;

                String version = matcher.group(1);
                String commit = matcher.group(2) != null ? matcher.group(2) : "";
                int versionCode = tryParseIntOrZero(matcher.group(3));
                String abi = extractAbi(fileName);
                if (abi == null && matcher.group(4) != null) {
                    abi = matcher.group(4).toLowerCase();
                }

                if (group == null) {
                    group = new ApkReleaseGroup(version, commit, versionCode, message.grouped_id);
                }

                boolean isSameRelease = (group.groupedId != 0 && message.grouped_id == group.groupedId)
                        || (!TextUtils.isEmpty(group.commit) && group.commit.equalsIgnoreCase(commit))
                        || (group.versionCode != 0 && group.versionCode == versionCode);
                if (!isSameRelease) continue;

                if (abi != null) {
                    group.abiDocuments.put(abi.toLowerCase(), message.media.document);
                }
                if (!TextUtils.isEmpty(message.message) && TextUtils.isEmpty(group.changelog)) {
                    group.changelog = message.message;
                    group.changelogEntities = message.entities;
                }
            } else if (group != null && group.groupedId != 0 && message.grouped_id == group.groupedId
                    && !TextUtils.isEmpty(message.message) && TextUtils.isEmpty(group.changelog)) {
                group.changelog = message.message;
                group.changelogEntities = message.entities;
            }
        }
        return group;
    }

    private static int tryParseIntOrZero(String s) {
        Integer v = tryParseInt(s);
        return v != null ? v : 0;
    }

    private static final class ApkReleaseGroup {
        final String version;
        final String commit;
        final int versionCode;
        final long groupedId;
        final Map<String, TLRPC.Document> abiDocuments = new HashMap<>();
        String changelog = "";
        ArrayList<TLRPC.MessageEntity> changelogEntities;

        ApkReleaseGroup(String version, String commit, int versionCode, long groupedId) {
            this.version = version;
            this.commit = commit;
            this.versionCode = versionCode;
            this.groupedId = groupedId;
        }
    }

    private boolean isNewerBeta(String remoteCommit, int remoteVersionCode) {
        int currentVersionCode = BuildConfig.VERSION_CODE;
        String currentVersionString = BuildConfig.BUILD_VERSION_STRING;

        if (remoteVersionCode > currentVersionCode) {
            return true;
        }
        if (remoteVersionCode == currentVersionCode && !TextUtils.isEmpty(remoteCommit)) {
            return currentVersionString == null || !currentVersionString.contains(remoteCommit);
        }
        return false;
    }

    private <T> T pickByAbiPreference(Map<String, T> map) {
        if (map == null || map.isEmpty()) return null;

        if (Build.SUPPORTED_ABIS != null) {
            for (String abi : Build.SUPPORTED_ABIS) {
                if (abi == null) continue;
                String lower = abi.toLowerCase();
                if ("arm64-v8a".equals(lower) && map.containsKey("arm64-v8a")) {
                    return map.get("arm64-v8a");
                }
                if ("x86_64".equals(lower) && map.containsKey("x86_64")) {
                    return map.get("x86_64");
                }
            }
        }

        // v7a or any other architecture downloads universal
        if (map.containsKey("universal")) {
            return map.get("universal");
        }

        return null;
    }

    private void notifyDelegate(Delegate delegate, TLRPC.TL_help_appUpdate update, String error) {
        if (delegate == null) return;
        AndroidUtilities.runOnUIThread(() -> delegate.onTLResponse(update, error));
    }

    private static final class InstanceHolder {
        private static final UpdateHelper instance = new UpdateHelper();
    }
}
