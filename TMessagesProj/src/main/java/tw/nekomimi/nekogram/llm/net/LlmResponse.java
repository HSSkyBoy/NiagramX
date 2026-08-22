package tw.nekomimi.nekogram.llm.net;

public record LlmResponse<T>(T data, String error, long durationMs, int httpCode) {

    public boolean isSuccess() {
        return error == null;
    }
}
