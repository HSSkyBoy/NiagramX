package tw.nekomimi.nekogram.llm.net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import tw.nekomimi.nekogram.llm.utils.ModelUtil;

public final class VertexGeminiClient {

    private VertexGeminiClient() {
    }

    public static final List<String> MODELS = List.of(
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite",
            "gemini-2.5-flash-lite",
            "gemini-3.7-flash",
            "gemini-3.6-flash",
            "gemini-3.5-flash",
            "gemini-2.5-flash"
    );

    public static LlmResponse<List<String>> getModels() {
        return new LlmResponse<>(MODELS, null, 0, 0);
    }

    public static LlmResponse<String> testGenerateContent(String baseUrl, String apiKey, String model) {
        return LlmTransport.test(model, (modelName, messages) ->
                generateContent(baseUrl, apiKey, modelName, messages, LlmTransport.TEST_HTTP_CLIENT));
    }

    public static LlmResponse<String> generateContent(String baseUrl, String apiKey, String model, JSONArray messages) {
        return generateContent(baseUrl, apiKey, model, messages, LlmTransport.HTTP_CLIENT);
    }

    private static LlmResponse<String> generateContent(String baseUrl, String apiKey, String model, JSONArray messages, OkHttpClient client) {
        LlmTransport.Credentials credentials = LlmTransport.prepareCredentials(baseUrl, apiKey);
        if (credentials.isInvalid()) {
            return LlmTransport.error(credentials.error());
        }
        try {
            return generateContent(credentials, model, buildRequest(credentials.baseUrl(), model, messages), client);
        } catch (Exception e) {
            return LlmTransport.error(e.toString());
        }
    }

    private static LlmResponse<String> generateContent(LlmTransport.Credentials credentials, String model, String requestJson, OkHttpClient client) {
        String modelPath = normalizeModelPath(model);
        String endpoint = credentials.baseUrl() + "/" + modelPath + ":generateContent";

        LlmResponse<String> response = LlmTransport.execute(() -> new Request.Builder()
                .url(endpoint)
                .header("x-goog-api-key", credentials.apiKey())
                .post(LlmTransport.jsonBody(requestJson))
                .build(), client);
        return LlmTransport.parseResponse(
                response,
                body -> {
                    String content = parseFirstCandidateContent(body);
                    return content != null ? content.trim() : null;
                },
                content -> content == null || content.isEmpty(),
                "Empty content: "
        );
    }

    private static String buildRequest(String baseUrl, String model, JSONArray messages) throws Exception {
        JSONArray systemParts = new JSONArray();
        JSONArray contents = new JSONArray();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) {
                continue;
            }
            String text = message.optString("content", "");
            if (text.isEmpty()) {
                continue;
            }
            String role = message.optString("role", "user");
            if ("system".equals(role)) {
                systemParts.put(new JSONObject().put("text", text));
                continue;
            }
            String contentRole = "assistant".equals(role) ? "model" : "user";
            JSONObject lastContent = contents.length() > 0 ? contents.optJSONObject(contents.length() - 1) : null;
            if (lastContent != null && contentRole.equals(lastContent.optString("role"))) {
                lastContent.getJSONArray("parts").put(new JSONObject().put("text", text));
            } else {
                contents.put(new JSONObject()
                        .put("role", contentRole)
                        .put("parts", new JSONArray().put(new JSONObject().put("text", text))));
            }
        }

        JSONObject requestJson = new JSONObject().put("contents", contents);
        if (systemParts.length() > 0) {
            requestJson.put("systemInstruction", new JSONObject().put("parts", systemParts));
        }
        ModelUtil.applyReasoningParameters(requestJson, baseUrl, model);
        return requestJson.toString();
    }

    private static String parseFirstCandidateContent(String body) {
        try {
            JSONObject json = new JSONObject(body);
            JSONArray candidates = json.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                return null;
            }
            JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
            if (content == null) {
                return null;
            }
            JSONArray parts = content.optJSONArray("parts");
            if (parts == null) {
                return null;
            }
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.optJSONObject(i);
                if (part == null || part.optBoolean("thought", false)) {
                    continue;
                }
                String value = part.optString("text", "");
                if (!value.isEmpty()) {
                    text.append(value);
                }
            }
            return text.toString();
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String normalizeModelPath(String model) {
        String modelName = model != null ? model.trim() : "";
        if (modelName.startsWith("publishers/")) {
            return modelName;
        }
        if (modelName.startsWith("models/")) {
            return "publishers/google/" + modelName;
        }
        if (modelName.startsWith("google/")) {
            return "publishers/google/models/" + modelName.substring("google/".length());
        }
        return "publishers/google/models/" + modelName;
    }

}
