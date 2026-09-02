package top.nkbe.niagram.filters;

import android.text.TextUtils;
import android.util.SparseBooleanArray;

import androidx.core.util.Consumer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

import top.nkbe.niagram.NekoConfig;

public class ReactionFilter {

    public record ReactionCountResult(ArrayList<TLRPC.ReactionCount> counts, int totalCount, boolean hasReactionsFromOtherUsers) {
    }

    public static boolean shouldFilter(int currentAccount, TLRPC.Message message) {
        return message != null && shouldFilter(currentAccount, MessageObject.getDialogId(message));
    }

    public static boolean shouldFilter(int currentAccount, long dialogId) {
        if (!NekoConfig.ignoreBlocked.Bool() || dialogId >= 0L) {
            return false;
        }
        int account = currentAccount >= 0 ? currentAccount : UserConfig.selectedAccount;
        return ChatObject.isMegagroup(MessagesController.getInstance(account).getChat(-dialogId));
    }

    public static boolean isBlockedPeer(int currentAccount, long dialogId, long peerId) {
        if (!shouldFilter(currentAccount, dialogId) || peerId == 0L) {
            return false;
        }
        int account = currentAccount >= 0 ? currentAccount : UserConfig.selectedAccount;
        if (MessagesController.getInstance(account).blockePeers.indexOfKey(peerId) >= 0) {
            return true;
        }
        if (peerId > 0L) {
            return AyuFilter.isCustomFilteredPeer(peerId);
        }
        return AyuFilter.isBlockedChannel(peerId);
    }

    public static boolean hasUnreadReactions(int currentAccount, TLRPC.Message message) {
        return message != null && hasUnreadReactions(currentAccount, MessageObject.getDialogId(message), message.reactions);
    }

