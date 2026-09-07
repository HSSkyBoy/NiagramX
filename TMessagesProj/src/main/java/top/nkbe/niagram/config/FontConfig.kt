package top.nkbe.niagram.config

/**
 * Domain-specific configuration for fonts and typography in NiagramX.
 * Consolidates typeface toggles, font weight fallback, and custom font paths.
 */
object FontConfig {
    val typeface: ConfigItem
        get() = NyaConfig.typeface

    val forceFontWeightFallback: ConfigItem
        get() = NyaConfig.forceFontWeightFallback

    val customFontRegular: ConfigItem
        get() = NyaConfig.customFontRegular

    val customFontBold: ConfigItem
        get() = NyaConfig.customFontBold

    val customFontItalic: ConfigItem
        get() = NyaConfig.customFontItalic

    val customFontMono: ConfigItem
        get() = NyaConfig.customFontMono

    val inputFieldTextSize: ConfigItem
        get() = NyaConfig.inputFieldTextSize
}
