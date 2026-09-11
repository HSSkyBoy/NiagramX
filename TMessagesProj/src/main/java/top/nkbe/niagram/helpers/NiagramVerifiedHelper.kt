package top.nkbe.niagram.helpers

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.CombinedDrawable
import top.nkbe.niagram.config.NyaConfig

object NiagramVerifiedHelper {

    const val TYPE_NONE = 0
    const val TYPE_TELEGRAM = 1
    const val TYPE_NIAGRAM_OFFICIAL = 2
    const val TYPE_NAGRAM_DEV = 3

    const val COLOR_NIAGRAM_PURPLE = 0xFF7452EB.toInt()
    const val COLOR_NAGRAM_BLUE = 0xFF2EA6FF.toInt()
    const val COLOR_CHECK_WHITE = 0xFFFFFFFF.toInt()

    private val NIAGRAM_DEVELOPERS = longArrayOf(
        6424286140L, // 空一格 K
        8554837612L  // HSSkyBoy
    )

    private val NIAGRAM_OFFICIAL_CHATS = longArrayOf(
        3959358684L, // Channel
        4352791341L, // Chat Group
        2163306347L  // Chat Channel
    )

    private val NAGRAM_DEVELOPERS = longArrayOf(
        784901712L, // NextAlone
        896711046L, // nekohasekai
        380570774L, // Haruhi
        457896977L, // Queally
        782954985L  // MaiTungTM
    )

    @JvmStatic
    fun isNiagramDeveloper(userId: Long): Boolean {
        return NIAGRAM_DEVELOPERS.contains(userId)
    }

    @JvmStatic
    fun isNiagramOfficialChat(chatId: Long): Boolean {
        return NIAGRAM_OFFICIAL_CHATS.contains(chatId)
    }

    @JvmStatic
    fun isNagramDeveloper(userId: Long): Boolean {
        return NAGRAM_DEVELOPERS.contains(userId)
    }

    @JvmStatic
    fun isSelfVerified(userId: Long): Boolean {
        if (!NyaConfig.verifySelf.Bool()) return false
        for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
            val userConfig = UserConfig.getInstance(a)
            if (userConfig != null && userConfig.isClientActivated && userConfig.clientUserId == userId) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun getUserVerifiedType(user: TLRPC.User?): Int {
        if (user == null) return TYPE_NONE
        if (isNiagramDeveloper(user.id) || isSelfVerified(user.id)) {
            return TYPE_NIAGRAM_OFFICIAL
        }
        if (isNagramDeveloper(user.id)) {
            return TYPE_NAGRAM_DEV
        }
        if (user.verified) {
            return TYPE_TELEGRAM
        }
        return TYPE_NONE
    }

    @JvmStatic
    fun getChatVerifiedType(chat: TLRPC.Chat?): Int {
        if (chat == null) return TYPE_NONE
        if (isNiagramOfficialChat(chat.id)) {
            return TYPE_NIAGRAM_OFFICIAL
        }
        if (chat.verified) {
            return TYPE_TELEGRAM
        }
        return TYPE_NONE
    }

    @JvmStatic
    fun isVerified(user: TLRPC.User?): Boolean {
        return getUserVerifiedType(user) != TYPE_NONE
    }

    @JvmStatic
    fun isVerified(chat: TLRPC.Chat?): Boolean {
        return getChatVerifiedType(chat) != TYPE_NONE
    }

    @JvmStatic
    fun getBadgeBackgroundColor(type: Int, fallbackColor: Int): Int {
        return when (type) {
            TYPE_NIAGRAM_OFFICIAL -> COLOR_NIAGRAM_PURPLE
            TYPE_NAGRAM_DEV -> COLOR_NAGRAM_BLUE
            else -> fallbackColor
        }
    }

    @JvmStatic
    fun createVerifiedDrawable(context: Context, type: Int, defaultBgColor: Int): Drawable {
        val bg = ContextCompat.getDrawable(context, R.drawable.verified_area)!!.mutate()
        val check = ContextCompat.getDrawable(context, R.drawable.verified_check)!!.mutate()
        val bgColor = getBadgeBackgroundColor(type, defaultBgColor)
        bg.colorFilter = PorterDuffColorFilter(bgColor, PorterDuff.Mode.MULTIPLY)
        check.colorFilter = PorterDuffColorFilter(COLOR_CHECK_WHITE, PorterDuff.Mode.MULTIPLY)
        return CombinedDrawable(bg, check)
    }
}
