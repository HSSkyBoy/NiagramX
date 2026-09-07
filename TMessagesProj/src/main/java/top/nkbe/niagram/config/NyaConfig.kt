package top.nkbe.niagram.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Pair
import androidx.core.content.edit
import com.radolyn.ayugram.utils.AyuGhostUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildVars
import org.telegram.messenger.FileLog
import org.telegram.messenger.SharedConfig
import top.nkbe.niagram.NekoXConfig
import top.nkbe.niagram.config.ConfigItem
import top.nkbe.niagram.config.ConfigItemKeyLinked
import top.nkbe.niagram.helpers.CloudSettingsHelper
import top.nkbe.niagram.llm.utils.UrlNormalizer
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.util.Arrays


object NyaConfig {
    @Volatile
    private var initialized = false

    @Volatile
    private var preferences: SharedPreferences? = null

    @JvmStatic
    fun getPreferences(): SharedPreferences {
        var p = preferences
        if (p == null) {
            p = ApplicationLoader.applicationContext.getSharedPreferences("nkmrcfg", Context.MODE_PRIVATE)
            preferences = p
        }
        return p
    }

    @JvmStatic
    fun init() {
        if (initialized) return
        synchronized(sync) {
            if (initialized) return
            if (ApplicationLoader.applicationContext == null) return

            loadConfig(false)
            updatePreferredTranslateTargetLangList()
            fixConfig()
            if (!BuildVars.LOGS_ENABLED) {
                showRPCError.setConfigBool(false)
            }
            initialized = true
        }
    }

    // --- Constants from NekoConfig ---
    const val TABLET_AUTO = 0
    const val TABLET_ENABLE = 1
    const val TABLET_DISABLE = 2
    const val TABLET_LANDSCAPE = 3

    const val DIALOG_FILTER_EXCLUDE_NONE = 0
    const val DIALOG_FILTER_EXCLUDE_MUTED = 1
    const val DIALOG_FILTER_EXCLUDE_ALL = 2

    const val MARKDOWN_PARSER_TELEGRAM = 0
    const val MARKDOWN_PARSER_NEKO = 1

    const val DRAWER_BACKGROUND_DEFAULT = 0
    const val DRAWER_BACKGROUND_AVATAR = 1
    const val DRAWER_BACKGROUND_BIG_AVATAR = 2
    const val DRAWER_BACKGROUND_WALLPAPER = 3

    const val DNS_TYPE_DEFAULT = 0
    const val DNS_TYPE_CLOUDFLARE = 1
    const val DNS_TYPE_GOOGLE = 2
    const val DNS_TYPE_TENCENT = 3
    const val DNS_TYPE_ALIDNS = 4
    const val DNS_TYPE_SYSTEM = 5
    const val DNS_TYPE_CUSTOM_DOH = 6

    const val WEB_PROXY_MODE_FOLLOW_TELEGRAM = 0
    const val WEB_PROXY_MODE_DIRECT = 1
    const val WEB_PROXY_MODE_CUSTOM = 2

    const val WEB_PROXY_TYPE_HTTP = 0
    const val WEB_PROXY_TYPE_SOCKS5 = 1

    const val ID_TYPE_HIDDEN = 0
    const val ID_TYPE_API = 1
    const val ID_TYPE_BOT_API = 2

    const val ENHANCED_LOADER_OFF = 0
    const val ENHANCED_LOADER_BALANCED = 1
    const val ENHANCED_LOADER_EXTREME = 2

    class DatacenterInfo(@JvmField var id: Int) {
        @JvmField var pingId: Long = 0
        @JvmField var ping: Long = 0
        @JvmField var checking: Boolean = false
        @JvmField var available: Boolean = false
        @JvmField var availableCheckTime: Long = 0
    }

    @JvmField
    val datacenterInfos = ArrayList<DatacenterInfo>(5)

    @JvmField
    val sync = Any()
    private var configLoaded = false
    private val configs = ArrayList<ConfigItem>()