    public static boolean hasUnreadReactions(int currentAccount, long dialogId, TLRPC.TL_messageReactions reactions) {
        if (reactions == null || reactions.recent_reactions == null) {
            return false;
        }
        if (!shouldFilter(currentAccount, dialogId)) {
            for (int i = 0; i < reactions.recent_reactions.size(); i++) {
                TLRPC.MessagePeerReaction reaction = reactions.recent_reactions.get(i);
                if (reaction != null && reaction.unread) {
                    return true;
                }
            }
            return false;
        }
        for (int i = 0; i < reactions.recent_reactions.size(); i++) {
            TLRPC.MessagePeerReaction reaction = reactions.recent_reactions.get(i);
            if (reaction != null && reaction.unread && !isBlockedPeer(currentAccount, dialogId, MessageObject.getPeerId(reaction.peer_id))) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasReactions(int currentAccount, TLRPC.Message message) {
        return message != null && hasReactions(currentAccount, MessageObject.getDialogId(message), message.reactions);
    }

    public static boolean hasReactions(int currentAccount, long dialogId, TLRPC.TL_messageReactions reactions) {
        if (reactions == null || reactions.results == null) {
            return false;
        }
        if (!shouldFilter(currentAccount, dialogId)) {
            return !reactions.results.isEmpty();
        }
        for (int i = 0; i < reactions.results.size(); i++) {
            if (getFilteredReactionCount(currentAccount, dialogId, reactions, reactions.results.get(i)) > 0) {
                return true;
            }
        }
        return false;
    }

    public static TLRPC.MessagePeerReaction getFirstReaction(int currentAccount, long dialogId, TLRPC.TL_messageReactions reactions) {
        if (reactions == null || reactions.recent_reactions == null) {
            return null;
        }
        if (!shouldFilter(currentAccount, dialogId)) {
            return reactions.recent_reactions.get(0);
        }
        for (int i = 0; i < reactions.recent_reactions.size(); i++) {
            TLRPC.MessagePeerReaction reaction = reactions.recent_reactions.get(i);
            if (reaction != null && !isBlockedPeer(currentAccount, dialogId, MessageObject.getPeerId(reaction.peer_id))) {
                return reaction;
            }
        }
        return null;
    }

    public static ArrayList<TLRPC.ReactionCount> getReactionCounts(int currentAccount, long dialogId, TLRPC.TL_messageReactions reactions) {
        if (reactions == null || reactions.results == null) {
            return new ArrayList<>();
        }
        if (!shouldFilter(currentAccount, dialogId)) {
            return reactions.results;
        }
        boolean changed = false;
        ArrayList<TLRPC.ReactionCount> visible = new ArrayList<>(reactions.results.size());
        for (int i = 0; i < reactions.results.size(); i++) {
            TLRPC.ReactionCount reactionCount = reactions.results.get(i);
            int visibleCount = getFilteredReactionCount(currentAccount, dialogId, reactions, reactionCount);
            if (visibleCount <= 0 && (reactionCount == null || !reactionCount.chosen)) {
                changed = true;
                continue;
            }
            if (reactionCount != null && visibleCount == reactionCount.count) {
                visible.add(reactionCount);
            } else {
                changed = true;
                visible.add(copyReactionCountWithCount(reactionCount, visibleCount));
            }
        }
        return changed ? visible : reactions.results;
    }

    public static ReactionCountResult getReactionCountResult(int currentAccount, long dialogId, TLRPC.TL_messageReactions reactions) {
        if (reactions == null || reactions.results == null) {
            return new ReactionCountResult(new ArrayList<>(), 0, false);
        }
        if (!shouldFilter(currentAccount, dialogId)) {
            boolean hasReactionsFromOtherUsers = false;
            int count = 0;
            for (TLRPC.ReactionCount r : reactions.results) {
                if (r == null) {
                    continue;
                }
                count += r.count;
                if (r.count > 1 || !r.chosen) {
                    hasReactionsFromOtherUsers = true;
                }
            }
            return new ReactionCountResult(reactions.results, count, hasReactionsFromOtherUsers);
        }
        boolean changed = false;
        int totalCount = 0;
        boolean hasReactionsFromOtherUsers = false;
        ArrayList<TLRPC.ReactionCount> visible = new ArrayList<>(reactions.results.size());
        for (int i = 0; i < reactions.results.size(); i++) {
            TLRPC.ReactionCount reactionCount = reactions.results.get(i);
            int visibleCount = getFilteredReactionCount(currentAccount, dialogId, reactions, reactionCount);
            totalCount += visibleCount;
            if (reactionCount != null && visibleCount > 0 && (visibleCount > 1 || !reactionCount.chosen)) {
                hasReactionsFromOtherUsers = true;
            }
            if (visibleCount <= 0 && (reactionCount == null || !reactionCount.chosen)) {
                changed = true;
                continue;
            }
            if (reactionCount != null && visibleCount == reactionCount.count) {
                visible.add(reactionCount);
            } else {
                changed = true;
                visible.add(copyReactionCountWithCount(reactionCount, visibleCount));
            }
        }
        return new ReactionCountResult(changed ? visible : reactions.results, totalCount, hasReactionsFromOtherUsers);
    }

    public static int getReactionCount(int currentAccount, long dialogId, TLRPC.TL_messageReactions reactions, TLRPC.ReactionCount reactionCount) {
        if (reactionCount == null) {
            return 0;
        }
        int count = reactionCount.count;
        if (!shouldFilter(currentAccount, dialogId)) {
            return count;
        }
        return getFilteredReactionCount(currentAccount, dialogId, reactions, reactionCount);
    }

    private static int getFilteredReactionCount(int currentAccount, long dialogId, TLRPC.TL_messageReactions reactions, TLRPC.ReactionCount reactionCount) {
        if (reactionCount == null) {
            return 0;
        }
        int count = reactionCount.count;
        if (reactions == null || reactions.recent_reactions == null || reactions.recent_reactions.isEmpty()) {
            return count;
        }
        for (int i = 0; i < reactions.recent_reactions.size(); i++) {
            TLRPC.MessagePeerReaction reaction = reactions.recent_reactions.get(i);
            if (reaction != null && isBlockedPeer(currentAccount, dialogId, MessageObject.getPeerId(reaction.peer_id)) && reactionsEqual(reaction.reaction, reactionCount.reaction)) {
                count--;
            }
        }
        return Math.max(0, count);
    }

    public static ArrayList<TLRPC.MessagePeerReaction> getPeerReactions(int currentAccount, long dialogId, ArrayList<TLRPC.MessagePeerReaction> reactions) {
        ArrayList<TLRPC.MessagePeerReaction> visibleReactions = new ArrayList<>();
        if (reactions == null) {
            return visibleReactions;
        }
        if (!shouldFilter(currentAccount, dialogId)) {
            return reactions;
        }
        for (int i = 0; i < reactions.size(); i++) {
            TLRPC.MessagePeerReaction reaction = reactions.get(i);
            if (reaction == null || !isBlockedPeer(currentAccount, dialogId, MessageObject.getPeerId(reaction.peer_id))) {
                visibleReactions.add(reaction);
            }
        }
        return visibleReactions;
    }

    public static boolean shouldShowReactionMention(int currentAccount, int reactionMentionCount, MessageObject messageObject) {
        if (reactionMentionCount <= 0) {
            return false;
        }
        if (messageObject == null || messageObject.messageOwner == null) {
            return true;
        }
        long dialogId = MessageObject.getDialogId(messageObject.messageOwner);
        if (!shouldFilter(currentAccount, dialogId)) {
            return true;
        }
        if (messageObject.messageOwner.reactions == null || messageObject.messageOwner.reactions.recent_reactions == null || messageObject.messageOwner.reactions.recent_reactions.isEmpty()) {
            return true;
        }
        boolean hasUnreadReaction = false;
        for (int i = 0; i < messageObject.messageOwner.reactions.recent_reactions.size(); i++) {
            TLRPC.MessagePeerReaction reaction = messageObject.messageOwner.reactions.recent_reactions.get(i);
            if (reaction != null && reaction.unread) {
                hasUnreadReaction = true;
                if (!isBlockedPeer(currentAccount, dialogId, MessageObject.getPeerId(reaction.peer_id))) {
                    return true;
                }
            }
        }
        return !hasUnreadReaction || reactionMentionCount > 1;
    }

    public static void requestNextReactionMention(MessagesController messagesController, int currentAccount, long dialogId, long topicId, int addOffset, Consumer<Integer> callback) {
        requestNextReactionMention(messagesController, currentAccount, dialogId, topicId, addOffset, 0, callback);
    }

    private static void requestNextReactionMention(MessagesController messagesController, int currentAccount, long dialogId, long topicId, int addOffset, int lastVisibleMessageId, Consumer<Integer> callback) {
        TLRPC.TL_messages_getUnreadReactions req = new TLRPC.TL_messages_getUnreadReactions();
        req.peer = messagesController.getInputPeer(dialogId);
        req.limit = 10;
        req.add_offset = addOffset;
        if (messagesController.isMonoForum(dialogId) && topicId != 0) {
            req.saved_peer_id = messagesController.getInputPeer(topicId);
            req.flags |= 2;
        }
        ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, error) -> {
            int messageId = lastVisibleMessageId;
            if (error == null && res != null && res.messages != null && !res.messages.isEmpty()) {
                for (int i = 0; i < res.messages.size(); i++) {
                    TLRPC.Message message = res.messages.get(i);
                    if (hasUnreadReactions(currentAccount, message)) {
                        messageId = message.id;
                    }
                }
                int nextAddOffset = addOffset + res.messages.size();
                boolean canLoadMore = res.count > 0 ? nextAddOffset < res.count : res.messages.size() == 10;
                if (canLoadMore) {
                    requestNextReactionMention(messagesController, currentAccount, dialogId, topicId, nextAddOffset, messageId, callback);
                    return;
                }
            }
            int finalMessageId = messageId;
            AndroidUtilities.runOnUIThread(() -> callback.accept(finalMessageId));
        });
    }

