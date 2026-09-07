package top.nkbe.niagram.settings;

import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.LaunchActivity.getLastFragment;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.radolyn.ayugram.utils.AyuState;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;

import java.util.Locale;

import top.nkbe.niagram.config.ConfigItem;
import top.nkbe.niagram.ui.cells.HeaderCell;
import top.nkbe.niagram.config.NyaConfig;

public class GhostModeActivity extends BaseNekoSettingsActivity {

    private int ghostEssentialsHeaderRow;
    private int ghostModeToggleRow;

    private int sendReadMessagePacketsRow;
    private int sendReadStoriesPacketsRow;
    private int sendOnlinePacketsRow;
    private int sendUploadProgressRow;
    private int sendOfflinePacketAfterOnlineRow;
    private int ghostModeNoticeRow;
    private int markReadAfterSendRow;
    private int markReadAfterSendNoticeRow;

    private int sendWithoutSoundRow;
    private int sendWithoutSoundNoticeRow;
    private int showGhostInDrawerRow;
    private int showGhostModeStatusRow;
    private boolean ghostModeMenuExpanded;

    @Override
    protected void updateRows() {
        super.updateRows();

        ghostEssentialsHeaderRow = addRow();
        ghostModeToggleRow = addRow();
        if (ghostModeMenuExpanded) {
            sendReadMessagePacketsRow = addRow();
            sendReadStoriesPacketsRow = addRow();
            sendOnlinePacketsRow = addRow();
            sendUploadProgressRow = addRow();
            sendOfflinePacketAfterOnlineRow = addRow();
            ghostModeNoticeRow = addRow();
        } else {
            sendReadMessagePacketsRow = -1;
            sendReadStoriesPacketsRow = -1;
            sendOnlinePacketsRow = -1;
            sendUploadProgressRow = -1;
            sendOfflinePacketAfterOnlineRow = -1;
            ghostModeNoticeRow = -1;
        }
        markReadAfterSendRow = addRow();
        markReadAfterSendNoticeRow = addRow();
        sendWithoutSoundRow = addRow();
        sendWithoutSoundNoticeRow = addRow();
        showGhostInDrawerRow = addRow();
        showGhostModeStatusRow = addRow();
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
    }

