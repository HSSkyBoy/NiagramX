package xyz.nextalone.nagram.helper;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.function.BooleanSupplier;

public class ForwardTextEdit {

    public static MessageObject getEditableMessage(ArrayList<MessageObject> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        long groupId = messages.get(0) != null ? messages.get(0).getGroupIdForUse() : 0;
        boolean album = messages.size() > 1;
        MessageObject captionCarrier = null;
        for (int i = 0; i < messages.size(); i++) {
            MessageObject message = messages.get(i);
            if (message == null || message.messageOwner == null) {
                return null;
            }
            if (album && message.getGroupIdForUse() != groupId) {
                return null;
            }
            if (!isTextCarrier(message)) {
                return null;
            }
            if (captionCarrier == null && message.caption != null) {
                captionCarrier = message;
            }
        }
        if (!album) {
            return messages.get(0);
        }
        if (groupId == 0) {
            return null;
        }
        return captionCarrier != null ? captionCarrier : messages.get(0);
    }

    private static boolean isTextCarrier(MessageObject message) {
        if (message.isPoll() || message.isTodo() || message.isLocation() || message.isLiveLocation() || message.isGame() || message.isInvoice() || message.isStoryMedia() || message.isVoiceOnce() || message.isRoundOnce() || message.isSticker() || message.isAnimatedSticker() || message.isVoice()) {
            return false;
        }
        return message.type == MessageObject.TYPE_TEXT || message.isAnimatedEmoji() || message.isPhoto() || message.isVideo() || message.isRoundVideo() || message.getDocument() != null || message.caption != null;
    }

    public static boolean isTextOnlyMessage(MessageObject message) {
        return message.type == MessageObject.TYPE_TEXT || message.isAnimatedEmoji();
    }

    public static CharSequence getForwardText(MessageObject editableMessage) {
        CharSequence text = ChatActivity.getMessageCaption(editableMessage, null, null);
        if (text == null && isTextOnlyMessage(editableMessage)) {
            text = ChatActivity.getMessageContent(editableMessage, 0, false);
        }
        return text;
    }

    public static boolean hasForwardTextChanged(String newText, MessageObject editableMessage) {
        CharSequence original = getForwardText(editableMessage);
        String originalText = original != null ? original.toString() : "";
        return !originalText.equals(newText);
    }

    public static boolean withEditedText(MessageObject editableMessage, String newText, ArrayList<TLRPC.MessageEntity> entities, BooleanSupplier sendAction) {
        String originalMessage = editableMessage.messageOwner.message;
        ArrayList<TLRPC.MessageEntity> originalEntities = editableMessage.messageOwner.entities;
        CharSequence originalCaption = editableMessage.caption;
        CharSequence originalMessageText = editableMessage.messageText;
        try {
            editableMessage.messageOwner.message = newText;
            editableMessage.messageOwner.entities = entities;
            editableMessage.caption = newText != null && newText.length() > 0 ? newText : null;
            editableMessage.messageText = newText != null ? newText : "";
            return sendAction.getAsBoolean();
        } finally {
            editableMessage.messageOwner.message = originalMessage;
            editableMessage.messageOwner.entities = originalEntities;
            editableMessage.caption = originalCaption;
            editableMessage.messageText = originalMessageText;
        }
    }
}