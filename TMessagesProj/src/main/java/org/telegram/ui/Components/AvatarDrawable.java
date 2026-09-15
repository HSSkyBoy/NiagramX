/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import top.nkbe.niagram.config.NyaConfig;
import top.nkbe.niagram.filters.AyuFilter;

import java.util.ArrayList;

public class AvatarDrawable extends Drawable {

    private static final int COLOR_ACCENT_BLUE_ID = 5;
    private static final float CLOWN_ICON_SCALE = 0.65f;
    private static final float SQUARE_AVATAR_RADIUS_FACTOR = 0.25f;

    private TextPaint namePaint;
    private final Paint localBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean hasGradient;
    private boolean hasAdvancedGradient;
    private int color, color2;
    private GradientTools advancedGradient;
    private Rect lastAdvancedBounds;
    private boolean needApplyColorAccent;
    private StaticLayout textLayout;
    private float textWidth;
    private float textHeight;
    private float textLeft;
    private StaticLayout clownTextLayout;
    private float clownTextLeft;
    private float clownTextWidth;
    private float clownTextHeight;
    private boolean isProfile;
    private boolean drawDeleted;
    private boolean drawClown;
    private Drawable clownDrawable;
    private int avatarType;
    private float archivedAvatarProgress;
    private float scaleSize = 1f;
    private StringBuilder stringBuilder = new StringBuilder(5);
    private int roundRadius = -1;

    private int gradientTop, gradientBottom;
    private int gradientColor1, gradientColor2;
    private LinearGradient gradient;

    private int gradientTop2, gradientBottom2;
    private int gradientColor21, gradientColor22;
    private LinearGradient gradient2;
    private boolean drawAvatarBackground = true;
    private boolean rotate45Background = false;

    public static final int AVATAR_TYPE_NORMAL = 0;
    public static final int AVATAR_TYPE_SAVED = 1;
    public static final int AVATAR_TYPE_ARCHIVED = 2;
    public static final int AVATAR_TYPE_SHARES = 3;
    public static final int AVATAR_TYPE_REPLIES = 12;

    public static final int AVATAR_TYPE_FILTER_CONTACTS = 4;
    public static final int AVATAR_TYPE_FILTER_NON_CONTACTS = 5;
    public static final int AVATAR_TYPE_FILTER_GROUPS = 6;
    public static final int AVATAR_TYPE_FILTER_CHANNELS = 7;
    public static final int AVATAR_TYPE_FILTER_BOTS = 8;
    public static final int AVATAR_TYPE_FILTER_MUTED = 9;
    public static final int AVATAR_TYPE_FILTER_READ = 10;
    public static final int AVATAR_TYPE_FILTER_ARCHIVED = 11;
    public static final int AVATAR_TYPE_REGISTER = 13;
    public static final int AVATAR_TYPE_OTHER_CHATS = 14;
    public static final int AVATAR_TYPE_CLOSE_FRIENDS = 15;
    public static final int AVATAR_TYPE_GIFT = 16;
    public static final int AVATAR_TYPE_COUNTRY = 17;
    public static final int AVATAR_TYPE_UNCLAIMED = 18;
    public static final int AVATAR_TYPE_TO_BE_DISTRIBUTED = 19;
    public static final int AVATAR_TYPE_STORY = 20;
    public static final int AVATAR_TYPE_ANONYMOUS = 21;
    public static final int AVATAR_TYPE_MY_NOTES = 22;
    public static final int AVATAR_TYPE_EXISTING_CHATS = 23;
    public static final int AVATAR_TYPE_NEW_CHATS = 24;
    public static final int AVATAR_TYPE_PREMIUM = 25;
    public static final int AVATAR_TYPE_STARS = 26;
    public static final int AVATAR_TYPE_SUGGESTION = 27;

    /**
     * Matches {@link org.telegram.ui.Components.AvatarConstructorFragment#defaultColors}
     * but reordered to preserve color tints.
     */
    public static final int[][] advancedGradients = new int[][]{
            new int[]{0xFFF64884, 0xFFEF5B41, 0xFFF6A730, 0xFFFF7742},
            new int[]{0xFFF5694E, 0xFFF5772C, 0xFFFFD412, 0xFFFFA743},
            new int[]{0xFF837CFF, 0xFFB063FF, 0xFFFF72A9, 0xFFE269FF},
            new int[]{0xFF09D260, 0xFF5EDC40, 0xFFC1E526, 0xFF80DF2B},
            new int[]{0xFF5EB6FB, 0xFF1FCEEB, 0xFF45F7B7, 0xFF1FF1D9},
            new int[]{0xFF4D8DFF, 0xFF2BBFFF, 0xFF20E2CD, 0xFF0EE1F1},
            new int[]{0xFFF94BA0, 0xFFFB5C80, 0xFFFFB23A, 0xFFFE7E62},
    };