    // Configs
    val showTextBold =
        addConfig(
            "TextBold",
            ConfigItem.configTypeBool,
            true
        )
    val showTextItalic =
        addConfig(
            "TextItalic",
            ConfigItem.configTypeBool,
            true
        )
    val showTextMono =
        addConfig(
            "TextMonospace",
            ConfigItem.configTypeBool,
            true
        )
    val showTextStrikethrough =
        addConfig(
            "TextStrikethrough",
            ConfigItem.configTypeBool,
            true
        )
    val showTextUnderline =
        addConfig(
            "TextUnderline",
            ConfigItem.configTypeBool,
            true
        )
    val showTextQuote =
        addConfig(
            "TextQuote",
            ConfigItem.configTypeBool,
            true
        )
    val showTextSpoiler =
        addConfig(
            "TextSpoiler",
            ConfigItem.configTypeBool,
            true
        )
    val showTextCreateLink =
        addConfig(
            "TextLink",
            ConfigItem.configTypeBool,
            true
        )
    val showTextCreateMention =
        addConfig(
            "TextCreateMention",
            ConfigItem.configTypeBool,
            true
        )
    val showTextCreateDate =
        addConfig(
            "TextCreateDate",
            ConfigItem.configTypeBool,
            true
        )
    val showTextRegular =
        addConfig(
            "TextRegular",
            ConfigItem.configTypeBool,
            true
        )
    val showTextTranslate =
        addConfig(
            "TextTranslate",
            ConfigItem.configTypeBool,
            true
        )
    val textStyleOrder =
        addConfig(
            "TextStyleOrder",
            ConfigItem.configTypeString,
            "translate,bold,italic,mono,code,strike,underline,quote,spoiler,link,mention,date,regular"
        )
    val combineMessage =
        addConfig(
            "CombineMessage",
            ConfigItem.configTypeInt,
            0
        )
    val noiseSuppressAndVoiceEnhance =
        addConfig(
            "NoiseSuppressAndVoiceEnhance",
            ConfigItem.configTypeBool,
            false
        )
    val showNoQuoteForward =
        addConfig(
            "NoQuoteForward",
            ConfigItem.configTypeBool,
            false
        )
    val showRepeatAsCopy =
        addConfig(
            "RepeatAsCopy",
            ConfigItem.configTypeBool,
            false
        )
    val showForceForward =
        addConfig(
            "ShowForceForward",
            ConfigItem.configTypeBool,
            true
        )
    val doubleTapAction =
        addConfig(
            "DoubleTapAction",
            ConfigItem.configTypeInt,
            3
        )
    val doubleTapActionOut =
        addConfig(
            "DoubleTapActionOut",
            ConfigItem.configTypeInt,
            8
        )
    val showCopyPhoto =
        addConfig(
            "CopyPhoto",
            ConfigItem.configTypeBool,
            false
        )
    val showReactions =
        addConfig(
            "Reactions",
            ConfigItem.configTypeBool,
            true
        )
    val customTitle =
        addConfig(
            "CustomTitle",
            ConfigItem.configTypeString,
            "Niagram"
        )
    val dateOfForwardedMsg =
        addConfig(
            "DateOfForwardedMsg",
            ConfigItem.configTypeBool,
            false
        )
    val showMessageID =
        addConfig(
            "ShowMessageID",
            ConfigItem.configTypeBool,
            false
        )
    val hideReadReceiptsLocally =
        addConfig(
            "HideReadReceiptsLocally",
            ConfigItem.configTypeBool,
            false
        )
    val showRPCError =
        addConfig(
            "ShowRPCError",
            ConfigItem.configTypeBool,
            false
        )
    val zalgoFilter =
        addConfig(
            "ZalgoFilter",
            ConfigItem.configTypeBool,
            false
        )
    val alwaysShowDownloadIcon =
        addConfig(
            "AlwaysShowDownloadIcon",
            ConfigItem.configTypeBool,
            false
        )
    val customEditedMessage =
        addConfig(
            "CustomEditedMessage",
            ConfigItem.configTypeString,
            ""
        )
    val disableProxyWhenVpnEnabled =
        addConfig(
            "DisableProxyWhenVpnEnabled",
            ConfigItem.configTypeBool,
            false
        )
    val notificationIcon =
        addConfig(
            "NotificationIcon",
            ConfigItem.configTypeInt,
            1
        )
    val showSetReminder =
        addConfig(
            "SetReminder",
            ConfigItem.configTypeBool,
            false
        )
    val showOnlineStatus =
        addConfig(
            "ShowOnlineStatus",
            ConfigItem.configTypeBool,
            false
        )
    val showFullAbout =
        addConfig(
            "ShowFullAbout",
            ConfigItem.configTypeBool,
            true
        )
    val typeMessageHintUseGroupName =
        addConfig(
            "TypeMessageHintUseGroupName",
            ConfigItem.configTypeBool,
            false
        )
    val showSendAsUnderMessageHint =
        addConfig(
            "ShowSendAsUnderMessageHint",
            ConfigItem.configTypeBool,
            false
        )
    val hideBotButtonInInputField =
        addConfig(
            "HideBotButtonInInputField",
            ConfigItem.configTypeBool,
            false
        )
    val chatDecoration =
        addConfig(
            "ChatDecoration",
            ConfigItem.configTypeInt,
            0
        )
    val doNotUnarchiveBySwipe =
        addConfig(
            "DoNotUnarchiveBySwipe",
            ConfigItem.configTypeBool,
            false
        )
    val defaultDeleteMenu =
        addConfig(
            "DefaultDeleteMenu",
            ConfigItem.configTypeInt,
            0
        )
    val defaultDeleteMenuBanUsers =
        addConfig(
            "DeleteBanUsers",
            defaultDeleteMenu,
            3,
            false
        )
    val defaultDeleteMenReportSpam =
        addConfig(
            "DeleteReportSpam",
            defaultDeleteMenu,
            2,
            false
        )
    val defaultDeleteMenuDeleteAll =
        addConfig(
            "DeleteAll",
            defaultDeleteMenu,
            1,
            false
        )
    val defaultDeleteMenuDoActionsInCommonGroups =
        addConfig(
            "DoActionsInCommonGroups",
            defaultDeleteMenu,
            0,
            false
        )
    val disableStories =
        addConfig(
            "DisableStories",
            ConfigItem.configTypeBool,
            false
        )
    val useLocalQuoteColorData =
        addConfig(
            "useLocalQuoteColorData",
            ConfigItem.configTypeString,
            ""
        )
    val useLocalEmojiStatusData =
        addConfig(
            "useLocalEmojiStatusData",
            ConfigItem.configTypeString,
            ""
        )
    val disableMarkdown =
        addConfig(
            "DisableMarkdown",
            ConfigItem.configTypeBool,
            false
        )
    val showSmallGIF =
        addConfig(
            "ShowSmallGIF",
            ConfigItem.configTypeBool,
            false
        )
    val disableClickCommandToSend =
        addConfig(
            "DisableClickCommandToSend",
            ConfigItem.configTypeBool,
            false
        )
    val disableDialogsFloatingButton =
        addConfig(
            "DisableDialogsFloatingButton",
            ConfigItem.configTypeBool,
            false
        )
    val centerActionBarTitle =
        addConfig(
            "CenterActionBarTitle",
            ConfigItem.configTypeBool,
            false
        )
    val hideStarsRating =
        addConfig(
            "HideStarsRating",
            ConfigItem.configTypeBool,
            false
        )
    val hideGiftButton =
        addConfig(
            "HideGiftButton",
            ConfigItem.configTypeBool,
            false
        )
    val scrollToSeenPhotoOnClose =
        addConfig(
            "ScrollToSeenPhotoOnClose",
            ConfigItem.configTypeBool,
            false
        )
    val showQuickReplyInBotCommands =
        addConfig(
            "ShowQuickReplyInBotCommands",
            ConfigItem.configTypeBool,
            false
        )
    val pushServiceType =
        addConfig(
            "PushServiceType",
            ConfigItem.configTypeInt,
            2
        )
    val pushServiceTypeInAppDialog =
        addConfig(
            "PushServiceTypeInAppDialog",
            ConfigItem.configTypeBool,
            false
        )
    val pushServiceTypeUnifiedGateway =
        addConfig(
            "PushServiceTypeUnifiedGateway",
            ConfigItem.configTypeString,
            ""
        )
    val pushServiceTypeUnifiedSimple =
        addConfig(
            "PushServiceTypeUnifiedSimple",
            ConfigItem.configTypeString,
            ""
        )
    val pushServiceTypeUnifiedWebPushPrivateKey =
        addConfig(
            "PushServiceTypeUnifiedWebPushPrivateKey",
            ConfigItem.configTypeString,
            ""
        )
    val pushServiceTypeUnifiedWebPushPublicKey =
        addConfig(
            "PushServiceTypeUnifiedWebPushPublicKey",
            ConfigItem.configTypeString,
            ""
        )
    val pushServiceTypeUnifiedWebPushAuthSecret =
        addConfig(
            "PushServiceTypeUnifiedWebPushAuthSecret",
            ConfigItem.configTypeString,
            ""
        )
    val sendMp4DocumentAsVideo =
        addConfig(
            "SendMp4DocumentAsVideo",
            ConfigItem.configTypeBool,
            true
        )
    val disableChannelMuteButton =
        addConfig(
            "DisableChannelMuteButton",
            ConfigItem.configTypeBool,
            false
        )
    val disablePreviewVideoSoundShortcut =
        addConfig(
            "DisablePreviewVideoSoundShortcut",
            ConfigItem.configTypeBool,
            true
        )
    val regexFiltersEnabled =
        addConfig(
            "RegexFilters",
            ConfigItem.configTypeBool,
            false
        )
    val regexFiltersData =
        addConfig(
            "RegexFiltersData",
            ConfigItem.configTypeString,
            "[]"
        )
    val regexFiltersEnableInChats =
        addConfig(
            "RegexFiltersEnableInChats",
            ConfigItem.configTypeBool,
            false
        )
    val regexChatFiltersData =
        addConfig(
            "RegexChatFiltersData",
            ConfigItem.configTypeString,
            "[]"
        )
    val regexFiltersExcludedDialogs =
        addConfig(
            "RegexFiltersExcludedDialogs",
            ConfigItem.configTypeString,
            "[]"
        )
    val blockedChannelsData =
        addConfig(
            "BlockedChannelsData",
            ConfigItem.configTypeString,
            "[]"
        )
    val customFilteredUsersData =
        addConfig(
            "CustomFilteredUsersData",
            ConfigItem.configTypeString,
            "[]"
        )
    val showTimeHint =
        addConfig(
            "ShowTimeHint",
            ConfigItem.configTypeBool,
            false
        )
    val searchHashtagDefaultPageChannel =
        addConfig(
            "SearchHashtagDefaultPageChannel",
            ConfigItem.configTypeInt,
            0
        )
    val searchHashtagDefaultPageChat =
        addConfig(
            "SearchHashtagDefaultPageChat",
            ConfigItem.configTypeInt,
            0
        )
    val enablePanguOnSending =
        addConfig(
            "EnablePanguOnSending",
            ConfigItem.configTypeBool,
            false
        )
    val defaultHlsVideoQuality =
        addConfig(
            "DefaultHlsVideoQuality",
            ConfigItem.configTypeInt,
            0
        )
    val disableBotOpenButton =
        addConfig(
            "DisableBotOpenButton",
            ConfigItem.configTypeBool,
            false
        )
    val customTitleUserName =
        addConfig(
            "CustomTitleUserName",
            ConfigItem.configTypeBool,
            false
        )
    val enhancedVideoBitrate =
        addConfig(
            "EnhancedVideoBitrate",
            ConfigItem.configTypeBool,
            false
        )
    val ActionBarButtonReply =
        addConfig(
            "Reply",
            ConfigItem.configTypeBool,
            false
        )
    val ActionBarButtonEdit =
        addConfig(
            "Edit",
            ConfigItem.configTypeBool,
            true
        )
    val ActionBarButtonSelectBetween =
        addConfig(
            "SelectBetween",
            ConfigItem.configTypeBool,
            true
        )
    val ActionBarButtonCopy =
        addConfig(
            "Copy",
            ConfigItem.configTypeBool,
            true
        )
    val ActionBarButtonForward =
        addConfig(
            "Forward",
            ConfigItem.configTypeBool,
            true
        )
    val playerDecoder =
        addConfig(
            "VideoPlayerDecoder",
            ConfigItem.configTypeInt,
            1
        )