    public static int markHiddenUnreadReactionsAsRead(MessagesController messagesController, int currentAccount, long dialogId, long topicId, ArrayList<MessageObject> messages, int reactionsMentionCount) {
        if (reactionsMentionCount <= 0 || !shouldFilter(currentAccount, dialogId)) {
            return reactionsMentionCount;
        }
        ArrayList<MessageObject> hiddenMessages = null;
        for (int i = 0; i < messages.size(); i++) {
            MessageObject messageObject = messages.get(i);
            if (canMarkUnreadReactionsAsHiddenRead(currentAccount, messageObject)) {
                if (hiddenMessages == null) {
                    hiddenMessages = new ArrayList<>();
                }
                hiddenMessages.add(messageObject);
            }
        }
        return markHiddenUnreadReactionMessagesAsRead(messagesController, dialogId, topicId, hiddenMessages, reactionsMentionCount);
    }

    public static int markHiddenUnreadReactionAsRead(MessagesController messagesController, int currentAccount, long dialogId, long topicId, MessageObject messageObject, int reactionsMentionCount) {
        if (reactionsMentionCount <= 0 || !shouldFilter(currentAccount, dialogId)) {
            return reactionsMentionCount;
        }
        if (canMarkUnreadReactionsAsHiddenRead(currentAccount, messageObject)) {
            ArrayList<MessageObject> hiddenMessages = new ArrayList<>(1);
            hiddenMessages.add(messageObject);
            return markHiddenUnreadReactionMessagesAsRead(messagesController, dialogId, topicId, hiddenMessages, reactionsMentionCount);
        }
        return reactionsMentionCount;
    }