    private int alpha = 255;
    private Theme.ResourcesProvider resourcesProvider;
    private boolean invalidateTextLayout;

    public AvatarDrawable() {
        this((Theme.ResourcesProvider) null);
    }

    public AvatarDrawable(Theme.ResourcesProvider resourcesProvider) {
        super();
        this.resourcesProvider = resourcesProvider;
        namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        namePaint.setTypeface(AndroidUtilities.bold());
        namePaint.setTextSize(dp(18));
    }

    public AvatarDrawable(TLRPC.User user) {
        this(user, false);
    }

    public AvatarDrawable(TLRPC.Chat chat) {
        this(chat, false);
    }

    public AvatarDrawable(TLRPC.User user, boolean profile) {
        this();
        isProfile = profile;
        if (user != null) {
            setInfo(user.id, user.first_name, user.last_name, null);
            drawDeleted = UserObject.isDeleted(user);
            drawClown = !drawDeleted && isClownPeer(UserConfig.selectedAccount, user.id);
        }
    }

    public static boolean isClownPeer(int currentAccount, long id) {
        if (id <= 0) {
            return false;
        }
        return NyaConfig.INSTANCE.getClownAvatarForBlockedUsers().Bool() && isBlockedPeer(currentAccount, id);
    }

    public static boolean isBlockedPeer(int currentAccount, long id) {
        if (id <= 0) {
            return false;
        }
        return (MessagesController.getInstance(currentAccount).blockePeers.indexOfKey(id) >= 0) || AyuFilter.isCustomFilteredPeer(id);
    }

    public void setDrawClown(boolean value) {
        if (drawDeleted && value) {
            return;
        }
        drawClown = value;
        if (drawClown) {
            invalidateTextLayout = true;
        }
    }

    public boolean isDrawClown() {
        return drawClown;
    }

    public AvatarDrawable(TLRPC.Chat chat, boolean profile) {
        this();
        isProfile = profile;
        setInfo(chat);
    }

    public void setDrawAvatarBackground(boolean drawAvatarBackground) {
        this.drawAvatarBackground = drawAvatarBackground;
    }

    public void setProfile(boolean value) {
        isProfile = value;
    }

    public static int getPeerColorIndex(int color) {
        float[] tempHSV = Theme.getTempHsv(5);
        Color.colorToHSV(color, tempHSV);
        final int hue = (int) tempHSV[0];
        if (hue >= 345 || hue < 29) return 0; // red
        if (hue < 67) return 1; // orange
        if (hue < 140) return 3; // green
        if (hue < 199) return 4; // cyan
        if (hue < 234) return 5; // blue
        if (hue < 301) return 2; // violet
        return 6; // pink
    }

    public static int getColorIndex(long id) {
        return (int) Math.abs(id % Theme.keys_avatar_background.length);
    }

    public static int getColorForId(long id) {
        return Theme.getColor(Theme.keys_avatar_background[getColorIndex(id)]);
    }

    public static int getButtonColorForId(long id, Theme.ResourcesProvider resourcesProvider) {
        return Theme.getColor(Theme.key_avatar_actionBarSelectorBlue, resourcesProvider);
    }

    public static int getIconColorForId(long id, Theme.ResourcesProvider resourcesProvider) {
        return Theme.getColor(Theme.key_avatar_actionBarIconBlue, resourcesProvider);
    }

    public static int getProfileColorForId(long id, Theme.ResourcesProvider resourcesProvider) {
        return Theme.getColor(Theme.keys_avatar_background[getColorIndex(id)], resourcesProvider);
    }

    public static int getProfileTextColorForId(long id, Theme.ResourcesProvider resourcesProvider) {
        return Theme.getColor(Theme.key_avatar_subtitleInProfileBlue, resourcesProvider);
    }