    val customFontRegular =
        addConfig(
            "customFontRegular",
            ConfigItem.configTypeString,
            ""
        )
    val customFontBold =
        addConfig(
            "customFontBold",
            ConfigItem.configTypeString,
            ""
        )
    val customFontItalic =
        addConfig(
            "customFontItalic",
            ConfigItem.configTypeString,
            ""
        )
    val customFontMono =
        addConfig(
            "customFontMono",
            ConfigItem.configTypeString,
            ""
        )
    val inputFieldTextSize =
        addConfig(
            "inputFieldTextSize",
            ConfigItem.configTypeInt,
            0
        )

    // --- Merged from NekoConfig ---
    @JvmField val unreadBadgeOnBackButton = addConfig("unreadBadgeOnBackButton", ConfigItem.configTypeBool, false)
    @JvmField val useCustomEmoji = addConfig("useCustomEmoji", ConfigItem.configTypeBool, false)
    @JvmField val repeatConfirm = addConfig("repeatConfirm", ConfigItem.configTypeBool, true)
    @JvmField val disableInstantCamera = addConfig("DisableInstantCamera", ConfigItem.configTypeBool, true)
    @JvmField val showSeconds = addConfig("showSeconds", ConfigItem.configTypeBool, false)
    @JvmField val useIosSounds = addConfig("useIosSounds", ConfigItem.configTypeBool, false)

    @JvmField val useIPv6 = addConfig("IPv6", ConfigItem.configTypeBool, false)
    @JvmField val hidePhone = addConfig("HidePhone", ConfigItem.configTypeBool, true)
    @JvmField val ignoreBlocked = addConfig("IgnoreBlocked", ConfigItem.configTypeBool, false)
    @JvmField val tabletMode = addConfig("TabletMode", ConfigItem.configTypeInt, 0)

    @JvmField val typeface = addConfig("TypefaceUseDefault", ConfigItem.configTypeBool, false)
    @JvmField val forceFontWeightFallback = addConfig("forceFontWeightFallback", ConfigItem.configTypeBool, false)
    @JvmField val nameOrder = addConfig("NameOrder", ConfigItem.configTypeInt, 1)
    @JvmField val mapPreviewProvider = addConfig("MapPreviewProvider", ConfigItem.configTypeInt, 0)
    @JvmField val showAddToSavedMessages = addConfig("showAddToSavedMessages", ConfigItem.configTypeBool, true)
    @JvmField val showReport = addConfig("showReport", ConfigItem.configTypeBool, false)
    @JvmField val showViewHistory = addConfig("showViewHistory", ConfigItem.configTypeBool, true)
    @JvmField val showAdminActions = addConfig("showAdminActions", ConfigItem.configTypeBool, true)
    @JvmField val showChangePermissions = addConfig("showChangePermissions", ConfigItem.configTypeBool, true)
    @JvmField val showDeleteDownloadedFile = addConfig("showDeleteDownloadedFile", ConfigItem.configTypeBool, true)
    @JvmField val showMessageDetails = addConfig("showMessageDetails", ConfigItem.configTypeBool, true)
    @JvmField val showTranslate = addConfig("showTranslate", ConfigItem.configTypeBool, true)
    @JvmField val showRepeat = addConfig("showRepeat", ConfigItem.configTypeBool, true)
    @JvmField val showShareMessages = addConfig("showShareMessages", ConfigItem.configTypeBool, false)
    @JvmField val showMessageHide = addConfig("showMessageHide", ConfigItem.configTypeBool, false)

    @JvmField val actionBarDecoration = addConfig("ActionBarDecoration", ConfigItem.configTypeInt, 0)
    @JvmField val stickerSize = addConfig("stickerSize", ConfigItem.configTypeFloat, 14.0f)
    @JvmField val unlimitedFavedStickers = addConfig("UnlimitedFavoredStickers", ConfigItem.configTypeBool, false)
    @JvmField val unlimitedPinnedDialogs = addConfig("UnlimitedPinnedDialogs", ConfigItem.configTypeBool, false)
    @JvmField val openArchiveOnPull = addConfig("OpenArchiveOnPull", ConfigItem.configTypeBool, false)
    @JvmField val hideKeyboardOnChatScroll = addConfig("HideKeyboardOnChatScroll", ConfigItem.configTypeBool, false)
    @JvmField val useSystemEmoji = addConfig("EmojiUseDefault", ConfigItem.configTypeBool, false)
    @JvmField val rearVideoMessages = addConfig("RearVideoMessages", ConfigItem.configTypeBool, false)
    @JvmField val hideAllTab = addConfig("HideAllTab", ConfigItem.configTypeBool, false)

    @JvmField val sortByUnmuted = addConfig("sort_by_unmuted", ConfigItem.configTypeBool, true)
    @JvmField val sortByUser = addConfig("sort_by_user", ConfigItem.configTypeBool, true)
    @JvmField val sortByContacts = addConfig("sort_by_contacts", ConfigItem.configTypeBool, true)

    @JvmField val disableSystemAccount = addConfig("DisableSystemAccount", ConfigItem.configTypeBool, false)
    @JvmField val skipOpenLinkConfirm = addConfig("SkipOpenLinkConfirm", ConfigItem.configTypeBool, false)

    @JvmField val showIdAndDc = addConfig("ShowIdAndDc", ConfigItem.configTypeBool, true)

    @JvmField val cachePath = addConfig("cache_path", ConfigItem.configTypeString, "")
    @JvmField val customSavePath = addConfig("customSavePath", ConfigItem.configTypeString, "Niagram")

    @JvmField val translationProvider = addConfig("translationProvider", ConfigItem.configTypeInt, 1)
    @JvmField val translateToLang = addConfig("TransToLang", ConfigItem.configTypeString, "")
    @JvmField val translateInputLang = addConfig("TransInputToLang", ConfigItem.configTypeString, "en")
    @JvmField val googleCloudTranslateKey = addConfig("GoogleCloudTransKey", ConfigItem.configTypeString, "")

    @JvmField val disableNotificationBubbles = addConfig("disableNotificationBubbles", ConfigItem.configTypeBool, false)

    @JvmField val tabsTitleType = addConfig("TabTitleType", ConfigItem.configTypeInt, NekoXConfig.TITLE_TYPE_TEXT)
    @JvmField val confirmAVMessage = addConfig("ConfirmAVMessage", ConfigItem.configTypeBool, false)
    @JvmField val askBeforeCall = addConfig("AskBeforeCalling", ConfigItem.configTypeBool, true)
    @JvmField val disableNumberRounding = addConfig("DisableNumberRounding", ConfigItem.configTypeBool, false)

    @JvmField val dnsType = addConfig("DnsType", ConfigItem.configTypeInt, DNS_TYPE_DEFAULT)
    @JvmField val customDoH = addConfig("CustomDoH", ConfigItem.configTypeString, "")

    @JvmField val webProxyMode = addConfig("WebProxyMode", ConfigItem.configTypeInt, WEB_PROXY_MODE_FOLLOW_TELEGRAM)
    @JvmField val webProxyType = addConfig("WebProxyType", ConfigItem.configTypeInt, WEB_PROXY_TYPE_HTTP)
    @JvmField val webProxyHost = addConfig("WebProxyHost", ConfigItem.configTypeString, "")
    @JvmField val webProxyPort = addConfig("WebProxyPort", ConfigItem.configTypeString, "")
    @JvmField val webProxyUsername = addConfig("WebProxyUsername", ConfigItem.configTypeString, "")
    @JvmField val webProxyPassword = addConfig("WebProxyPassword", ConfigItem.configTypeString, "")

    @JvmField val mediaPreview = addConfig("MediaPreview", ConfigItem.configTypeBool, true)

