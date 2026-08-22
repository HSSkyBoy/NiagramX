package tw.nekomimi.nekogram.llm.net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import tw.nekomimi.nekogram.llm.utils.ModelUtil;
import xyz.nextalone.nagram.NaConfig;

public final class OpenAICompatClient {

    private OpenAICompatClient() {
    }

    public static LlmResponse<List<String>> fetchModels(String baseUrl, String apiKey) {
        LlmTransport.Credentials credentials = LlmTransport.prepareCredentials(baseUrl, apiKey);
        if (credentials.isInvalid()) {
            return LlmTransport.error(credentials.error());
        }
        LlmResponse<String> response = LlmTransport.execute(() -> new Request.Builder()
                .url(credentials.baseUrl() + "/models")
                .header("Authorization", "Bearer " + credentials.apiKey())
                .get()
                .build(), LlmTransport.HTTP_CLIENT);
        return LlmTransport.parseResponse(
                response,
                body -> {
                    List<String> models = parseModelIds(body);
                    return isGeminiModelsEndpoint(credentials.baseUrl()) ? ModelUtil.stripModelsPrefix(models) : models;
                },
                List::isEmpty,
                "No models found: "
        );
    }

    public static LlmResponse<String> testChatCompletions(String baseUrl, String apiKey, String model) {
        return LlmTransport.test(model, (modelName, messages) ->
                chatCompletions(baseUrl, apiKey, modelName, messages, NaConfig.INSTANCE.getLlmTemperature().Float(), LlmTransport.TEST_HTTP_CLIENT));
    }

    public static LlmResponse<String> chatCompletions(String baseUrl, String apiKey, String model, JSONArray messages) {
        return chatCompletions(baseUrl, apiKey, model, messages, NaConfig.INSTANCE.getLlmTemperature().Float(), LlmTransport.HTTP_CLIENT);
    }

    private static LlmResponse<String> chatCompletions(String baseUrl, String apiKey, String model, JSONArray messages, Float temperature, OkHttpClient client) {
        LlmTransport.Credentials credentials = LlmTransport.prepareCredentials(baseUrl, apiKey);
        if (credentials.isInvalid()) {
            return LlmTransport.error(credentials.error());
        }
        return LlmTransport.executeWithOptionalParameters(
                credentials.baseUrl(),
                model,
                withOptionalParameters -> chatCompletions(
                        credentials,
                        buildRequest(credentials.baseUrl(), model, messages, temperature, withOptionalParameters),
                        client
                )
        );
    }

    private static LlmResponse<String> chatCompletions(LlmTransport.Credentials credentials, String requestJson, OkHttpClient client) {
        LlmResponse<String> response = LlmTransport.execute(() -> new Request.Builder()
                .url(credentials.baseUrl() + "/chat/completions")
                .header("Authorization", "Bearer " + credentials.apiKey())
                .post(LlmTransport.jsonBody(requestJson))
                .build(), client);
        return LlmTransport.parseResponse(
                response,
                body -> {
                    String content = parseFirstMessageContent(body);
                    return content != null ? content.trim() : null;
                },
                content -> content == null || content.isEmpty(),
                "Empty content: "
        );
    }

    private static String buildRequest(String baseUrl, String model, JSONArray messages, Float temperature, boolean withOptionalParameters) throws Exception {
        JSONObject requestJson = new JSONObject()
                .put("model", model)
                .put("messages", messages);
        if (withOptionalParameters) {
            if (temperature != null && ModelUtil.supportsTemperature(model)) {
                requestJson.put("temperature", temperature);
            }
            ModelUtil.applyReasoningParameters(requestJson, baseUrl, model);
        }
        return requestJson.toString();
    }

    private static String parseFirstMessageContent(String body) {
        try {
            JSONObject json = new JSONObject(body);
            JSONArray choices = json.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                return null;
            }
            JSONObject first = choices.getJSONObject(0);
            JSONObject message = first.optJSONObject("message");
            if (message == null) {
                return null;
            }
            return message.optString("content", null);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static List<String> parseModelIds(String body) throws Exception {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String trimmed = body != null ? body.trim() : "";
        if (trimmed.isEmpty()) {
            return new ArrayList<>();
        }

        if (trimmed.startsWith("[")) {
            JSONArray array = new JSONArray(trimmed);
            extractModelIdsFromArray(array, out);
        } else {
            JSONObject json = new JSONObject(trimmed);
            if (json.has("data") && json.get("data") instanceof JSONArray) {
                extractModelIdsFromArray(json.getJSONArray("data"), out);
            } else if (json.has("models") && json.get("models") instanceof JSONArray) {
                extractModelIdsFromArray(json.getJSONArray("models"), out);
            } else if (json.has("data") && json.get("data") instanceof JSONObject) {
                JSONObject data = json.getJSONObject("data");
                if (data.has("id")) {
                    String id = data.optString("id", "").trim();
                    if (!id.isEmpty()) out.add(id);
                }
            }
        }

        return new ArrayList<>(out);
    }

    private static void extractModelIdsFromArray(JSONArray array, LinkedHashSet<String> out) {
        for (int i = 0; i < array.length(); i++) {
            Object item = array.opt(i);
            if (item instanceof JSONObject obj) {
                String id = obj.optString("id", "").trim();
                if (!id.isEmpty()) {
                    out.add(id);
                }
            } else if (item instanceof String s) {
                String id = s.trim();
                if (!id.isEmpty()) {
                    out.add(id);
                }
            }
        }
    }

    private static boolean isGeminiModelsEndpoint(String baseUrl) {
        return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("generativelanguage.googleapis.com");
    }
}
