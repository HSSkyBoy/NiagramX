package top.nkbe.niagram.helpers.remote;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

// Downloads an APK from an HTTPS URL (GitHub Release asset) into the
// app's private storage, verifies integrity and signatures, and reports progress.
public class ApkDownloader {

    public interface Callback {
        default void onProgress(float progress) {}
        void onSuccess(File file);
        default void onError(String message) {}
    }

    private static final int BUFFER_SIZE = 16384;
    private static final long PROGRESS_THROTTLE_MS = 100;
    private static final int CONNECT_TIMEOUT_S = 30;
    private static final int READ_TIMEOUT_S = 60;
    private static final int WRITE_TIMEOUT_S = 60;

    // 1. URL Domain validation
    private static final String[] GITHUB_RELEASE_PREFIXES = new String[] {
            "https://github.com/HSSkyBoy/NiagramX/releases/",
            "https://github.com/HSSkyBoy/Nigram/releases/"
    };

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static OkHttpClient client;

    private static OkHttpClient getClient() {
        if (client == null) {
            client = top.nkbe.niagram.utils.HttpClient.INSTANCE.getInstance().newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
                    .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
                    .writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS)
                    .build();
        }
        return client;
    }

    private static boolean isUrlAllowed(String url) {
        if (url == null) {
            return false;
        }
        for (String prefix : GITHUB_RELEASE_PREFIXES) {
            if (url.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static File getDestFile(String url) {
        File dir = ApplicationLoader.getFilesDirFixed("update");
        return new File(dir, Utilities.MD5(url) + ".apk");
    }

    /**
     * Clean up stale .tmp and older APK files in files/update/ directory
     */
    public static void cleanStaleFiles(File currentTarget) {
        try {
            File dir = ApplicationLoader.getFilesDirFixed("update");
            if (dir != null && dir.exists()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (currentTarget != null && f.getAbsolutePath().equals(currentTarget.getAbsolutePath())) {
                            continue;
                        }
                        // Delete stale temp files or older apks
                        f.delete();
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    /**
     * Verifies that the downloaded APK has the matching package name and is signed with the same
     * certificates as the currently running application.
     */
    public static boolean verifyApkSignature(Context context, File apkFile) {
        if (context == null || apkFile == null || !apkFile.exists()) {
            return false;
        }
        try {
            PackageManager pm = context.getPackageManager();
            String currentPackageName = context.getPackageName();
            PackageInfo currentPkg;
            PackageInfo archivePkg;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                currentPkg = pm.getPackageInfo(currentPackageName, PackageManager.GET_SIGNING_CERTIFICATES);
                archivePkg = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
            } else {
                currentPkg = pm.getPackageInfo(currentPackageName, PackageManager.GET_SIGNATURES);
                archivePkg = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), PackageManager.GET_SIGNATURES);
            }

            if (archivePkg == null) {
                FileLog.e("ApkDownloader: Unable to parse package archive info for " + apkFile.getAbsolutePath());
                return false;
            }

            if (!currentPackageName.equals(archivePkg.packageName)) {
                FileLog.e("ApkDownloader: Package name mismatch! Current: " + currentPackageName + ", Downloaded: " + archivePkg.packageName);
                return false;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (currentPkg.signingInfo == null || archivePkg.signingInfo == null) {
                    FileLog.e("ApkDownloader: signingInfo is null on API 28+");
                    return false;
                }
                Signature[] currentSigs = currentPkg.signingInfo.hasMultipleSigners()
                        ? currentPkg.signingInfo.getApkContentsSigners()
                        : currentPkg.signingInfo.getSigningCertificateHistory();
                Signature[] archiveSigs = archivePkg.signingInfo.hasMultipleSigners()
                        ? archivePkg.signingInfo.getApkContentsSigners()
                        : archivePkg.signingInfo.getSigningCertificateHistory();
                return matchSignatures(currentSigs, archiveSigs);
            } else {
                if (currentPkg.signatures == null || archivePkg.signatures == null) {
                    FileLog.e("ApkDownloader: signatures array is null");
                    return false;
                }
                return matchSignatures(currentPkg.signatures, archivePkg.signatures);
            }
        } catch (Exception e) {
            FileLog.e("ApkDownloader: Error verifying APK signature: " + e.getMessage(), e);
            return false;
        }
    }

    private static boolean matchSignatures(Signature[] sigs1, Signature[] sigs2) {
        if (sigs1 == null || sigs2 == null || sigs1.length == 0 || sigs2.length == 0) {
            return false;
        }
        HashSet<Signature> set1 = new HashSet<>(Arrays.asList(sigs1));
        HashSet<Signature> set2 = new HashSet<>(Arrays.asList(sigs2));
        return set1.equals(set2);
    }

    public static void download(String url, Callback callback) {
        if (!isUrlAllowed(url)) {
            AndroidUtilities.runOnUIThread(() -> callback.onError("Refusing download URL outside project releases"));
            return;
        }

        File dest = getDestFile(url);
        // If the finalized dest file already exists and passes signature verification, reuse it immediately.
        if (dest.exists() && verifyApkSignature(ApplicationLoader.applicationContext, dest)) {
            AndroidUtilities.runOnUIThread(() -> callback.onSuccess(dest));
            return;
        }

        File tmp = new File(dest.getPath() + ".tmp");
        executor.submit(() -> {
            // Clean up stale downloads before starting a fresh one
            cleanStaleFiles(dest);

            Response response = null;
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", "NiagramX")
                        .build();
                response = getClient().newCall(request).execute();
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    throw new RuntimeException("HTTP " + response.code());
                }
                long total = body.contentLength();
                try (InputStream in = body.byteStream();
                     FileOutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[BUFFER_SIZE];
                    long read = 0;
                    int n;
                    long lastReport = 0;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                        read += n;
                        long now = System.currentTimeMillis();
                        if (total > 0 && now - lastReport > PROGRESS_THROTTLE_MS) {
                            final float p = Math.min(1f, read / (float) total);
                            lastReport = now;
                            AndroidUtilities.runOnUIThread(() -> callback.onProgress(p));
                        }
                    }

                    // Content-Length integrity check
                    if (total > 0 && read != total) {
                        throw new RuntimeException("Incomplete download: received " + read + " of " + total + " bytes");
                    }
                }

                // Finalize atomically: dest only appears once the full file is written
                if (dest.exists()) {
                    dest.delete();
                }
                if (!tmp.renameTo(dest)) {
                    throw new RuntimeException("Failed to finalize download file");
                }

                // Security check: verify APK signature and package name against installed app
                if (!verifyApkSignature(ApplicationLoader.applicationContext, dest)) {
                    dest.delete();
                    throw new RuntimeException("Downloaded APK failed signature or package verification");
                }

                File done = dest;
                AndroidUtilities.runOnUIThread(() -> callback.onSuccess(done));
            } catch (Exception e) {
                FileLog.e(e);
                try {
                    tmp.delete();
                } catch (Exception ignored) {
                }
                String message = e.getMessage();
                AndroidUtilities.runOnUIThread(() -> callback.onError(message));
            } finally {
                if (response != null) response.close();
            }
        });
    }
}