    @JvmField val disableVibration = addConfig("DisableVibration", ConfigItem.configTypeBool, false)
    @JvmField val autoPauseVideo = addConfig("AutoPauseVideo", ConfigItem.configTypeBool, false)
    @JvmField val disableProximityEvents = addConfig("DisableProximityEvents", ConfigItem.configTypeBool, false)

    @JvmField val ignoreContentRestrictions = addConfig("ignoreContentRestrictions", ConfigItem.configTypeBool, true)
    @JvmField val useChatAttachMediaMenu = addConfig("UseChatAttachEnterMenu", ConfigItem.configTypeBool, true)
    @JvmField val disableLinkPreviewByDefault = addConfig("DisableLinkPreviewByDefault", ConfigItem.configTypeBool, false)
    @JvmField val sendCommentAfterForward = addConfig("SendCommentAfterForward", ConfigItem.configTypeBool, true)
    @JvmField val disableTrending = addConfig("DisableTrending", ConfigItem.configTypeBool, true)
    @JvmField val dontSendGreetingSticker = addConfig("DontSendGreetingSticker", ConfigItem.configTypeBool, true)
    @JvmField val hideTimeForSticker = addConfig("HideTimeForSticker", ConfigItem.configTypeBool, false)
    @JvmField val takeGIFasVideo = addConfig("TakeGIFasVideo", ConfigItem.configTypeBool, false)
    @JvmField val maxRecentStickerCount = addConfig("maxRecentStickerCount", ConfigItem.configTypeInt, 20)
    @JvmField val disableSwipeToNext = addConfig("disableSwipeToNextChannel", ConfigItem.configTypeBool, false)
    @JvmField val disableSwipeToNextTopic = addConfig("disableSwipeToNextTopic", ConfigItem.configTypeBool, false)
    @JvmField val disableChoosingSticker = addConfig("disableChoosingSticker", ConfigItem.configTypeBool, false)
    @JvmField val hideGroupSticker = addConfig("hideGroupSticker", ConfigItem.configTypeBool, false)
    @JvmField val rememberAllBackMessages = addConfig("rememberAllBackMessages", ConfigItem.configTypeBool, false)
    @JvmField val hideSendAsChannel = addConfig("hideSendAsChannel", ConfigItem.configTypeBool, false)
    @JvmField val showSpoilersDirectly = addConfig("showSpoilersDirectly", ConfigItem.configTypeBool, false)

    @JvmField val disableAutoDownloadingWin32Executable = addConfig("Win32ExecutableFiles", ConfigItem.configTypeBool, true)
    @JvmField val disableAutoDownloadingArchive = addConfig("ArchiveFiles", ConfigItem.configTypeBool, true)
    @JvmField val noPreloadTrackIfRepeatOne = addConfig("NoPreloadTrackIfRepeatOne", ConfigItem.configTypeBool, false)

    @JvmField val customAudioBitrate = addConfig("customAudioBitrate", ConfigItem.configTypeInt, 32)
    @JvmField val enhancedFileLoader = addConfig("enhancedFileLoader", ConfigItem.configTypeInt, ENHANCED_LOADER_OFF)
    @JvmField val uploadBoost = addConfig("uploadBoost", ConfigItem.configTypeBool, false)
    @JvmField val useOSMDroidMap = addConfig("useOSMDroidMap", ConfigItem.configTypeBool, false)
    @JvmField val mapDriftingFixForGoogleMaps = addConfig("mapDriftingFixForGoogleMaps", ConfigItem.configTypeBool, true)

    @JvmField val localPremium = addConfig("localPremium", ConfigItem.configTypeBool, false)

    @JvmField val usePersianCalendar = addConfig("UsePersianCalendar", ConfigItem.configTypeBool, false)
    @JvmField val displayPersianCalendarByLatin = addConfig("DisplayPersianCalendarByLatin", ConfigItem.configTypeBool, false)

    @JvmField val minimizedStickerCreator = addConfig("minimizedStickerCreator", ConfigItem.configTypeBool, false)

    // --- Ghost Mode ---
    @JvmField val sendReadMessagePackets = addConfig("sendReadMessagePackets", ConfigItem.configTypeBool, true)
    @JvmField val sendReadStoriesPackets = addConfig("sendReadStoriesPackets", ConfigItem.configTypeBool, true)
    @JvmField val sendOnlinePackets = addConfig("sendOnlinePackets", ConfigItem.configTypeBool, true)
    @JvmField val sendUploadProgress = addConfig("sendUploadProgress", ConfigItem.configTypeBool, true)
    @JvmField val sendOfflinePacketAfterOnline = addConfig("sendOfflinePacketAfterOnline", ConfigItem.configTypeBool, false)
    @JvmField val markReadAfterSend = addConfig("markReadAfterSend", ConfigItem.configTypeBool, true)
    @JvmField val showGhostInDrawer = addConfig("showGhostInDrawer", ConfigItem.configTypeBool, false)
    @JvmField val showGhostModeStatus = addConfig("showGhostModeStatus", ConfigItem.configTypeBool, false)

    // --- Locked Status ---
    @JvmField val sendReadMessagePacketsLocked = addConfig("sendReadMessagePacketsLocked", ConfigItem.configTypeBool, false)
    @JvmField val sendReadStoriesPacketsLocked = addConfig("sendReadStoriesPacketsLocked", ConfigItem.configTypeBool, false)
    @JvmField val sendOnlinePacketsLocked = addConfig("sendOnlinePacketsLocked", ConfigItem.configTypeBool, false)
    @JvmField val sendUploadProgressLocked = addConfig("sendUploadProgressLocked", ConfigItem.configTypeBool, false)
    @JvmField val sendOfflinePacketAfterOnlineLocked = addConfig("sendOfflinePacketAfterOnlineLocked", ConfigItem.configTypeBool, false)

