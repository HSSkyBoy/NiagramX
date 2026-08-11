package tw.nekomimi.nekogram.translate.source.raw;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import tw.nekomimi.nekogram.utils.HttpClient;

public class BingTranslatorRaw {
    private static final String PREF_NAME = "bing_translator_config";
    private static final String DEFAULT_HOST = "www.bing.com";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36 Edg/151.0.4129.59";

    private final OkHttpClient httpClient = HttpClient.INSTANCE.getInstance();
    private final AtomicInteger count = new AtomicInteger();

    private String host = DEFAULT_HOST;
    private String ig;
    private String iid;
    private String key;
    private String token;
    private long tokenTs;
    private long tokenExpiryInterval;

    public String translate(String text, String from, String to) throws IOException {
        FileLog.d("Starting translation from " + from + " to " + to + ", text length: " + text.length());

        loadConfigFromPrefs();

        if (isTokenExpired()) {
            FileLog.d("Token expired, fetching new config");
            fetchConfig();
        }

        FileLog.d("performTranslation parameters - ig: " + ig + ", iid: " + iid + ", key: " + key + ", token: " + token);

        return performTranslation(from, to, text);
    }

    private void fetchConfig() throws IOException {
        FileLog.d("Fetching config from Bing translator");
        Request request = new Request.Builder()
            .url(getTranslatorUrl())
            .header("User-Agent", USER_AGENT)
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch Bing config: HTTP " + response.code() + ": " + body);
            }

            host = response.request().url().host();
            ig = extractValue(body, "IG:\\s*\"([^\"]+)\"");
            iid = extractValue(body, "data-iid=\"([^\"]+)\"");
            String params = extractValue(body, "params_AbusePreventionHelper\\s*=\\s*(\\[[^\\]]+\\])");
            JSONArray values = new JSONArray(params);
            key = String.valueOf(values.get(0));
            token = values.getString(1);
            tokenTs = Long.parseLong(key);
            tokenExpiryInterval = values.getLong(2);
            count.set(0);

            saveConfigToPrefs();
            FileLog.d("Config fetched successfully");
        } catch (JSONException | IllegalArgumentException e) {
            throw new IOException("Failed to parse Bing config", e);
        }
    }

    private static String extractValue(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("Failed to extract Bing config value");
    }

    private String performTranslation(String from, String to, String text) throws IOException {
        HttpUrl url = new HttpUrl.Builder()
            .scheme("https")
            .host(host)
            .addPathSegment("ttranslatev3")
            .addQueryParameter("isVertical", "1")
            .addQueryParameter("IG", ig)
            .addQueryParameter("IID", iid)
            .addQueryParameter("SFX", String.valueOf(count.incrementAndGet()))
            .addQueryParameter("ref", "TThis")
            .addQueryParameter("edgepdftranslator", "1")
            .build();

        FormBody.Builder formBody = createFormBody(from, to, text);
        Request request = createTranslationRequest(url, formBody.build());

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("Bing translation failed: HTTP " + response.code() + ": " + body);
            }

            String contentType = response.header("Content-Type");
            if (contentType != null && contentType.startsWith("application/json")) {
                return extractTranslatedText(body);
            }
            if (response.header("isgenderdebiasedtranslation") != null) {
                formBody.add("isGenderDebiasViewPresent", "true");
                return performGenderDebiasedTranslation(url, formBody.build());
            }
            throw new IOException("Unexpected Bing translation response: " + body);
        }
    }

    private FormBody.Builder createFormBody(String from, String to, String text) {
        return new FormBody.Builder()
            .add("fromLang", from)
            .add("to", to)
            .add("text", text)
            .add("token", token)
            .add("key", key)
            .add("tryFetchingGenderDebiasedTranslations", "true");
    }

    private Request createTranslationRequest(HttpUrl url, FormBody body) {
        return new Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", getTranslatorUrl())
            .post(body)
            .build();
    }

    private String performGenderDebiasedTranslation(HttpUrl url, FormBody body) throws IOException {
        try (Response response = httpClient.newCall(createTranslationRequest(url, body)).execute()) {
            String responseBody = response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("Bing gender-debiased translation failed: HTTP " + response.code() + ": " + responseBody);
            }
            try {
                return new JSONObject(responseBody).getString("masculineTranslation");
            } catch (JSONException e) {
                throw new IOException("Failed to parse Bing gender-debiased translation", e);
            }
        }
    }

    private String extractTranslatedText(String jsonResponse) throws IOException {
        try {
            JSONArray jsonArray = new JSONArray(jsonResponse);
            JSONObject firstObject = jsonArray.getJSONObject(0);
            JSONArray translations = firstObject.getJSONArray("translations");
            JSONObject translation = translations.getJSONObject(0);
            return translation.getString("text");
        } catch (JSONException e) {
            FileLog.e("Failed to parse translation response: ", e);
            throw new IOException("Failed to parse translation response", e);
        }
    }

    private boolean isTokenExpired() {
        return System.currentTimeMillis() - tokenTs > tokenExpiryInterval;
    }

    private String getTranslatorUrl() {
        return "https://" + host + "/translator";
    }

    private void loadConfigFromPrefs() throws IOException {
        SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        host = prefs.getString("host", DEFAULT_HOST);
        ig = prefs.getString("ig", null);
        iid = prefs.getString("iid", null);
        key =  prefs.getString("key", null);
        token = prefs.getString("token", null);
        tokenTs = prefs.getLong("tokenTs", 0);
        tokenExpiryInterval = prefs.getLong("tokenExpiryInterval", 0);
        if (ig == null) fetchConfig();
        FileLog.d("loadConfigFromPrefs, ig: " + ig + ", iid: " + iid + ", key: " + key + ", token: " + token + ", tokenTs: " + tokenTs + ", tokenExpiryInterval:" + tokenExpiryInterval);
    }

    private void saveConfigToPrefs() {
        SharedPreferences.Editor editor = ApplicationLoader.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
        editor.putString("host", host);
        editor.putString("ig", ig);
        editor.putString("iid", iid);
        editor.putString("key", key);
        editor.putString("token", token);
        editor.putLong("tokenTs", tokenTs);
        editor.putLong("tokenExpiryInterval", tokenExpiryInterval);
        editor.apply();
        FileLog.d("saveConfigToPrefs, ig: " + ig + ", iid: " + iid + ", key: " + key + ", token: " + token + ", tokenTs: " + tokenTs + ", tokenExpiryInterval:" + tokenExpiryInterval);
    }
}
