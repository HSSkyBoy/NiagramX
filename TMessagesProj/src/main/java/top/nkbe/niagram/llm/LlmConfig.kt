package top.nkbe.niagram.llm

import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import top.nkbe.niagram.config.ConfigItem
import top.nkbe.niagram.llm.net.GeminiNativeClient
import top.nkbe.niagram.llm.preset.PresetRegistry
import top.nkbe.niagram.llm.utils.UrlNormalizer
import top.nkbe.niagram.translate.Translator
import top.nkbe.niagram.config.NyaConfig

object LlmConfig {

    @JvmStatic
    fun isGeminiNative(preset: Int): Boolean {
        return preset == PresetRegistry.GOOGLE_AGENT_PLATFORM || preset == PresetRegistry.GOOGLE_AI_STUDIO
    }

    @JvmStatic
    fun getDefaultModelName(preset: Int): String {
        if (isGeminiNative(preset)) {
            return GeminiNativeClient.MODELS[0]
        }
        return getString(PresetRegistry.getDefaultModelResId(preset))
    }

    @JvmStatic
    fun getSavedModelName(preset: Int): String {
        val value = when (preset) {
            PresetRegistry.OPENAI -> NyaConfig.llmProviderOpenAIModel.String()
            PresetRegistry.GOOGLE_AI_STUDIO -> NyaConfig.llmProviderGeminiModel.String()
            PresetRegistry.GROQ -> NyaConfig.llmProviderGroqModel.String()
            PresetRegistry.DEEPSEEK -> NyaConfig.llmProviderDeepSeekModel.String()
            PresetRegistry.XAI -> NyaConfig.llmProviderXAIModel.String()
            PresetRegistry.CEREBRAS -> NyaConfig.llmProviderCerebrasModel.String()
            PresetRegistry.OLLAMA_CLOUD -> NyaConfig.llmProviderOllamaCloudModel.String()
            PresetRegistry.OPENROUTER -> NyaConfig.llmProviderOpenRouterModel.String()
            PresetRegistry.VERCEL_AI_GATEWAY -> NyaConfig.llmProviderVercelAIGatewayModel.String()
            PresetRegistry.GOOGLE_AGENT_PLATFORM -> NyaConfig.llmProviderVertexModel.String()
            else -> NyaConfig.llmModelName.String()
        }
        return value?.trim() ?: ""
    }

    @JvmStatic
    fun setSavedModelName(preset: Int, model: String?) {
        val value = model?.trim() ?: ""
        when (preset) {
            PresetRegistry.OPENAI -> NyaConfig.llmProviderOpenAIModel.setConfigString(value)
            PresetRegistry.GOOGLE_AI_STUDIO -> NyaConfig.llmProviderGeminiModel.setConfigString(value)
            PresetRegistry.GROQ -> NyaConfig.llmProviderGroqModel.setConfigString(value)
            PresetRegistry.DEEPSEEK -> NyaConfig.llmProviderDeepSeekModel.setConfigString(value)
            PresetRegistry.XAI -> NyaConfig.llmProviderXAIModel.setConfigString(value)
            PresetRegistry.CEREBRAS -> NyaConfig.llmProviderCerebrasModel.setConfigString(value)
            PresetRegistry.OLLAMA_CLOUD -> NyaConfig.llmProviderOllamaCloudModel.setConfigString(value)
            PresetRegistry.OPENROUTER -> NyaConfig.llmProviderOpenRouterModel.setConfigString(value)
            PresetRegistry.VERCEL_AI_GATEWAY -> NyaConfig.llmProviderVercelAIGatewayModel.setConfigString(value)
            PresetRegistry.GOOGLE_AGENT_PLATFORM -> NyaConfig.llmProviderVertexModel.setConfigString(value)
            else -> NyaConfig.llmModelName.setConfigString(value)
        }
    }

    @JvmStatic
    fun getEffectiveModelName(preset: Int): String {
        val saved = getSavedModelName(preset)
        return saved.ifBlank {
            getDefaultModelName(preset)
        }
    }

    @JvmStatic
    fun getEffectiveBaseUrl(preset: Int): String {
        return if (preset == PresetRegistry.CUSTOM) {
            val userUrl = NyaConfig.llmApiUrl.String().trim()
            userUrl.ifEmpty {
                getString(R.string.LlmApiUrlDefault)
            }
        } else {
            PresetRegistry.getPresetBaseUrl(preset).orEmpty()
        }
    }

    @JvmStatic
    fun setSavedCustomBaseUrl(baseUrl: String?) {
        val value = UrlNormalizer.normalizeBaseUrl(baseUrl)
        NyaConfig.llmApiUrl.setConfigString(value)
    }

    @JvmStatic
    fun getApiKeyConfigItem(preset: Int): ConfigItem {
        return when (preset) {
            PresetRegistry.OPENAI -> NyaConfig.llmProviderOpenAIKey
            PresetRegistry.GOOGLE_AI_STUDIO -> NyaConfig.llmProviderGeminiKey
            PresetRegistry.GROQ -> NyaConfig.llmProviderGroqKey
            PresetRegistry.DEEPSEEK -> NyaConfig.llmProviderDeepSeekKey
            PresetRegistry.XAI -> NyaConfig.llmProviderXAIKey
            PresetRegistry.CEREBRAS -> NyaConfig.llmProviderCerebrasKey
            PresetRegistry.OLLAMA_CLOUD -> NyaConfig.llmProviderOllamaCloudKey
            PresetRegistry.OPENROUTER -> NyaConfig.llmProviderOpenRouterKey
            PresetRegistry.VERCEL_AI_GATEWAY -> NyaConfig.llmProviderVercelAIGatewayKey
            PresetRegistry.GOOGLE_AGENT_PLATFORM -> NyaConfig.llmProviderVertexKey
            else -> NyaConfig.llmApiKey
        }
    }

    @JvmStatic
    fun getFirstApiKey(preset: Int): String? {
        val raw = getApiKeyConfigItem(preset).String()?.trim()
        if (raw.isNullOrBlank()) {
            return null
        }
        return raw.split(",")
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
    }

    @JvmStatic
    fun isLLMTranslatorAvailable(): Boolean {
        val llmProvider = NyaConfig.llmProviderPreset.Int()
        val keyConfig = when (llmProvider) {
            PresetRegistry.OPENAI -> NyaConfig.llmProviderOpenAIKey
            PresetRegistry.GOOGLE_AI_STUDIO -> NyaConfig.llmProviderGeminiKey
            PresetRegistry.GROQ -> NyaConfig.llmProviderGroqKey
            PresetRegistry.DEEPSEEK -> NyaConfig.llmProviderDeepSeekKey
            PresetRegistry.XAI -> NyaConfig.llmProviderXAIKey
            PresetRegistry.CEREBRAS -> NyaConfig.llmProviderCerebrasKey
            PresetRegistry.OLLAMA_CLOUD -> NyaConfig.llmProviderOllamaCloudKey
            PresetRegistry.OPENROUTER -> NyaConfig.llmProviderOpenRouterKey
            PresetRegistry.VERCEL_AI_GATEWAY -> NyaConfig.llmProviderVercelAIGatewayKey
            PresetRegistry.GOOGLE_AGENT_PLATFORM -> NyaConfig.llmProviderVertexKey
            else -> NyaConfig.llmApiKey
        }
        return keyConfig.String().isNotEmpty()
    }

    @JvmStatic
    fun llmIsDefaultProvider(): Boolean {
        return NyaConfig.translationProvider.Int() == Translator.providerLLMTranslator
    }
}