    public static int getProfileBackColorForId(long id, Theme.ResourcesProvider resourcesProvider) {
        return Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider);
    }

    public static String colorName(int color) {
        final int[] resIds = new int[] { R.string.ColorRed, R.string.ColorOrange, R.string.ColorViolet, R.string.ColorGreen, R.string.ColorCyan, R.string.ColorBlue, R.string.ColorPink };
        return LocaleController.getString(resIds[color % resIds.length]);
    }

    public static int getNameColorNameForId(long id) {
        return Theme.keys_avatar_nameInMessage[getColorIndex(id)];
    }

    public void setInfo(TLRPC.User user) {
        setInfo(UserConfig.selectedAccount, user);
    }

    public void setInfo(int currentAccount, TLRPC.User user) {
        if (user != null) {
            setInfo(user.id, user.first_name, user.last_name, null, user.color != null ? UserObject.getColorId(user) : null, UserObject.getPeerColorForAvatar(currentAccount, user));
            drawDeleted = UserObject.isDeleted(user);
            drawClown = !drawDeleted && isClownPeer(currentAccount, user.id);
        }
    }

    public void setInfo(TLObject object) {
        if (object instanceof TLRPC.User) {
            setInfo((TLRPC.User) object);
        } else if (object instanceof TLRPC.Chat) {
            setInfo((TLRPC.Chat) object);
        } else if (object instanceof TLRPC.ChatInvite) {
            setInfo((TLRPC.ChatInvite) object);
        }
    }

    public void setInfo(int currentAccount, TLObject object) {
        if (object instanceof TLRPC.User) {
            setInfo(currentAccount, (TLRPC.User) object);
        } else if (object instanceof TLRPC.Chat) {
            setInfo(currentAccount, (TLRPC.Chat) object);
        } else if (object instanceof TLRPC.ChatInvite) {
            setInfo(currentAccount, (TLRPC.ChatInvite) object);
        }
    }

    public void setScaleSize(float value) {
        scaleSize = value;
    }

    private void applySolidColor(int colorValue) {
        hasGradient = false;
        hasAdvancedGradient = false;
        color = color2 = colorValue;
    }

    private void applyGradient(int colorValue1, int colorValue2) {
        hasGradient = true;
        hasAdvancedGradient = false;
        color = colorValue1;
        color2 = colorValue2;
    }

    private void applyAvatarColorIndex(int index) {
        applyGradient(
                getThemedColor(Theme.keys_avatar_background[getColorIndex(index)]),
                getThemedColor(Theme.keys_avatar_background2[getColorIndex(index)])
        );
    }

    private void applyAdvancedGradient(int... colors) {
        hasAdvancedGradient = true;
        hasGradient = false;
        if (advancedGradient == null) {
            advancedGradient = new GradientTools();
        }
        advancedGradient.setColors(colors[0], colors[1], colors[2], colors[3]);
    }

    public void setAvatarType(int value) {
        avatarType = value;
        drawClown = false;
        rotate45Background = false;
        switch (avatarType) {
            case AVATAR_TYPE_REGISTER:
                applySolidColor(Theme.getColor(Theme.key_chats_actionBackground));
                break;
            case AVATAR_TYPE_ARCHIVED:
                applySolidColor(getThemedColor(Theme.key_avatar_backgroundArchivedHidden));
                break;
            case AVATAR_TYPE_SUGGESTION:
            case AVATAR_TYPE_REPLIES:
            case AVATAR_TYPE_SAVED:
            case AVATAR_TYPE_OTHER_CHATS:
                applyGradient(getThemedColor(Theme.key_avatar_backgroundSaved), getThemedColor(Theme.key_avatar_background2Saved));
                break;
            case AVATAR_TYPE_STORY:
                rotate45Background = true;
                applyGradient(getThemedColor(Theme.key_stories_circle1), getThemedColor(Theme.key_stories_circle2));
                break;
            case AVATAR_TYPE_SHARES:
            case AVATAR_TYPE_FILTER_CONTACTS:
            case AVATAR_TYPE_FILTER_READ:
            case AVATAR_TYPE_COUNTRY:
                applyAvatarColorIndex(5);
                break;
            case AVATAR_TYPE_PREMIUM:
                applyAvatarColorIndex(2);
                break;
            case AVATAR_TYPE_STARS:
            case AVATAR_TYPE_FILTER_CHANNELS:
            case AVATAR_TYPE_NEW_CHATS:
                applyAvatarColorIndex(1);
                break;
            case AVATAR_TYPE_FILTER_NON_CONTACTS:
                applyAvatarColorIndex(4);
                break;
            case AVATAR_TYPE_FILTER_GROUPS:
            case AVATAR_TYPE_EXISTING_CHATS:
                applyAvatarColorIndex(3);
                break;
            case AVATAR_TYPE_FILTER_BOTS:
                applyAvatarColorIndex(0);
                break;
            case AVATAR_TYPE_FILTER_MUTED:
                applyAvatarColorIndex(6);
                break;
            case AVATAR_TYPE_ANONYMOUS:
                applyAdvancedGradient(0xFF837CFF, 0xFFB063FF, 0xFFFF72A9, 0xFFE269FF);
                break;
            case AVATAR_TYPE_MY_NOTES:
                applyAdvancedGradient(0xFF4D8DFF, 0xFF2BBFFF, 0xFF20E2CD, 0xFF0EE1F1);
                break;
            default:
                applyAvatarColorIndex(4);
                break;
        }
        needApplyColorAccent = avatarType != AVATAR_TYPE_ARCHIVED && avatarType != AVATAR_TYPE_SAVED && avatarType != AVATAR_TYPE_STORY && avatarType != AVATAR_TYPE_ANONYMOUS && avatarType != AVATAR_TYPE_SUGGESTION && avatarType != AVATAR_TYPE_REPLIES && avatarType != AVATAR_TYPE_OTHER_CHATS;
    }

    public void setArchivedAvatarHiddenProgress(float progress) {
        archivedAvatarProgress = progress;
    }

    public int getAvatarType() {
        return avatarType;
    }

    public void setInfo(TLRPC.Chat chat) {
        setInfo(UserConfig.selectedAccount, chat);
    }
    public void setInfo(int currentAccount, TLRPC.Chat chat) {
        if (chat != null) {
            setInfo(chat.id, chat.title, null, null, chat != null && chat.color != null ? ChatObject.getColorId(chat) : null, ChatObject.getPeerColorForAvatar(currentAccount, chat));
            drawClown = false;
        }
    }

    public void setInfo(TLRPC.ChatInvite chat) {
        setInfo(UserConfig.selectedAccount, chat);
    }
    public void setInfo(int currentAccount, TLRPC.ChatInvite chat) {
        if (chat != null) {
            setInfo(0, chat.title, null, null, chat.chat != null && chat.chat.color != null ? ChatObject.getColorId(chat.chat) : null, ChatObject.getPeerColorForAvatar(currentAccount, chat.chat));
            drawClown = false;
        }
    }

    public void setColor(int value) {
        hasGradient = false;
        hasAdvancedGradient = false;
        color = color2 = value;
        needApplyColorAccent = false;
    }

    public void setColor(int value, int value2) {
        hasGradient = true;
        hasAdvancedGradient = false;
        color = value;
        color2 = value2;
        needApplyColorAccent = false;
    }

    public void setTextSize(int size) {
        namePaint.setTextSize(size);
    }

    public void setInfo(long id, String firstName, String lastName) {
        setInfo(id, firstName, lastName, null, null, null);
    }

    public int getColor() {
        return needApplyColorAccent ? Theme.changeColorAccent(color) : color;
    }

    public int getColor2() {
        return needApplyColorAccent ? Theme.changeColorAccent(color2) : color2;
    }

    private static String takeFirstCharacter(String text) {
        ArrayList<Emoji.EmojiSpanRange> ranges = Emoji.parseEmojis(text);
        if (ranges != null && !ranges.isEmpty() && ranges.get(0).start == 0) {
            return text.substring(0, ranges.get(0).end);
        }
        return text.substring(0, text.offsetByCodePoints(0, Math.min(text.codePointCount(0, text.length()), 1)));
    }

    public void setInfo(long id) {
        invalidateTextLayout = true;
        hasGradient = true;
        hasAdvancedGradient = false;
        color = getThemedColor(Theme.keys_avatar_background[getColorIndex(id)]);
        color2 = getThemedColor(Theme.keys_avatar_background2[getColorIndex(id)]);
        avatarType = AVATAR_TYPE_NORMAL;
        drawDeleted = false;
        getAvatarSymbols("", "", "", stringBuilder);
    }

    public void setInfo(long id, String firstName, String lastName, String custom) {
        setInfo(id, firstName, lastName, custom, null, null);
    }

    public void setInfo(long id, String firstName, String lastName, String custom, Integer customColor, MessagesController.PeerColor profileColor) {
        setInfo(id, firstName, lastName, custom, customColor, profileColor, false);
    }

    public void setInfo(long id, String firstName, String lastName, String custom, Integer customColor, MessagesController.PeerColor profileColor, boolean advancedGradient) {
        invalidateTextLayout = true;
        if (advancedGradient) {
            hasGradient = false;
            hasAdvancedGradient = true;
            if (this.advancedGradient == null) {
                this.advancedGradient = new GradientTools();
            }
        } else {
            hasGradient = true;
            hasAdvancedGradient = false;
        }

        if (profileColor != null) {
            if (advancedGradient) {
                int[] gradient = advancedGradients[getPeerColorIndex(profileColor.getAvatarColor1())];
                this.advancedGradient.setColors(gradient[0], gradient[1], gradient[2], gradient[3]);
            } else {
                color = profileColor.getAvatarColor1();
                color2 = profileColor.getAvatarColor2();
            }
        } else if (customColor != null) {
            setPeerColor(customColor);
        } else {
            if (advancedGradient) {
                int[] gradient = advancedGradients[getColorIndex(id)];
                this.advancedGradient.setColors(gradient[0], gradient[1], gradient[2], gradient[3]);
            } else {
                color = getThemedColor(Theme.keys_avatar_background[getColorIndex(id)]);
                color2 = getThemedColor(Theme.keys_avatar_background2[getColorIndex(id)]);
            }
        }
        needApplyColorAccent = id == COLOR_ACCENT_BLUE_ID; // Tinting manually set blue color


        avatarType = AVATAR_TYPE_NORMAL;
        drawDeleted = false;
        drawClown = isClownPeer(UserConfig.selectedAccount, id);

        if (firstName == null || firstName.length() == 0) {
            firstName = lastName;
            lastName = null;
        }

        getAvatarSymbols(firstName, lastName, custom, stringBuilder);
    }

    public void setPeerColor(int id) {
        if (advancedGradient != null) {
            hasGradient = false;
            hasAdvancedGradient = true;
        } else {
            hasGradient = true;
            hasAdvancedGradient = false;
        }
        if (id >= 14) {
            MessagesController messagesController = MessagesController.getInstance(UserConfig.selectedAccount);
            if (messagesController != null && messagesController.peerColors != null && messagesController.peerColors.getColor(id) != null) {
                final int peerColor = messagesController.peerColors.getColor(id).getColor1();
                if (advancedGradient != null) {
                    int[] gradient = advancedGradients[getPeerColorIndex(peerColor)];
                    this.advancedGradient.setColors(gradient[0], gradient[1], gradient[2], gradient[3]);
                } else {
                    color = getThemedColor(Theme.keys_avatar_background[getPeerColorIndex(peerColor)]);
                    color2 = getThemedColor(Theme.keys_avatar_background2[getPeerColorIndex(peerColor)]);
                }
            } else {
                if (advancedGradient != null) {
                    int[] gradient = advancedGradients[getColorIndex(id)];
                    this.advancedGradient.setColors(gradient[0], gradient[1], gradient[2], gradient[3]);
                } else {
                    color = getThemedColor(Theme.keys_avatar_background[getColorIndex(id)]);
                    color2 = getThemedColor(Theme.keys_avatar_background2[getColorIndex(id)]);
                }
            }
        } else {
            if (advancedGradient != null) {
                int[] gradient = advancedGradients[getColorIndex(id)];
                this.advancedGradient.setColors(gradient[0], gradient[1], gradient[2], gradient[3]);
            } else {
                color = getThemedColor(Theme.keys_avatar_background[getColorIndex(id)]);
                color2 = getThemedColor(Theme.keys_avatar_background2[getColorIndex(id)]);
            }
        }
    }

    public void setText(String text) {
        invalidateTextLayout = true;
        avatarType = AVATAR_TYPE_NORMAL;
        drawDeleted = false;
        getAvatarSymbols(text, null, null, stringBuilder);
    }

    public static void getAvatarSymbols(String firstName, String lastName, String custom, StringBuilder result) {
        result.setLength(0);
        if (custom != null) {
            result.append(custom);
        } else {
            if (firstName != null && firstName.length() > 0) {
                result.append(takeFirstCharacter(firstName));
            }
            if (lastName != null && lastName.length() > 0) {
                String lastNameLastWord = lastName;
                int index;
                if ((index = lastNameLastWord.lastIndexOf(' ')) >= 0) {
                    lastNameLastWord = lastNameLastWord.substring(index + 1);
                }
                if (Build.VERSION.SDK_INT > 17) {
                    result.append("\u200C");
                }
                result.append(takeFirstCharacter(lastNameLastWord));
            } else if (firstName != null && firstName.length() > 0) {
                for (int a = firstName.length() - 1; a >= 0; a--) {
                    if (firstName.charAt(a) == ' ') {
                        if (a != firstName.length() - 1 && firstName.charAt(a + 1) != ' ') {
                            if (Build.VERSION.SDK_INT > 17) {
                                result.append("\u200C");
                            }
                            result.append(takeFirstCharacter(firstName.substring(a + 1)));
                            break;
                        }
                    }
                }
            }
        }
    }

    private Drawable customIconDrawable;
    private int iconTx, iconTy;
    public void setCustomIcon(Drawable drawable) {
        customIconDrawable = drawable;
    }

    public void setIconTranslation(int tx, int ty) {
        this.iconTx = tx;
        this.iconTy = ty;
    }

    public Drawable getCustomIcon() {
        return customIconDrawable;
    }

    private Drawable getTypeDrawable(int type) {
        if (customIconDrawable != null) {
            return customIconDrawable;
        }
        int index;
        switch (type) {
            case AVATAR_TYPE_SAVED: index = 0; break;
            case AVATAR_TYPE_FILTER_CONTACTS: index = 2; break;
            case AVATAR_TYPE_FILTER_NON_CONTACTS: index = 3; break;
            case AVATAR_TYPE_FILTER_GROUPS: index = 4; break;
            case AVATAR_TYPE_FILTER_CHANNELS: index = 5; break;
            case AVATAR_TYPE_FILTER_BOTS: index = 6; break;
            case AVATAR_TYPE_FILTER_MUTED: index = 7; break;
            case AVATAR_TYPE_FILTER_READ: index = 8; break;
            case AVATAR_TYPE_SHARES: index = 10; break;
            case AVATAR_TYPE_REPLIES: index = 11; break;
            case AVATAR_TYPE_OTHER_CHATS: index = 12; break;
            case AVATAR_TYPE_CLOSE_FRIENDS: index = 13; break;
            case AVATAR_TYPE_GIFT: index = 14; break;
            case AVATAR_TYPE_TO_BE_DISTRIBUTED: index = 15; break;
            case AVATAR_TYPE_UNCLAIMED: index = 16; break;
            case AVATAR_TYPE_STORY: index = 17; break;
            case AVATAR_TYPE_ANONYMOUS: index = 18; break;
            case AVATAR_TYPE_MY_NOTES: index = 19; break;
            case AVATAR_TYPE_NEW_CHATS: index = 20; break;
            case AVATAR_TYPE_EXISTING_CHATS: index = 21; break;
            case AVATAR_TYPE_PREMIUM: index = 22; break;
            case AVATAR_TYPE_STARS: index = 23; break;
            case AVATAR_TYPE_SUGGESTION: index = 24; break;
            default: index = 9; break;
        }
        return Theme.avatarDrawables != null && index < Theme.avatarDrawables.length ? Theme.avatarDrawables[index] : null;
    }

    private Paint prepareBackgroundPaint(Rect bounds, int size) {
        Paint backgroundPaint = localBackgroundPaint;
        if (hasAdvancedGradient && advancedGradient != null) {
            if (lastAdvancedBounds == null || !lastAdvancedBounds.equals(bounds)) {
                if (lastAdvancedBounds == null) {
                    lastAdvancedBounds = new Rect(bounds);
                } else {
                    lastAdvancedBounds.set(bounds);
                }
                advancedGradient.setBounds(bounds.left, bounds.top, bounds.left + size, bounds.top + size);
            }
            backgroundPaint = advancedGradient.paint;
        } else if (hasGradient) {
            int c1 = ColorUtils.setAlphaComponent(getColor(), alpha);
            int c2 = ColorUtils.setAlphaComponent(getColor2(), alpha);
            if (gradient == null || gradientBottom != bounds.height() || gradientColor1 != c1 || gradientColor2 != c2) {
                gradient = new LinearGradient(0, 0, 0, gradientBottom = bounds.height(), gradientColor1 = c1, gradientColor2 = c2, Shader.TileMode.CLAMP);
            }
            backgroundPaint.setShader(gradient);
            backgroundPaint.setAlpha(alpha);
        } else {
            backgroundPaint.setShader(null);
            backgroundPaint.setColor(ColorUtils.setAlphaComponent(getColor(), alpha));
        }
        return backgroundPaint;
    }

    private void drawBackground(Canvas canvas, int size, Paint backgroundPaint) {
        if (rotate45Background) {
            canvas.save();
            canvas.rotate(-45, size / 2.0f, size / 2.0f);
        }
        int r = roundRadius;
        if (r <= 0 && NyaConfig.INSTANCE.getShowSquareAvatar().Bool()) {
            r = (int) (size * SQUARE_AVATAR_RADIUS_FACTOR);
        }
        if (r > 0) {
            AndroidUtilities.rectTmp.set(0, 0, size, size);
            canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, backgroundPaint);
        } else {
            canvas.drawCircle(size / 2.0f, size / 2.0f, size / 2.0f, backgroundPaint);
        }
        if (rotate45Background) {
            canvas.restore();
        }
    }

    private void drawArchived(Canvas canvas, int size, Paint backgroundPaint) {
        if (archivedAvatarProgress != 0) {
            backgroundPaint.setColor(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_avatar_backgroundArchived), alpha));
            canvas.drawCircle(size / 2.0f, size / 2.0f, size / 2.0f * archivedAvatarProgress, backgroundPaint);
            if (Theme.dialogs_archiveAvatarDrawableRecolored) {
                Theme.dialogs_archiveAvatarDrawable.beginApplyLayerColors();
                Theme.dialogs_archiveAvatarDrawable.setLayerColor("Arrow1", Theme.getNonAnimatedColor(Theme.key_avatar_backgroundArchived));
                Theme.dialogs_archiveAvatarDrawable.setLayerColor("Arrow2", Theme.getNonAnimatedColor(Theme.key_avatar_backgroundArchived));
                Theme.dialogs_archiveAvatarDrawable.commitApplyLayerColors();
                Theme.dialogs_archiveAvatarDrawableRecolored = false;
            }
        } else {
            if (!Theme.dialogs_archiveAvatarDrawableRecolored) {
                Theme.dialogs_archiveAvatarDrawable.beginApplyLayerColors();
                Theme.dialogs_archiveAvatarDrawable.setLayerColor("Arrow1", color);
                Theme.dialogs_archiveAvatarDrawable.setLayerColor("Arrow2", color);
                Theme.dialogs_archiveAvatarDrawable.commitApplyLayerColors();
                Theme.dialogs_archiveAvatarDrawableRecolored = true;
            }
        }
        int w = Theme.dialogs_archiveAvatarDrawable.getIntrinsicWidth();
        int h = Theme.dialogs_archiveAvatarDrawable.getIntrinsicHeight();
        int x = (size - w) / 2;
        int y = (size - h) / 2;
        canvas.save();
        Theme.dialogs_archiveAvatarDrawable.setBounds(x, y, x + w, y + h);
        Theme.dialogs_archiveAvatarDrawable.draw(canvas);
        canvas.restore();
    }

    private void drawTypeIcon(Canvas canvas, int size) {
        Drawable drawable = getTypeDrawable(avatarType);
        if (drawable != null) {
            final int w = (int) (drawable.getIntrinsicWidth() * scaleSize);
            final int h = (int) (drawable.getIntrinsicHeight() * scaleSize);
            final int x = (size - w) / 2 + iconTx;
            final int y = (size - h) / 2 + iconTy;
            drawable.setBounds(x, y, x + w, y + h);
            if (alpha != 255) {
                drawable.setAlpha(alpha);
                drawable.draw(canvas);
                drawable.setAlpha(255);
            } else {
                drawable.draw(canvas);
            }
        }
    }

    private void drawClown(Canvas canvas, int size, Paint backgroundPaint) {
        int bgAlpha = backgroundPaint != null ? backgroundPaint.getAlpha() : 255;
        int finalAlpha = (int) ((alpha / 255f) * (bgAlpha / 255f) * 255);
        if (clownDrawable == null) {
            clownDrawable = Emoji.getEmojiDrawable("🤡");
        }
        if (clownDrawable != null) {
            int w = (int) (size * CLOWN_ICON_SCALE);
            int h = (int) (size * CLOWN_ICON_SCALE);
            if (isProfile) {
                w = (int) (w * scaleSize);
                h = (int) (h * scaleSize);
            }
            int x = (size - w) / 2;
            int y = (size - h) / 2;
            clownDrawable.setBounds(x, y, x + w, y + h);
            if (finalAlpha != 255) {
                clownDrawable.setAlpha(finalAlpha);
                clownDrawable.draw(canvas);
                clownDrawable.setAlpha(255);
            } else {
                clownDrawable.draw(canvas);
            }
        } else {
            if (invalidateTextLayout || clownTextLayout == null) {
                try {
                    CharSequence text = Emoji.replaceEmoji("🤡", namePaint.getFontMetricsInt(), true);
                    clownTextLayout = new StaticLayout(text, namePaint, dp(100), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    if (clownTextLayout.getLineCount() > 0) {
                        clownTextLeft = clownTextLayout.getLineLeft(0);
                        clownTextWidth = clownTextLayout.getLineWidth(0);
                        clownTextHeight = clownTextLayout.getLineBottom(0);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            if (clownTextLayout != null) {
                float scale = size / (float) dp(50);
                canvas.save();
                canvas.scale(scale, scale, size / 2f, size / 2f);
                canvas.translate((size - clownTextWidth) / 2 - clownTextLeft, (size - clownTextHeight) / 2);
                if (finalAlpha != 255) {
                    namePaint.setAlpha(finalAlpha);
                }
                clownTextLayout.draw(canvas);
                canvas.restore();
            }
        }
    }

    private void drawDeleted(Canvas canvas, int size) {
        Drawable deletedDrawable = Theme.avatarDrawables[1];
        if (deletedDrawable == null) {
            return;
        }
        int w = deletedDrawable.getIntrinsicWidth();
        int h = deletedDrawable.getIntrinsicHeight();
        if (isProfile) {
            w *= scaleSize;
            h *= scaleSize;
        } else if (w > size - dp(6) || h > size - dp(6)) {
            float scale = size / (float) dp(50);
            w *= scale;
            h *= scale;
        }
        int x = (size - w) / 2;
        int y = (size - h) / 2;
        deletedDrawable.setBounds(x, y, x + w, y + h);
        deletedDrawable.draw(canvas);
    }

    private void drawText(Canvas canvas, int size) {
        if (invalidateTextLayout) {
            invalidateTextLayout = false;
            if (stringBuilder.length() > 0) {
                CharSequence text = stringBuilder.toString().toUpperCase();
                text = Emoji.replaceEmoji(text, namePaint.getFontMetricsInt(), true);
                if (textLayout == null || !TextUtils.equals(text, textLayout.getText())) {
                    try {
                        textLayout = new StaticLayout(text, namePaint, dp(100), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                        if (textLayout.getLineCount() > 0) {
                            textLeft = textLayout.getLineLeft(0);
                            textWidth = textLayout.getLineWidth(0);
                            textHeight = textLayout.getLineBottom(0);
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            } else {
                textLayout = null;
            }
        }
        if (textLayout != null) {
            float scale = size / (float) dp(50);
            canvas.scale(scale, scale, size / 2f, size / 2f);
            canvas.translate((size - textWidth) / 2 - textLeft, (size - textHeight) / 2);
            textLayout.draw(canvas);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds == null) {
            return;
        }
        int size = bounds.width();
        namePaint.setColor(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_avatar_text), alpha));
        Paint backgroundPaint = prepareBackgroundPaint(bounds, size);

        canvas.save();
        canvas.translate(bounds.left, bounds.top);

        if (drawAvatarBackground) {
            drawBackground(canvas, size, backgroundPaint);
        }

        if (avatarType == AVATAR_TYPE_ARCHIVED) {
            drawArchived(canvas, size, backgroundPaint);
        } else if (drawDeleted && Theme.avatarDrawables[1] != null) {
            drawDeleted(canvas, size);
        } else if (drawClown) {
            drawClown(canvas, size, backgroundPaint);
        } else if (avatarType != 0 || customIconDrawable != null) {
            drawTypeIcon(canvas, size);
        } else {
            drawText(canvas, size);
        }

        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }

    @Override
    public void setColorFilter(ColorFilter cf) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return 0;
    }

    @Override
    public int getIntrinsicHeight() {
        return 0;
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public void setRoundRadius(int roundRadius) {
        if (roundRadius > 0 && NyaConfig.INSTANCE.getShowSquareAvatar().Bool()) {
            this.roundRadius = Math.max(AndroidUtilities.dp(4), Math.round(roundRadius * 0.45f));
        } else {
            this.roundRadius = roundRadius;
        }
    }
}