    // NagramX
    val enableSaveDeletedMessages =
        addConfig(
            "EnableSaveDeletedMessages",
            ConfigItem.configTypeBool,
            false
        )
    val saveDeletedMessagesPrivate =
        addConfig(
            "SaveDeletedMessagesInPrivate",
            ConfigItem.configTypeBool,
            true
        )
    val saveDeletedMessagesGroup =
        addConfig(
            "SaveDeletedMessagesInGroups",
            ConfigItem.configTypeBool,
            true
        )
    val saveDeletedMessagesChannel =
        addConfig(
            "SaveDeletedMessagesInChannels",
            ConfigItem.configTypeBool,
            false
        )
    val enableSaveEditsHistory =
        addConfig(
            "EnableSaveEditsHistory",
            ConfigItem.configTypeBool,
            false
        )
    val allowScreenCapture =
        addConfig(
            "AllowScreenCapture",
            ConfigItem.configTypeBool,
            true
        )
    val allowCopyProtectedContent =
        addConfig(
            "AllowCopyProtectedContent",
            ConfigItem.configTypeBool,
            true
        )
    val saveTTLMedia =
        addConfig(
            "SaveTTLMedia",
            ConfigItem.configTypeBool,
            true
        )
    val compactChatInput =
        addConfig(
            "CompactChatInput",
            ConfigItem.configTypeBool,
            false
        )
    val saveLocalLastSeen =
        addConfig(
            "SaveLocalLastSeen",
            ConfigItem.configTypeBool,
            false
        )
    val messageSavingSaveMedia =
        addConfig(
            "MessageSavingSaveMedia",
            ConfigItem.configTypeBool,
            true
        )
    val saveMediaInPrivateChats =
        addConfig(
            "SaveMediaInPrivateChats",
            ConfigItem.configTypeBool,
            true
        )
    val saveMediaInPublicChannels =
        addConfig(
            "SaveMediaInPublicChannels",
            ConfigItem.configTypeBool,
            true
        )
    val saveMediaInPrivateChannels =
        addConfig(
            "SaveMediaInPrivateChannels",
            ConfigItem.configTypeBool,
            true
        )
    val saveMediaInPublicGroups =
        addConfig(
            "SaveMediaInPublicGroups",
            ConfigItem.configTypeBool,
            true
        )
    val saveMediaInPrivateGroups =
        addConfig(
            "SaveMediaInPrivateGroups",
            ConfigItem.configTypeBool,
            true
        )
    val saveDeletedMessageForBot =
        addConfig(
            "SaveDeletedMessageForBot", // save in bot chats
            ConfigItem.configTypeBool,
            false
        )
    val saveDeletedMessageForBotUser =
        addConfig(
            "SaveDeletedMessageForBotUser", // all messages from bot
            ConfigItem.configTypeBool,
            false
        )
    val customDeletedMark =
        addConfig(
            "CustomDeletedMark",
            ConfigItem.configTypeString,
            ""
        )
    val hidePremiumSection =
        addConfig(
            "HidePremiumSection",
            ConfigItem.configTypeBool,
            false
        )
    val hideHelpSection =
        addConfig(
            "HideHelpSection",
            ConfigItem.configTypeBool,
            true
        )
    val llmApiUrl =
        addConfig(
            "LlmApiUrl",
            ConfigItem.configTypeString,
            ""
        )
    val llmApiKey =
        addConfig(
            "LlmApiKey",
            ConfigItem.configTypeString,
            ""
        )
    val llmModelName =
        addConfig(
            "LlmModelName",
            ConfigItem.configTypeString,
            ""
        )
    val llmSystemPrompt =
        addConfig(
            "LlmSystemPrompt",
            ConfigItem.configTypeString,
            ""
        )
    val llmUserPrompt =
        addConfig(
            "LlmUserPrompt",
            ConfigItem.configTypeString,
            ""
        )
    val llmProviderPreset =
        addConfig(
            "LlmProviderPreset",
            ConfigItem.configTypeInt,
            0
        )
    val llmProviderOpenAIKey =
        addConfig(
            "LlmProviderOpenAIKey",
            ConfigItem.configTypeString,
            ""
        )
    val llmProviderOpenAIModel =
        addConfig(
            "LlmProviderOpenAIModel",
            ConfigItem.configTypeString,
            ""
        )
    val llmProviderGeminiKey =
        addConfig(
            "LlmProviderGeminiKey",
            ConfigItem.configTypeString,
            ""
        )
    val llmProviderGeminiModel =
        addConfig(
            "LlmProviderGeminiModel",
            ConfigItem.configTypeString,
            ""
        )
    val llmProviderXAIKey =
        addConfig(
            "LlmProviderXAIKey",
            ConfigItem.configTypeString,
            ""
        )
    val llmProviderXAIModel =
        addConfig(
            "LlmProviderXAIModel",
            ConfigItem.configTypeString,
            ""
        )
    val llmProviderGroqKey =
        addConfig(
            "LlmProviderGroqKey",
            ConfigItem.configTypeString,
            ""
        )
    val llmProviderGroqModel =
        addConfig(
            "LlmProviderGroqModel",
            ConfigItem.configTypeString,
            ""
        )
    val llmProviderDeepSeekKey =
        addConfig(
            "LlmProviderDeepSeekKey",
            ConfigItem.configTypeString,
            ""
        )
    val llmProviderDeepSeekModel =
        addConfig(
            "LlmProviderDeepSeekModel",
            ConfigItem.configTypeString,
            ""
        )
    val llmProviderCerebrasKey =
        addConfig(
            "LlmProviderCerebrasKey",
            ConfigItem.configTypeString,
            ""
        )
    val llmProviderCerebrasModel =
        addConfig(
            "LlmProviderCerebrasModel",
            ConfigItem.configTypeString,
            ""
        )
    val llmProviderOllamaCloudKey =
        addConfig(
            "LlmProviderOllamaCloudKey",
            ConfigItem.configTypeString,
            ""
        )
    val llmProviderOllamaCloudModel =
        addConfig(
            "LlmProviderOllamaCloudModel",
            ConfigItem.configTypeString,
            ""
        )
    val llmProviderOpenRouterKey =
        addConfig(
            "LlmProviderOpenRouterKey",
            ConfigItem.configTypeString,
            ""
        )
    val llmProviderOpenRouterModel =
        addConfig(
            "LlmProviderOpenRouterModel",
            ConfigItem.configTypeString,
            ""
        )
    val llmProviderVercelAIGatewayKey =
        addConfig(
            "LlmProviderVercelAIGatewayKey",
            ConfigItem.configTypeString,
            ""
        )
    val llmProviderVercelAIGatewayModel =
        addConfig(
            "LlmProviderVercelAIGatewayModel",
            ConfigItem.configTypeString,
            ""
        )
    val llmProviderVertexKey =
        addConfig(
            "LlmProviderVertexKey",
            ConfigItem.configTypeString,
            ""
        )
    val llmProviderVertexModel =
        addConfig(
            "LlmProviderVertexModel",
            ConfigItem.configTypeString,
            ""
        )
    val llmTemperature =
        addConfig(
            "LlmTemperature",
            ConfigItem.configTypeFloat,
            0.7f
        )
    val llmUseContext =
        addConfig(
            "LlmUseContext",
            ConfigItem.configTypeBool,
            false
        )
    val llmContextSize =
        addConfig(
            "LlmContextSize",
            ConfigItem.configTypeInt,
            2
        )
    val llmUseContextInAutoTranslate =
        addConfig(
            "LlmUseContextInAutoTranslate",
            ConfigItem.configTypeBool,
            false
        )
    val translucentDeletedMessages =
        addConfig(
            "TranslucentDeletedMessages",
            ConfigItem.configTypeBool,
            true
        )
    val enableSeparateArticleTranslator =
        addConfig(
            "EnableSeparateArticleTranslator",
            ConfigItem.configTypeBool,
            false
        )
    val articleTranslationProvider =
        addConfig(
            "ArticleTranslationProvider",
            ConfigItem.configTypeInt,
            1
        )
    val disableCrashlyticsCollection =
        addConfig(
            "DisableCrashlyticsCollection",
            ConfigItem.configTypeBool,
            false
        )
    val showStickersRowToplevel =
        addConfig(
            "ShowStickersRowToplevel",
            ConfigItem.configTypeBool,
            true
        )
    val hideShareButtonInChannel =
        addConfig(
            "HideShareButtonInChannel",
            ConfigItem.configTypeBool,
            false
        )
    val preferredTranslateTargetLang =
        addConfig(
            "PreferredTranslateTargetLang",
            ConfigItem.configTypeString,
            ""
        )
    val telegramUIAutoTranslate =
        addConfig(
            "TelegramUIAutoTranslate",
            ConfigItem.configTypeBool,
            true
        )
    val translatorMode =
        addConfig(
            "TranslatorMode",
            ConfigItem.configTypeInt,
            0 // 0: off; 1: manual only; 2: all
        )
    val translatorModeWithOriginalMigrated =
        addConfig(
            "TranslatorModeWithOriginalMigrated",
            ConfigItem.configTypeBool,
            false
        )
    val centerActionBarTitleType =
        addConfig(
            "CenterActionBarTitleType",
            ConfigItem.configTypeInt,
            1 // 0: off; 1: always on; 2: settings only; 3: chats only
        )
    val hideArchive =
        addConfig(
            "HideArchive",
            ConfigItem.configTypeBool,
            false
        )
    val confirmAllLinks =
        addConfig(
            "ConfirmAllLinks",
            ConfigItem.configTypeBool,
            false
        )
    val useDeletedIcon =
        addConfig(
            "UseDeletedIcon",
            ConfigItem.configTypeBool,
            true
        )
    val useEditedIcon =
        addConfig(
            "UseEditedIcon",
            ConfigItem.configTypeBool,
            true
        )
    val saveToChatSubfolder =
        addConfig(
            "SaveToChatSubfolder",
            ConfigItem.configTypeBool,
            false
        )
    val silentMessageByDefault =
        addConfig(
            "SilentMessageByDefault",
            ConfigItem.configTypeBool,
            false
        )
    val folderNameAsTitle =
        addConfig(
            "FolderNameAsTitle",
            ConfigItem.configTypeBool,
            false
        )
    val translatorKeepMarkdown =
        addConfig(
            "TranslatorKeepMarkdown",
            ConfigItem.configTypeBool,
            true
        )
    val googleTranslateExp =
        addConfig(
            "GoogleTranslateExp",
            ConfigItem.configTypeBool,
            true
        )
    val springAnimationCrossfade =
        addConfig(
            "SpringAnimationCrossfade",
            ConfigItem.configTypeBool,
            true
        )
    val dontAutoPlayNextVoice =
        addConfig(
            "DontAutoPlayNextVoice",
            ConfigItem.configTypeBool,
            false
        )
    val messageColoredBackground =
        addConfig(
            "MessageColoredBackground",
            ConfigItem.configTypeBool,
            true
        )
    val chatMenuItemBoostGroup =
        addConfig(
            "ChatMenuItemBoostGroup",
            ConfigItem.configTypeBool,
            true
        )
    val chatMenuItemLinkedChat =
        addConfig(
            "ChatMenuItemLinkedChat",
            ConfigItem.configTypeBool,
            true
        )
    val chatMenuItemToBeginning =
        addConfig(
            "ChatMenuItemToBeginning",
            ConfigItem.configTypeBool,
            true
        )
    val chatMenuItemGoToMessage =
        addConfig(
            "ChatMenuItemGoToMessage",
            ConfigItem.configTypeBool,
            true
        )
    val chatMenuItemHideTitle =
        addConfig(
            "ChatMenuItemHideTitle",
            ConfigItem.configTypeBool,
            true
        )
    val chatMenuItemViewDeleted =
        addConfig(
            "ChatMenuItemViewDeleted",
            ConfigItem.configTypeBool,
            true
        )
    val chatMenuItemClearDeleted =
        addConfig(
            "ChatMenuItemClearDeleted",
            ConfigItem.configTypeBool,
            true
        )
    val chatMenuItemDeleteOwnMessages =
        addConfig(
            "ChatMenuItemDeleteOwnMessages",
            ConfigItem.configTypeBool,
            true
        )
    val mediaViewerMenuItemForward =
        addConfig(
            "MediaViewerMenuItemForward",
            ConfigItem.configTypeBool,
            true
        )
    val mediaViewerMenuItemNoQuoteForward =
        addConfig(
            "MediaViewerMenuItemNoQuoteForward",
            ConfigItem.configTypeBool,
            true
        )
    val mediaViewerMenuItemCopyFrame =
        addConfig(
            "MediaViewerMenuItemCopyFrame",
            ConfigItem.configTypeBool,
            true
        )
    val mediaViewerMenuItemCopyPhoto =
        addConfig(
            "MediaViewerMenuItemCopyPhoto",
            ConfigItem.configTypeBool,
            true
        )
    val mediaViewerMenuItemSetProfilePhoto =
        addConfig(
            "MediaViewerMenuItemSetProfilePhoto",
            ConfigItem.configTypeBool,
            true
        )
    val mediaViewerMenuItemScanQRCode =
        addConfig(
            "MediaViewerMenuItemScanQRCode",
            ConfigItem.configTypeBool,
            true
        )
    val hideReactions =
        addConfig(
            "HideReactions",
            ConfigItem.configTypeBool,
            false
        )
    val performanceClass =
        addConfig(
            "PerformanceClass",
            ConfigItem.configTypeInt,
            0
        )
    val transcribeProvider =
        addConfig(
            "TranscribeProvider",
            ConfigItem.configTypeInt,
            0
        )
    val transcribeProviderCfAccountID =
        addConfig(
            "TranscribeProviderCfAccountID",
            ConfigItem.configTypeString,
            ""
        )
    val transcribeProviderCfApiToken =
        addConfig(
            "TranscribeProviderCfApiToken",
            ConfigItem.configTypeString,
            ""
        )
    val transcribeProviderGeminiApiKey =
        addConfig(
            "TranscribeProviderGeminiApiKey",
            ConfigItem.configTypeString,
            ""
        )
    val transcribeProviderOpenAiApiBase =
        addConfig(
            "TranscribeProviderOpenAiApiBase",
            ConfigItem.configTypeString,
            ""
        )
    val transcribeProviderOpenAiModel =
        addConfig(
            "TranscribeProviderOpenAiModel",
            ConfigItem.configTypeString,
            ""
        )
    val transcribeProviderOpenAiApiKey =
        addConfig(
            "TranscribeProviderOpenAiApiKey",
            ConfigItem.configTypeString,
            ""
        )
    val transcribeProviderOpenAiPrompt =
        addConfig(
            "TranscribeProviderOpenAiPrompt",
            ConfigItem.configTypeString,
            ""
        )
    val showReplyInPrivate =
        addConfig(
            "ReplyInPrivate",
            ConfigItem.configTypeBool,
            false
        )
    val transcribeProviderGeminiPrompt =
        addConfig(
            "TranscribeProviderGeminiPrompt",
            ConfigItem.configTypeString,
            ""
        )
    val hideDividers =
        addConfig(
            "HideDividers",
            ConfigItem.configTypeBool,
            false
        )
    val iconReplacements =
        addConfig(
            "IconReplacements",
            ConfigItem.configTypeInt,
            0
        )
    val showCopyAsSticker =
        addConfig(
            "CopyPhotoAsSticker",
            ConfigItem.configTypeBool,
            false
        )
    val showAddToStickers =
        addConfig(
            "AddToStickers",
            ConfigItem.configTypeBool,
            false
        )
    val showAddToFavorites =
        addConfig(
            "AddToFavorites",
            ConfigItem.configTypeBool,
            true
        )
    val showTranslateMessageLLM =
        addConfig(
            "TranslateMessageLLM",
            ConfigItem.configTypeBool,
            false
        )
    val shortcutsAdministrators =
        addConfig(
            "ChannelAdministrators",
            ConfigItem.configTypeBool,
            false
        )
    val shortcutsRecentActions =
        addConfig(
            "EventLog",
            ConfigItem.configTypeBool,
            true
        )
    val shortcutsStatistics =
        addConfig(
            "Statistics",
            ConfigItem.configTypeBool,
            false
        )
    val shortcutsPermissions =
        addConfig(
            "ChannelPermissions",
            ConfigItem.configTypeBool,
            false
        )
    val shortcutsMembers =
        addConfig(
            "GroupMembers",
            ConfigItem.configTypeBool,
            false
        )
    val leftBottomButton =
        addConfig(
            "LeftBottomButtonAction",
            ConfigItem.configTypeInt,
            0
        )
    val showTextMonoCode =
        addConfig(
            "TextMonoCode",
            ConfigItem.configTypeBool,
            true
        )
    val showCopyLink =
        addConfig(
            "CopyLink",
            ConfigItem.configTypeBool,
            true
        )
    val preferCommonGroupsTab =
        addConfig(
            "PreferCommonGroupsTab",
            ConfigItem.configTypeBool,
            true
        )
    val groupedMessageMenu =
        addConfig(
            "GroupedMessageMenu",
            ConfigItem.configTypeBool,
            true
        )
    val autoUpdateChannel =
        addConfig(
            "AutoUpdateChannel",
            ConfigItem.configTypeInt,
            1 // 0: off; 1: release; 2: beta
        )
    val premiumItemEmojiStatus =
        addConfig(
            "PremiumItemEmojiStatus",
            ConfigItem.configTypeBool,
            true
        )
    val premiumItemEmojiInReplies =
        addConfig(
            "PremiumItemEmojiInReplies",
            ConfigItem.configTypeBool,
            true
        )
    val premiumItemCustomColorInReplies =
        addConfig(
            "PremiumItemCustomColorInReplies",
            ConfigItem.configTypeBool,
            true
        )
    val premiumItemCustomWallpaper =
        addConfig(
            "PremiumItemCustomWallpaper",
            ConfigItem.configTypeBool,
            true
        )
    val premiumItemVideoAvatar =
        addConfig(
            "PremiumItemVideoAvatar",
            ConfigItem.configTypeBool,
            true
        )
    val premiumItemStarInReactions =
        addConfig(
            "PremiumItemStarInReactions",
            ConfigItem.configTypeBool,
            true
        )
    val premiumItemStickerEffects =
        addConfig(
            "PremiumItemStickerEffects",
            ConfigItem.configTypeBool,
            true
        )
    val premiumItemBoosts =
        addConfig(
            "PremiumItemBoosts",
            ConfigItem.configTypeBool,
            true
        )
    val switchStyle =
        addConfig(
            "SwitchStyle",
            ConfigItem.configTypeInt,
            0 // 0: default; 1: Modern; 2: MD3
        )
    val sliderStyle =
        addConfig(
            "SliderStyle",
            ConfigItem.configTypeInt,
            0 // 0: default; 1: Modern; 2: MD3
        )
    val iosButtonPlacement =
        addConfig(
            "IosButtonPlacement",
            ConfigItem.configTypeBool,
            false
        )
    val iosInputAppearance =
        addConfig(
            "IosInputAppearance",
            ConfigItem.configTypeBool,
            false
        )
    val compactInputSize =
        addConfig(
            "CompactInputSize",
            ConfigItem.configTypeBool,
            false
        )
    val disableGooeyAvatarAnimation =
        addConfig(
            "DisableGooeyAvatarAnimation",
            ConfigItem.configTypeBool,
            false
        )
    val showSquareAvatar =
        addConfig(
            "ShowSquareAvatar",
            ConfigItem.configTypeBool,
            false
        )
    val showForwardTextEdit =
        addConfig(
            "ShowForwardTextEdit",
            ConfigItem.configTypeBool,
            true
        )
    val actionButtonStyle =
        addConfig(
            "ActionButtonStyle",
            ConfigItem.configTypeInt,
            0
        )
    val ignoreUnreadCount =
        addConfig(
            "IgnoreUnreadCount",
            ConfigItem.configTypeInt,
            getIgnoreMutedCountLegacy()
        )
    val markdownParser =
        addConfig(
            "MarkdownParser",
            ConfigItem.configTypeInt,
            MARKDOWN_PARSER_NEKO
        )
    val defaultScheduledTime =
        addConfig(
            "DefaultScheduledTime",
            ConfigItem.configTypeInt,
            10
        )
    val enableQuickSchedule =
        addConfig(
            "EnableQuickSchedule",
            ConfigItem.configTypeBool,
            false
        )
    val disableAiEditor =
        addConfig(
            "DisableAiEditor",
            ConfigItem.configTypeBool,
            false
        )
    val keepTranslatorPreferences =
        addConfig(
            "KeepTranslatorPreferences",
            ConfigItem.configTypeBool,
            false
        )
    val usePinnedReactionsChats =
        addConfig(
            "UsePinnedReactionsChats",
            ConfigItem.configTypeBool,
            false
        )
    val pinnedReactionsChats =
        addConfig(
            "PinnedReactionsChats",
            ConfigItem.configTypeString,
            "[]"
        )
    val usePinnedReactionsChannels =
        addConfig(
            "UsePinnedReactionsChannels",
            ConfigItem.configTypeBool,
            false
        )
    val pinnedReactionsChannels =
        addConfig(
            "PinnedReactionsChannels",
            ConfigItem.configTypeString,
            "[]"
        )
    val hideStoriesFromHeader =
        addConfig(
            "HideStoriesFromHeader",
            ConfigItem.configTypeBool,
            true
        )
    val disableAvatarBlur =
        addConfig(
            "DisableAvatarBlur",
            ConfigItem.configTypeBool,
            false
        )
    val disableInAppBrowserGestures =
        addConfig(
            "DisableInAppBrowserGestures",
            ConfigItem.configTypeBool,
            false
        )
    val idDcType =
        addConfig(
            "IdDcType",
            ConfigItem.configTypeInt,
            1
        )
    val fixLinkPreview =
        addConfig(
            "FixLinkPreview",
            ConfigItem.configTypeBool,
            true
        )
    val showAddToBookmark =
        addConfig(
            "ShowAddToBookmark",
            ConfigItem.configTypeBool,
            false
        )
    val sortByUnread =
        addConfig(
            "SortByUnread",
            ConfigItem.configTypeBool,
            false
        )
    val cameraInVideoMessages =
        addConfig(
            "CameraInVideoMessages",
            ConfigItem.configTypeInt,
            1 // 0: front; 1: rear; 2: ask
        )
    val showCopyFrame =
        addConfig(
            "MessageMenuCopyFrame",
            ConfigItem.configTypeBool,
            false
        )
    val deleteChatForBothSides =
        addConfig(
            "DeleteChatForBothSides",
            ConfigItem.configTypeBool,
            true
        )
    val backAnimationStyle =
        addConfig(
            "BackAnimationStyle",
            ConfigItem.configTypeInt,
            0 // 0: Classic, 1: Spring, 2: Predictive Back
        )
    val mainTabsHideTitles =
        addConfig(
            "MainTabsHideTitles",
            ConfigItem.configTypeBool,
            false
        )
    val mainTabsHideContacts =
        addConfig(
            "MainTabsHideContacts",
            ConfigItem.configTypeBool,
            false
        )
    val showNotificationPreviewWhenLocked =
        addConfig(
            "ShowNotificationPreviewWhenLocked",
            ConfigItem.configTypeBool,
            false
        )
    val strokeOnViews =
        addConfig(
            "StrokeOnViews",
            ConfigItem.configTypeBool,
            true
        )
    val folderTabsStroke =
        addConfig(
            "FolderTabsStroke",
            ConfigItem.configTypeBool,
            true
        )
    val liquidGlassAngle =
        addConfig(
            "LiquidGlassAngle",
            ConfigItem.configTypeInt,
            0
        )
    val liquidGlassIntensity =
        addConfig(
            "LiquidGlassIntensity",
            ConfigItem.configTypeInt,
            75
        )
    val hideBottomNavigationBar =
        addConfig(
            "HideBottomNavigationBar",
            ConfigItem.configTypeBool,
            false
        )
    val hideDialogsSearchField =
        addConfig(
            "HideDialogsSearchField",
            ConfigItem.configTypeBool,
            false
        )
    val deepLTranslateKey =
        addConfig(
            "DeepLTranslateKey",
            ConfigItem.configTypeString,
            ""
        )