    public static boolean markHiddenUnreadReactionsAsRead(MessagesController messagesController, int currentAccount, long dialogId, long topicId, MessageObject messageObject, int reactionsMentionCount, Consumer<Integer> countConsumer) {
        int newCount = markHiddenUnreadReactionAsRead(messagesController, currentAccount, dialogId, topicId, messageObject, reactionsMentionCount);
        countConsumer.accept(newCount);
        return reactionsMentionCount != newCount;
    }

    private static int markHiddenUnreadReactionMessagesAsRead(MessagesController messagesController, long dialogId, long topicId, ArrayList<MessageObject> hiddenMessages, int reactionsMentionCount) {
        if (hiddenMessages == null || hiddenMessages.isEmpty()) {
            return reactionsMentionCount;
        }
        SparseBooleanArray unreadReactions = new SparseBooleanArray();
        for (int i = 0; i < hiddenMessages.size(); i++) {
            unreadReactions.put(hiddenMessages.get(i).getId(), false);
        }
        messagesController.checkUnreadReactions(dialogId, topicId, unreadReactions);
        for (int i = 0; i < hiddenMessages.size(); i++) {
            hiddenMessages.get(i).markReactionsAsRead();
        }
        int newCount = Math.max(0, reactionsMentionCount - hiddenMessages.size());
        if (newCount == 0) {
            messagesController.markReactionsAsRead(dialogId, topicId);
        }
        return newCount;
    }

    private static boolean canMarkUnreadReactionsAsHiddenRead(int currentAccount, MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null || messageObject.messageOwner.reactions == null || messageObject.messageOwner.reactions.recent_reactions == null || messageObject.messageOwner.reactions.recent_reactions.isEmpty()) {
            return false;
        }
        long dialogId = messageObject.getDialogId();
        boolean hasUnreadReaction = false;
        for (int i = 0; i < messageObject.messageOwner.reactions.recent_reactions.size(); i++) {
            TLRPC.MessagePeerReaction reaction = messageObject.messageOwner.reactions.recent_reactions.get(i);
            if (reaction == null || !reaction.unread) {
                continue;
            }
            hasUnreadReaction = true;
            if (!isBlockedPeer(currentAccount, dialogId, MessageObject.getPeerId(reaction.peer_id))) {
                return false;
            }
        }
        return hasUnreadReaction;
    }

    private static TLRPC.ReactionCount copyReactionCountWithCount(TLRPC.ReactionCount reactionCount, int count) {
        TLRPC.TL_reactionCount copy = new TLRPC.TL_reactionCount();
        if (reactionCount == null) {
            copy.count = Math.max(0, count);
            return copy;
        }
        copy.flags = reactionCount.flags;
        copy.chosen_order = reactionCount.chosen_order;
        copy.chosen = reactionCount.chosen;
        copy.lastDrawnPosition = reactionCount.lastDrawnPosition;
        copy.reaction = reactionCount.reaction;
        copy.count = Math.max(0, count);
        return copy;
    }

    private static boolean reactionsEqual(TLRPC.Reaction reaction, TLRPC.Reaction reaction1) {
        if (reaction instanceof TLRPC.TL_reactionEmoji && reaction1 instanceof TLRPC.TL_reactionEmoji) {
            return TextUtils.equals(((TLRPC.TL_reactionEmoji) reaction).emoticon, ((TLRPC.TL_reactionEmoji) reaction1).emoticon);
        }
        if (reaction instanceof TLRPC.TL_reactionCustomEmoji && reaction1 instanceof TLRPC.TL_reactionCustomEmoji) {
            return ((TLRPC.TL_reactionCustomEmoji) reaction).document_id == ((TLRPC.TL_reactionCustomEmoji) reaction1).document_id;
        }
        return reaction instanceof TLRPC.TL_reactionPaid && reaction1 instanceof TLRPC.TL_reactionPaid;
    }
}
