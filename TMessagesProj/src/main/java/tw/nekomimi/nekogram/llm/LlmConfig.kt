package tw.nekomimi.nekogram.llm

import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.config.ConfigItem
import tw.nekomimi.nekogram.llm.preset.PresetRegistry
import tw.nekomimi.nekogram.llm.utils.UrlNormalizer
import tw.nekomimi.nekogram.translate.Translator
import xyz.nextalone.nagram.NaConfig

object LlmConfig {

    @JvmStatic
    fun getDefaultModelName(preset: Int): String {
        return getString(PresetRegistry.getDefaultModelResId(preset))
    }

    @JvmStatic
    fun getSavedModelName(preset: Int): String {
        val value = when (preset) {
            PresetRegistry.OPENAI -> NaConfig.llmProviderOpenAIModel.String()
            PresetRegistry.GEMINI -> NaConfig.llmProviderGeminiModel.String()
            PresetRegistry.GROQ -> NaConfig.llmProviderGroqModel.String()
            PresetRegistry.DEEPSEEK -> NaConfig.llmProviderDeepSeekModel.String()
            PresetRegistry.XAI -> NaConfig.llmProviderXAIModel.String()
            PresetRegistry.CEREBRAS -> NaConfig.llmProviderCerebrasModel.String()
            PresetRegistry.OLLAMA_CLOUD -> NaConfig.llmProviderOllamaCloudModel.String()
            PresetRegistry.OPENROUTER -> NaConfig.llmProviderOpenRouterModel.String()
            PresetRegistry.VERCEL_AI_GATEWAY -> NaConfig.llmProviderVercelAIGatewayModel.String()
            else -> NaConfig.llmModelName.String()
        }
        return value?.trim() ?: ""
    }

    @JvmStatic
    fun setSavedModelName(preset: Int, model: String?) {
        val value = model?.trim() ?: ""
        when (preset) {
            PresetRegistry.OPENAI -> NaConfig.llmProviderOpenAIModel.setConfigString(value)
            PresetRegistry.GEMINI -> NaConfig.llmProviderGeminiModel.setConfigString(value)
            PresetRegistry.GROQ -> NaConfig.llmProviderGroqModel.setConfigString(value)
            PresetRegistry.DEEPSEEK -> NaConfig.llmProviderDeepSeekModel.setConfigString(value)
            PresetRegistry.XAI -> NaConfig.llmProviderXAIModel.setConfigString(value)
            PresetRegistry.CEREBRAS -> NaConfig.llmProviderCerebrasModel.setConfigString(value)
            PresetRegistry.OLLAMA_CLOUD -> NaConfig.llmProviderOllamaCloudModel.setConfigString(value)
            PresetRegistry.OPENROUTER -> NaConfig.llmProviderOpenRouterModel.setConfigString(value)
            PresetRegistry.VERCEL_AI_GATEWAY -> NaConfig.llmProviderVercelAIGatewayModel.setConfigString(value)
            else -> NaConfig.llmModelName.setConfigString(value)
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
            val userUrl = NaConfig.llmApiUrl.String().trim()
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
        NaConfig.llmApiUrl.setConfigString(value)
    }

    @JvmStatic
    fun getApiKeyConfigItem(preset: Int): ConfigItem {
        return when (preset) {
            PresetRegistry.OPENAI -> NaConfig.llmProviderOpenAIKey
            PresetRegistry.GEMINI -> NaConfig.llmProviderGeminiKey
            PresetRegistry.GROQ -> NaConfig.llmProviderGroqKey
            PresetRegistry.DEEPSEEK -> NaConfig.llmProviderDeepSeekKey
            PresetRegistry.XAI -> NaConfig.llmProviderXAIKey
            PresetRegistry.CEREBRAS -> NaConfig.llmProviderCerebrasKey
            PresetRegistry.OLLAMA_CLOUD -> NaConfig.llmProviderOllamaCloudKey
            PresetRegistry.OPENROUTER -> NaConfig.llmProviderOpenRouterKey
            PresetRegistry.VERCEL_AI_GATEWAY -> NaConfig.llmProviderVercelAIGatewayKey
            else -> NaConfig.llmApiKey
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
        val llmProvider = NaConfig.llmProviderPreset.Int()
        val keyConfig = when (llmProvider) {
            PresetRegistry.OPENAI -> NaConfig.llmProviderOpenAIKey
            PresetRegistry.GEMINI -> NaConfig.llmProviderGeminiKey
            PresetRegistry.GROQ -> NaConfig.llmProviderGroqKey
            PresetRegistry.DEEPSEEK -> NaConfig.llmProviderDeepSeekKey
            PresetRegistry.XAI -> NaConfig.llmProviderXAIKey
            PresetRegistry.CEREBRAS -> NaConfig.llmProviderCerebrasKey
            PresetRegistry.OLLAMA_CLOUD -> NaConfig.llmProviderOllamaCloudKey
            PresetRegistry.OPENROUTER -> NaConfig.llmProviderOpenRouterKey
            PresetRegistry.VERCEL_AI_GATEWAY -> NaConfig.llmProviderVercelAIGatewayKey
            else -> NaConfig.llmApiKey
        }
        return keyConfig.String().isNotEmpty()
    }

    @JvmStatic
    fun llmIsDefaultProvider(): Boolean {
        return NekoConfig.translationProvider.Int() == Translator.providerLLMTranslator
    }
}