    val preferredTranslateTargetLangList = ArrayList<String>()
    fun updatePreferredTranslateTargetLangList() {
        AndroidUtilities.runOnUIThread({
            preferredTranslateTargetLangList.clear()
            val str = preferredTranslateTargetLang.String().trim()

            if (str.isEmpty()) return@runOnUIThread

            val languages = str.replace('-', '_').split(",")
            if (languages.isEmpty() || languages[0].trim().isEmpty()) return@runOnUIThread

            languages.forEach { lang ->
                preferredTranslateTargetLangList.add(lang.trim().lowercase())
            }
        }, 1000)
    }

    private fun getIgnoreMutedCountLegacy(): Int {
        return when {
            getPreferences().getBoolean(
                "IgnoreFolderCount", false
            ) -> DIALOG_FILTER_EXCLUDE_ALL

            getPreferences().getBoolean(
                "IgnoreMutedCount", true
            ) -> DIALOG_FILTER_EXCLUDE_MUTED

            else -> DIALOG_FILTER_EXCLUDE_NONE
        }
    }

    private fun fixConfig() {
        if (ApplicationLoader.applicationContext == null) {
            return
        }
        if (!translatorModeWithOriginalMigrated.Bool()) {
            if (getPreferences().contains(translatorMode.key)) {
                translatorMode.setConfigInt(
                    when (translatorMode.Int()) {
                        0 -> 1
                        1 -> 0
                        else -> 0
                    }
                )
            }
            translatorModeWithOriginalMigrated.setConfigBool(true)
        }
        if (translatorMode.Int() !in 0..2) {
            translatorMode.setConfigInt(0)
        }
        if (!getPreferences().contains(idDcType.key) && !getPreferences().getBoolean(
                "ShowIdAndDc", true
            )
        ) {
            idDcType.setConfigInt(0)
        }
        if (!getPreferences().contains(cameraInVideoMessages.key)) {
            val legacyRear = getPreferences().getBoolean("RearVideoMessages", false)
            cameraInVideoMessages.setConfigInt(if (legacyRear) 1 else 0)
        }
        if (!getPreferences().contains(backAnimationStyle.key) &&
            getPreferences().contains("SpringAnimation")
        ) {
            val legacySpring = getPreferences().getBoolean("SpringAnimation", false)
            if (legacySpring) {
                backAnimationStyle.setConfigInt(1) // SPRING
            }
            getPreferences().edit { remove("SpringAnimation") }
        }
        if (!getPreferences().contains(strokeOnViews.key)) {
            strokeOnViews.changed(SharedConfig.getDevicePerformanceClass() != SharedConfig.PERFORMANCE_CLASS_LOW)
        }
        if (!getPreferences().contains(compactInputSize.key) && getPreferences().contains(compactChatInput.key)) {
            compactInputSize.setConfigBool(compactChatInput.Bool())
        }
        if (!getPreferences().contains(actionButtonStyle.key)) {
            val legacyWhiteSend = getPreferences().getBoolean("WhiteSendButton", false)
            actionButtonStyle.setConfigInt(if (legacyWhiteSend) 2 else 0)
            getPreferences().edit { remove("WhiteSendButton") }
        }

        val mainPreferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE)
        if (!mainPreferences.contains("photoHighQualityDefault") && getPreferences().contains("SendHighQualityPhoto")) {
            val highQuality = getPreferences().getBoolean("SendHighQualityPhoto", true)
            mainPreferences.edit {
                putBoolean("photoHighQualityDefault", highQuality)
            }
            SharedConfig.photoHighQualityDefault = highQuality
        }