    private void updateGhostViews() {
        var isActive = NyaConfig.isGhostModeActive();

        listAdapter.notifyItemChanged(ghostModeToggleRow, PARTIAL);
        listAdapter.notifyItemChanged(sendReadMessagePacketsRow, !isActive);
        listAdapter.notifyItemChanged(sendOnlinePacketsRow, !isActive);
        listAdapter.notifyItemChanged(sendUploadProgressRow, !isActive);
        listAdapter.notifyItemChanged(sendReadStoriesPacketsRow, !isActive);
        listAdapter.notifyItemChanged(sendOfflinePacketAfterOnlineRow, isActive);

        NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.mainUserInfoChanged);
    }


    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == ghostModeToggleRow) {
            ghostModeMenuExpanded ^= true;
            updateRows();
            listAdapter.notifyItemChanged(ghostModeToggleRow, PARTIAL);
            if (ghostModeMenuExpanded) {
                listAdapter.notifyItemRangeInserted(ghostModeToggleRow + 1, 6);
            } else {
                listAdapter.notifyItemRangeRemoved(ghostModeToggleRow + 1, 6);
            }
        } else if (position == sendReadMessagePacketsRow) {
            if (!view.isEnabled()) return;
            NyaConfig.sendReadMessagePackets.toggleConfigBool();
            ((CheckBoxCell) view).setChecked(NyaConfig.sendReadMessagePackets.Bool(), true);
            AyuState.setAllowReadPacket(false, -1);
            updateGhostViews();
        } else if (position == sendReadStoriesPacketsRow) {
            if (!view.isEnabled()) return;
            NyaConfig.sendReadStoriesPackets.toggleConfigBool();
            ((CheckBoxCell) view).setChecked(NyaConfig.sendReadStoriesPackets.Bool(), true);
            updateGhostViews();
        } else if (position == sendOnlinePacketsRow) {
            if (!view.isEnabled()) return;
            NyaConfig.sendOnlinePackets.toggleConfigBool();
            ((CheckBoxCell) view).setChecked(NyaConfig.sendOnlinePackets.Bool(), true);
            updateGhostViews();
        } else if (position == sendUploadProgressRow) {
            if (!view.isEnabled()) return;
            NyaConfig.sendUploadProgress.toggleConfigBool();
            ((CheckBoxCell) view).setChecked(NyaConfig.sendUploadProgress.Bool(), true);
            updateGhostViews();
        } else if (position == sendOfflinePacketAfterOnlineRow) {
            if (!view.isEnabled()) return;
            NyaConfig.sendOfflinePacketAfterOnline.toggleConfigBool();
            ((CheckBoxCell) view).setChecked(NyaConfig.sendOfflinePacketAfterOnline.Bool(), true);
            updateGhostViews();
        } else if (position == markReadAfterSendRow) {
            NyaConfig.markReadAfterSend.toggleConfigBool();
            ((TextCheckCell) view).setChecked(NyaConfig.markReadAfterSend.Bool());
            AyuState.setAllowReadPacket(false, -1);
        } else if (position == sendWithoutSoundRow) {
            NyaConfig.INSTANCE.getSilentMessageByDefault().toggleConfigBool();
            ((TextCheckCell) view).setChecked(NyaConfig.INSTANCE.getSilentMessageByDefault().Bool());
        } else if (position == showGhostInDrawerRow) {
            NyaConfig.showGhostInDrawer.toggleConfigBool();
            ((TextCheckCell) view).setChecked(NyaConfig.showGhostInDrawer.Bool());
            NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.mainUserInfoChanged);
        } else if (position == showGhostModeStatusRow) {
            NyaConfig.showGhostModeStatus.toggleConfigBool();
            ((TextCheckCell) view).setChecked(NyaConfig.showGhostModeStatus.Bool());
            NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.mainUserInfoChanged);
        }
    }

    @Override
    protected boolean onItemLongClick(View view, int position, float x, float y) {
        ConfigItem targetItem = null;
        ConfigItem lockedItem = null;

        if (position == sendReadMessagePacketsRow) {
            targetItem = NyaConfig.sendReadMessagePackets;
            lockedItem = NyaConfig.sendReadMessagePacketsLocked;
        } else if (position == sendReadStoriesPacketsRow) {
            targetItem = NyaConfig.sendReadStoriesPackets;
            lockedItem = NyaConfig.sendReadStoriesPacketsLocked;
        } else if (position == sendOnlinePacketsRow) {
            targetItem = NyaConfig.sendOnlinePackets;
            lockedItem = NyaConfig.sendOnlinePacketsLocked;
        } else if (position == sendUploadProgressRow) {
            targetItem = NyaConfig.sendUploadProgress;
            lockedItem = NyaConfig.sendUploadProgressLocked;
        } else if (position == sendOfflinePacketAfterOnlineRow) {
            targetItem = NyaConfig.sendOfflinePacketAfterOnline;
            lockedItem = NyaConfig.sendOfflinePacketAfterOnlineLocked;
        }

        if (lockedItem != null && targetItem != null) {
            boolean currentLocked = lockedItem.Bool();
            if (!currentLocked && getGhostModeLockedCount() >= 4) {
                AndroidUtilities.shakeViewSpring(view, -4);
                return true;
            }
            lockedItem.setConfigBool(!currentLocked);
            view.setEnabled(currentLocked);
            listAdapter.notifyItemChanged(ghostModeToggleRow, PARTIAL);
            return true;
        }
        return super.onItemLongClick(view, position, x, y);
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.GhostMode);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private int getGhostModeSelectedCount() {
        int count = 0;
        if (!NyaConfig.sendReadMessagePackets.Bool()) count++;
        if (!NyaConfig.sendReadStoriesPackets.Bool()) count++;
        if (!NyaConfig.sendOnlinePackets.Bool()) count++;
        if (!NyaConfig.sendUploadProgress.Bool()) count++;
        if (NyaConfig.sendOfflinePacketAfterOnline.Bool()) count++;
        return count;
    }

    private int getGhostModeLockedCount() {
        int count = 0;
        if (NyaConfig.sendReadMessagePacketsLocked.Bool()) count++;
        if (NyaConfig.sendReadStoriesPacketsLocked.Bool()) count++;
        if (NyaConfig.sendOnlinePacketsLocked.Bool()) count++;
        if (NyaConfig.sendUploadProgressLocked.Bool()) count++;
        if (NyaConfig.sendOfflinePacketAfterOnlineLocked.Bool()) count++;
        return count;
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            if (type == TYPE_CHECKBOX2) {
                return holder.itemView.isEnabled();
            }
            return type == TYPE_CHECK || type == TYPE_CHECK2;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean payload) {
            switch (holder.getItemViewType()) {
                case TYPE_SHADOW:
                    holder.itemView.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                case TYPE_CHECK:
                    TextCheckCell textCheckCell = (TextCheckCell) holder.itemView;
                    textCheckCell.setEnabled(true, null);
                    if (position == markReadAfterSendRow) {
                        textCheckCell.setTextAndCheck(getString(R.string.MarkReadAfterSend), NyaConfig.markReadAfterSend.Bool(), true);
                    } else if (position == sendWithoutSoundRow) {
                        textCheckCell.setTextAndCheck(getString(R.string.SilentMessageByDefault), NyaConfig.INSTANCE.getSilentMessageByDefault().Bool(), true);
                    } else if (position == showGhostInDrawerRow) {
                        textCheckCell.setTextAndCheck(getString(R.string.GhostModeInDrawer), NyaConfig.showGhostInDrawer.Bool(), true);
                    } else if (position == showGhostModeStatusRow) {
                        textCheckCell.setTextAndCheck(getString(R.string.GhostModeStatusIndicator), NyaConfig.showGhostModeStatus.Bool(), false);
                    }
                    break;
                case TYPE_HEADER:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == ghostEssentialsHeaderRow) {
                        headerCell.setText(getString(R.string.GhostEssentialsHeader));
                    }
                    break;
                case TYPE_INFO_PRIVACY:
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    if (position == ghostModeNoticeRow) {
                        cell.setText(getString(R.string.GhostModeNotice));
                    } else if (position == markReadAfterSendNoticeRow) {
                        cell.setText(getString(R.string.MarkReadAfterSendNotice));
                    } else if (position == sendWithoutSoundNoticeRow) {
                        cell.setText(getString(R.string.SendWithoutSoundRowNotice));
                    }
                    break;
                case TYPE_CHECK2:
                    TextCheckCell2 checkCell = (TextCheckCell2) holder.itemView;
                    if (position == ghostModeToggleRow) {
                        int selectedCount = getGhostModeSelectedCount();
                        boolean isActive = NyaConfig.isGhostModeActive();
                        checkCell.setTextAndCheck(getString(R.string.GhostMode), isActive, true, true);
                        checkCell.setCollapseArrow(String.format(Locale.US, "%d/5", selectedCount), !ghostModeMenuExpanded, () -> {
                            NyaConfig.toggleGhostMode();
                            String msg = isActive
                                    ? getString(R.string.GhostModeDisabled)
                                    : getString(R.string.GhostModeEnabled);
                            BulletinFactory.of(getLastFragment()).createSuccessBulletin(msg).show();
                            updateGhostViews();
                        });
                    }
                    checkCell.getCheckBox().setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
                    checkCell.getCheckBox().setDrawIconType(0);
                    break;
                case TYPE_CHECKBOX2:
                    CheckBoxCell checkBoxCell = (CheckBoxCell) holder.itemView;
                    ConfigItem item = null;
                    ConfigItem lockedItem = null;
                    boolean checkValue = false;
                    String title = "";

                    if (position == sendReadMessagePacketsRow) {
                        item = NyaConfig.sendReadMessagePackets;
                        lockedItem = NyaConfig.sendReadMessagePacketsLocked;
                        checkValue = !item.Bool();
                        title = getString(R.string.DontSendReadMessagePackets);
                    } else if (position == sendReadStoriesPacketsRow) {
                        item = NyaConfig.sendReadStoriesPackets;
                        lockedItem = NyaConfig.sendReadStoriesPacketsLocked;
                        checkValue = !item.Bool();
                        title = getString(R.string.DontReadStoriesPackets);
                    } else if (position == sendOnlinePacketsRow) {
                        item = NyaConfig.sendOnlinePackets;
                        lockedItem = NyaConfig.sendOnlinePacketsLocked;
                        checkValue = !item.Bool();
                        title = getString(R.string.DontSendOnlinePackets);
                    } else if (position == sendUploadProgressRow) {
                        item = NyaConfig.sendUploadProgress;
                        lockedItem = NyaConfig.sendUploadProgressLocked;
                        checkValue = !item.Bool();
                        title = getString(R.string.DontSendUploadProgress);
                    } else if (position == sendOfflinePacketAfterOnlineRow) {
                        item = NyaConfig.sendOfflinePacketAfterOnline;
                        lockedItem = NyaConfig.sendOfflinePacketAfterOnlineLocked;
                        checkValue = item.Bool();
                        title = getString(R.string.SendOfflinePacketAfterOnline);
                    }

                    if (item != null && lockedItem != null) {
                        boolean isLocked = lockedItem.Bool();
                        checkBoxCell.setText(title, "", checkValue, true, true);
                        checkBoxCell.setEnabled(!isLocked);
                    }
                    checkBoxCell.setPad(1);
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == ghostEssentialsHeaderRow) {
                return TYPE_HEADER;
            } else if (position == ghostModeNoticeRow || position == markReadAfterSendNoticeRow || position == sendWithoutSoundNoticeRow) {
                return TYPE_INFO_PRIVACY;
            } else if (position == ghostModeToggleRow) {
                return TYPE_CHECK2;
            } else if (position >= sendReadMessagePacketsRow && position <= sendOfflinePacketAfterOnlineRow) {
                return TYPE_CHECKBOX2;
            }
            return TYPE_CHECK;
        }
    }
}