        val currentLlmApiUrl = llmApiUrl.String()
        val normalizedLlmApiUrl = UrlNormalizer.normalizeBaseUrl(currentLlmApiUrl)
        if (normalizedLlmApiUrl != currentLlmApiUrl) {
            llmApiUrl.setConfigString(normalizedLlmApiUrl)
        }
    }

    private fun resetInvalidConfig(o: ConfigItem, e: RuntimeException) {
        val key = if (o is ConfigItemKeyLinked) o.keyLinked.key else o.key
        FileLog.e("Invalid config value for $key", e)
        o.value = o.defaultValue
        getPreferences().edit { remove(key) }
    }

    private fun addConfig(
        k: String, t: Int, d: Any?
    ): ConfigItem {
        val a = ConfigItem(
            k, t, d
        )
        configs.add(
            a
        )
        return a
    }

    @Suppress("SameParameterValue")
    private fun addConfig(
        k: String, t: ConfigItem, d: Int, e: Any?
    ): ConfigItem {
        val a = ConfigItemKeyLinked(
            k,
            t,
            d,
            e,
        )
        configs.add(
            a
        )
        return a
    }

    @JvmStatic
    fun fixDriftingForGoogleMaps(): Boolean {
        return !useOSMDroidMap.Bool() && mapDriftingFixForGoogleMaps.Bool()
    }

    private val ghostToggleItems: List<Pair<ConfigItem, ConfigItem>> by lazy {
        listOf(
            Pair(sendReadMessagePackets, sendReadMessagePacketsLocked),
            Pair(sendReadStoriesPackets, sendReadStoriesPacketsLocked),
            Pair(sendOnlinePackets, sendOnlinePacketsLocked),
            Pair(sendUploadProgress, sendUploadProgressLocked),
            Pair(sendOfflinePacketAfterOnline, sendOfflinePacketAfterOnlineLocked)
        )
    }

    @JvmStatic
    fun isGhostModeActive(): Boolean {
        for (pair in ghostToggleItems) {
            val item = pair.first
            val lockedItem = pair.second
            if (!lockedItem.Bool()) {
                val currentValue = item.Bool()
                val isGhostState = (item == sendOfflinePacketAfterOnline) == currentValue
                if (!isGhostState) {
                    return false
                }
            }
        }
        return true
    }

    @JvmStatic
    fun setGhostMode(enabled: Boolean) {
        for (pair in ghostToggleItems) {
            val item = pair.first
            val lockedItem = pair.second
            if (!lockedItem.Bool()) {
                val targetValue = (item == sendOfflinePacketAfterOnline) == enabled
                item.setConfigBool(targetValue)
            }
        }
    }

    @JvmStatic
    fun toggleGhostMode() {
        val newState = !isGhostModeActive()
        setGhostMode(newState)
        val sendOnlineNow = !newState && !sendOfflinePacketAfterOnlineLocked.Bool() && sendOfflinePacketAfterOnline.Bool()
        AyuGhostUtils.performStatusRequest(sendOnlineNow)
    }

    fun loadConfig(
        force: Boolean
    ) {
        synchronized(
            sync
        ) {
            if (configLoaded && !force) {
                return
            }
            if (ApplicationLoader.applicationContext == null) {
                return
            }
            for (i in configs.indices) {
                val o = configs[i]
                try {
                    if (o.type == ConfigItem.configTypeBool) {
                        o.value = getPreferences().getBoolean(
                            o.key, o.defaultValue as Boolean
                        )
                    }
                    if (o.type == ConfigItem.configTypeInt) {
                        try {
                            o.value = getPreferences().getInt(
                                o.key, o.defaultValue as Int
                            )
                        } catch (e: ClassCastException) {
                            try {
                                val oldBool = getPreferences().getBoolean(o.key, false)
                                o.value = if (oldBool) ENHANCED_LOADER_BALANCED else ENHANCED_LOADER_OFF
                                getPreferences().edit().putInt(o.key, o.value as Int).apply()
                            } catch (_: Exception) {
                                o.value = o.defaultValue
                            }
                        }
                    }
                    if (o.type == ConfigItem.configTypeLong) {
                        o.value = getPreferences().getLong(
                            o.key, (o.defaultValue as Long)
                        )
                    }
                    if (o.type == ConfigItem.configTypeFloat) {
                        o.value = getPreferences().getFloat(
                            o.key, (o.defaultValue as Float)
                        )
                    }
                    if (o.type == ConfigItem.configTypeString) {
                        o.value = getPreferences().getString(
                            o.key, o.defaultValue as String
                        )
                    }
                    if (o.type == ConfigItem.configTypeSetInt) {
                        val ss = getPreferences().getStringSet(
                            o.key, HashSet()
                        )
                        val si = HashSet<Int>()
                        for (s in ss!!) {
                            si.add(
                                s.toInt()
                            )
                        }
                        o.value = si
                    }
                    if (o.type == ConfigItem.configTypeMapIntInt) {
                        val cv = getPreferences().getString(
                            o.key, ""
                        )
                        // Log.e("NC", String.format("Getting pref %s val %s", o.key, cv));
                        if (cv!!.isEmpty()) {
                            o.value = HashMap<Int, Int>()
                        } else {
                            try {
                                val data = Base64.decode(
                                    cv, Base64.DEFAULT
                                )
                                val ois = ObjectInputStream(
                                    ByteArrayInputStream(
                                        data
                                    )
                                )
                                o.value = ois.readObject() as HashMap<*, *>
                                if (o.value == null) {
                                    o.value = HashMap<Int, Int>()
                                }
                                ois.close()
                            } catch (_: Exception) {
                                o.value = HashMap<Int, Int>()
                            }
                        }
                    }
                    if (o.type == ConfigItem.configTypeBoolLinkInt) {
                        o as ConfigItemKeyLinked
                        o.changedFromKeyLinked(getPreferences().getInt(o.keyLinked.key, 0))
                    }
                } catch (e: ClassCastException) {
                    resetInvalidConfig(o, e)
                } catch (e: NumberFormatException) {
                    resetInvalidConfig(o, e)
                }
            }
            if (!configLoaded) {
                getPreferences().registerOnSharedPreferenceChangeListener(CloudSettingsHelper.listener)
                for (a in 1..5) {
                    datacenterInfos.add(DatacenterInfo(a))
                }
            }
            configLoaded = true
        }
    }

    fun getConfigTypes(): Map<String, Int> {
        synchronized(sync) {
            return configs.associate { it.key to it.type }
        }
    }

    init {
        init()
    }

}
