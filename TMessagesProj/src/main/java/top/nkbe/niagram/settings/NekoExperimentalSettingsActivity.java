package top.nkbe.niagram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BlurredRecyclerView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UndoView;

import java.util.ArrayList;

import kotlin.Unit;
import top.nkbe.niagram.config.CellGroup;
import top.nkbe.niagram.config.cell.AbstractConfigCell;
import top.nkbe.niagram.config.cell.ConfigCellCustom;
import top.nkbe.niagram.config.cell.ConfigCellDivider;
import top.nkbe.niagram.config.cell.ConfigCellHeader;
import top.nkbe.niagram.config.cell.ConfigCellSelectBox;
import top.nkbe.niagram.config.cell.ConfigCellTextCheck;
import top.nkbe.niagram.ui.PopupBuilder;
import top.nkbe.niagram.config.NyaConfig;

@SuppressLint("RtlHardcoded")
@SuppressWarnings("unused")
public class NekoExperimentalSettingsActivity extends BaseNekoXSettingsActivity {

    private ListAdapter listAdapter;

    @Override
    protected RecyclerListView.SelectionAdapter getListAdapter() {
        return listAdapter;
    }

    @Override
    protected CellGroup getCellGroup() {
        return cellGroup;
    }

    @Override
    protected String getSettingsPrefix() {
        return "experimental";
    }

    private AnimatorSet animatorSet;
    private boolean sensitiveCanChange = false;
    private boolean sensitiveEnabled = false;

    private final CellGroup cellGroup = new CellGroup(this);

    // General
    private final AbstractConfigCell headerGeneral = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.General)));
    private final AbstractConfigCell backAnimationStyleRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NyaConfig.INSTANCE.getBackAnimationStyle(),
            Build.VERSION.SDK_INT >= 34 ? new String[]{
                    getString(R.string.BackAnimationClassic),
                    getString(R.string.BackAnimationSpring),
                    getString(R.string.BackAnimationPredictive),
            } : new String[]{
                    getString(R.string.BackAnimationClassic),
                    getString(R.string.BackAnimationSpring),
            }, null));
    private final AbstractConfigCell springAnimationCrossfadeRow = cellGroup.appendCell(new ConfigCellTextCheck(NyaConfig.INSTANCE.getSpringAnimationCrossfade()));
    private final AbstractConfigCell localPremiumRow = cellGroup.appendCell(new ConfigCellTextCheck(NyaConfig.localPremium));
    private final AbstractConfigCell unlimitedPinnedDialogsRow = cellGroup.appendCell(new ConfigCellTextCheck(NyaConfig.unlimitedPinnedDialogs, getString(R.string.UnlimitedPinnedDialogsAbout)));
    private final AbstractConfigCell unlimitedFavedStickersRow = cellGroup.appendCell(new ConfigCellTextCheck(NyaConfig.unlimitedFavedStickers, getString(R.string.UnlimitedFavoredStickersAbout)));
    private final AbstractConfigCell dividerGeneral = cellGroup.appendCell(new ConfigCellDivider());

    // Connections
    private final AbstractConfigCell headerConnection = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Connection)));
    private final AbstractConfigCell autoActivateMainlandProxyRow = cellGroup.appendCell(new ConfigCellTextCheck(NyaConfig.INSTANCE.getAutoActivateMainlandProxy(), getString(R.string.AutoActivateMainlandProxyDesc)));
    private final AbstractConfigCell disableProxyWhenVpnEnabledRow = cellGroup.appendCell(new ConfigCellTextCheck(NyaConfig.INSTANCE.getDisableProxyWhenVpnEnabled(), getString(R.string.DisableProxyWhenVpnEnabledDesc)));
    private final AbstractConfigCell boostUploadRow = cellGroup.appendCell(new ConfigCellTextCheck(NyaConfig.uploadBoost));
    private final AbstractConfigCell enhancedFileLoaderRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NyaConfig.enhancedFileLoader, new String[]{
            getString(R.string.enhancedFileLoaderOff),
            getString(R.string.enhancedFileLoaderBalanced),
            getString(R.string.enhancedFileLoaderExtreme)
    }, new int[]{
            NyaConfig.ENHANCED_LOADER_OFF,
            NyaConfig.ENHANCED_LOADER_BALANCED,
            NyaConfig.ENHANCED_LOADER_EXTREME
    }, null));
    private final AbstractConfigCell dividerConnection = cellGroup.appendCell(new ConfigCellDivider());

    // Media
    private final AbstractConfigCell headerMedia = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.MediaSettings)));
    private final AbstractConfigCell audioEnhanceRow = cellGroup.appendCell(new ConfigCellTextCheck(NyaConfig.INSTANCE.getNoiseSuppressAndVoiceEnhance()));
    private final AbstractConfigCell sendMp4DocumentAsVideoRow = cellGroup.appendCell(new ConfigCellTextCheck(NyaConfig.INSTANCE.getSendMp4DocumentAsVideo()));
    private final AbstractConfigCell enhancedVideoBitrateRow = cellGroup.appendCell(new ConfigCellTextCheck(NyaConfig.INSTANCE.getEnhancedVideoBitrate()));
    private final AbstractConfigCell customAudioBitrateRow = cellGroup.appendCell(new ConfigCellCustom("customGroupVoipAudioBitrate", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell playerDecoderRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NyaConfig.INSTANCE.getPlayerDecoder(), new String[]{
            getString(R.string.VideoPlayerDecoderHardware),
            getString(R.string.VideoPlayerDecoderPreferHW),
            getString(R.string.VideoPlayerDecoderPreferSW),
    }, null));
    private final AbstractConfigCell dividerMedia = cellGroup.appendCell(new ConfigCellDivider());

    // N-Config
    private final AbstractConfigCell headerNConfig = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.N_Config)));
    private final AbstractConfigCell showRPCErrorRow = cellGroup.appendCell(new ConfigCellTextCheck(NyaConfig.INSTANCE.getShowRPCError()));
    private final ConfigCellTextCheck forceFontWeightFallbackRow = (ConfigCellTextCheck) cellGroup.appendCell(new ConfigCellTextCheck(NyaConfig.forceFontWeightFallback, null, getString(R.string.ForceFontWeightFallback)));
    private final AbstractConfigCell disableChoosingStickerRow = cellGroup.appendCell(new ConfigCellTextCheck(NyaConfig.disableChoosingSticker));
    private final AbstractConfigCell disableFilteringRow = cellGroup.appendCell(new ConfigCellCustom("SensitiveDisableFiltering", CellGroup.ITEM_TYPE_TEXT_CHECK, true));
    private final AbstractConfigCell devicePerformanceClassRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NyaConfig.INSTANCE.getPerformanceClass(), new String[]{
            getString(R.string.QualityAuto) + " [" + SharedConfig.getPerformanceClassName(SharedConfig.measureDevicePerformanceClass()) + "]",
            getString(R.string.PerformanceClassHigh),
            getString(R.string.PerformanceClassAverage),
            getString(R.string.PerformanceClassLow),
    }, null));
    private final AbstractConfigCell allowScreenCaptureRow = cellGroup.appendCell(new ConfigCellTextCheck(NyaConfig.INSTANCE.getAllowScreenCapture(), getString(R.string.AllowScreenCaptureNotice)));
    private final AbstractConfigCell allowCopyProtectedContentRow = cellGroup.appendCell(new ConfigCellTextCheck(NyaConfig.INSTANCE.getAllowCopyProtectedContent(), getString(R.string.AllowCopyProtectedContentNotice)));
    private final AbstractConfigCell saveTTLMediaRow = cellGroup.appendCell(new ConfigCellTextCheck(NyaConfig.INSTANCE.getSaveTTLMedia(), getString(R.string.SaveTTLMediaNotice)));
    private final AbstractConfigCell compactChatInputRow = cellGroup.appendCell(new ConfigCellTextCheck(NyaConfig.INSTANCE.getCompactChatInput(), getString(R.string.CompactChatInputNotice)));
    private final AbstractConfigCell dividerNConfig = cellGroup.appendCell(new ConfigCellDivider());

    // Story
    private final AbstractConfigCell headerStory = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Story)));
    private final AbstractConfigCell disableStoriesRow = cellGroup.appendCell(new ConfigCellTextCheck(NyaConfig.INSTANCE.getDisableStories()));
    private final AbstractConfigCell hideFromHeaderRow = cellGroup.appendCell(new ConfigCellTextCheck(NyaConfig.INSTANCE.getHideStoriesFromHeader()));
    private final AbstractConfigCell dividerStory = cellGroup.appendCell(new ConfigCellDivider());

    // Pangu
    private final AbstractConfigCell headerPangu = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Pangu)));
    private final AbstractConfigCell enablePanguOnSendingRow = cellGroup.appendCell(new ConfigCellTextCheck(NyaConfig.INSTANCE.getEnablePanguOnSending(), getString(R.string.PanguInfo)));
    private final AbstractConfigCell dividerPangu = cellGroup.appendCell(new ConfigCellDivider());

    public NekoExperimentalSettingsActivity() {
        if (NyaConfig.INSTANCE.getBackAnimationStyle().Int() != ActionBarLayout.BACK_ANIMATION_SPRING) {
            cellGroup.rows.remove(springAnimationCrossfadeRow);
        }
        checkStoriesRows();
        updateForceFontWeightFallbackEnabled();
        addRowsToMap(cellGroup);
    }

    @SuppressLint("NewApi")
    @Override
    public View createView(Context context) {
        View superView = super.createView(context);

        listAdapter = new ListAdapter(context);

        listView.setAdapter(listAdapter);

        setupDefaultListeners();

        // Cells: Set OnSettingChanged Callbacks
        cellGroup.callBackSettingsChanged = (key, newValue) -> {
            if (key.equals(NyaConfig.INSTANCE.getDisableStories().getKey())) {
                checkStoriesRows();
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NyaConfig.localPremium.getKey())) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mainUserInfoChanged);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
            } else if (key.equals(NyaConfig.INSTANCE.getBackAnimationStyle().getKey())) {
                final int style = (int) newValue;
                if (style != ActionBarLayout.BACK_ANIMATION_SPRING) {
                    if (cellGroup.rows.contains(springAnimationCrossfadeRow)) {
                        final int index = cellGroup.rows.indexOf(springAnimationCrossfadeRow);
                        cellGroup.rows.remove(springAnimationCrossfadeRow);
                        listAdapter.notifyItemRemoved(index);
                    }
                } else {
                    if (!cellGroup.rows.contains(springAnimationCrossfadeRow)) {
                        final int index = cellGroup.rows.indexOf(backAnimationStyleRow) + 1;
                        cellGroup.rows.add(index, springAnimationCrossfadeRow);
                        listAdapter.notifyItemInserted(index);
                    }
                }
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NyaConfig.INSTANCE.getSpringAnimationCrossfade().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NyaConfig.INSTANCE.getPerformanceClass().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NyaConfig.INSTANCE.getPlayerDecoder().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NyaConfig.forceFontWeightFallback.getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NyaConfig.INSTANCE.getHideStoriesFromHeader().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NyaConfig.enhancedFileLoader.getKey())) {
                final int mode = (int) newValue;
                if (mode == NyaConfig.ENHANCED_LOADER_BALANCED) {
                    showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                            .setTitle(getString(R.string.enhancedFileLoader))
                            .setMessage(getString(R.string.enhancedFileLoaderBalancedNotice))
                            .setPositiveButton(getString(R.string.OK), null)
                            .create());
                } else if (mode == NyaConfig.ENHANCED_LOADER_EXTREME) {
                    showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                            .setTitle(getString(R.string.enhancedFileLoader))
                            .setMessage(getString(R.string.enhancedFileLoaderExtremeNotice))
                            .setPositiveButton(getString(R.string.OK), null)
                            .create());
                }
            }
        };

        return superView;
    }

    @Override
    public void onResume() {
        super.onResume();
        checkSensitive();
        updateForceFontWeightFallbackEnabled();
    }

    private void updateForceFontWeightFallbackEnabled() {
        forceFontWeightFallbackRow.setEnabled(NyaConfig.typeface.Bool());
    }

    @Override
    protected void onCustomCellClick(View view, int position, float x, float y) {
        if (position == cellGroup.rows.indexOf(disableFilteringRow)) {
            sensitiveEnabled = !sensitiveEnabled;
            TL_account.setContentSettings req = new TL_account.setContentSettings();
            req.sensitive_enabled = sensitiveEnabled;
            AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
            progressDialog.show();
            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                progressDialog.dismiss();
                if (error == null) {
                    if (response instanceof TLRPC.TL_boolTrue && view instanceof TextCheckCell) {
                        ((TextCheckCell) view).setChecked(sensitiveEnabled);
                    }
                } else {
                    AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(currentAccount, error, this, req));
                }
            }));
        } else if (position == cellGroup.rows.indexOf(customAudioBitrateRow)) {
            PopupBuilder builder = new PopupBuilder(view);
            builder.setItems(new String[]{
                    "32 (" + getString(R.string.Default) + ")",
                    "64",
                    "128",
                    "192",
                    "256",
                    "320"
            }, (i, __) -> {
                switch (i) {
                    case 0:
                        NyaConfig.customAudioBitrate.setConfigInt(32);
                        break;
                    case 1:
                        NyaConfig.customAudioBitrate.setConfigInt(64);
                        break;
                    case 2:
                        NyaConfig.customAudioBitrate.setConfigInt(128);
                        break;
                    case 3:
                        NyaConfig.customAudioBitrate.setConfigInt(192);
                        break;
                    case 4:
                        NyaConfig.customAudioBitrate.setConfigInt(256);
                        break;
                    case 5:
                        NyaConfig.customAudioBitrate.setConfigInt(320);
                        break;
                }
                listAdapter.notifyItemChanged(position);
                return Unit.INSTANCE;
            });
            builder.show();
        }
    }

    @Override
    public int getBaseGuid() {
        return 11000;
    }

    @Override
    public int getDrawable() {
        return R.drawable.msg_fave;
    }

    @Override
    public String getTitle() {
        return getString(R.string.Experimental);
    }

    private void checkSensitive() {
        TL_account.getContentSettings req = new TL_account.getContentSettings();
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TL_account.contentSettings settings = (TL_account.contentSettings) response;
                sensitiveEnabled = settings.sensitive_enabled;
                sensitiveCanChange = settings.sensitive_can_change;
                int count = listView.getChildCount();
                ArrayList<Animator> animators = new ArrayList<>();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.getChildViewHolder(child);
                    int position = holder.getAdapterPosition();
                    if (position == cellGroup.rows.indexOf(disableFilteringRow)) {
                        TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                        checkCell.setChecked(sensitiveEnabled);
                        checkCell.setEnabled(sensitiveCanChange, animators);
                        if (sensitiveCanChange) {
                            if (!animators.isEmpty()) {
                                if (animatorSet != null) {
                                    animatorSet.cancel();
                                }
                                animatorSet = new AnimatorSet();
                                animatorSet.playTogether(animators);
                                animatorSet.addListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animator) {
                                        if (animator.equals(animatorSet)) {
                                            animatorSet = null;
                                        }
                                    }
                                });
                                animatorSet.setDuration(150);
                                animatorSet.start();
                            }
                        }
                    }
                }
            } else {
                AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(currentAccount, error, this, req));
            }
        }));
    }

    //impl ListAdapter
    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        protected void onBindCustomViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.itemView instanceof TextCheckCell textCheckCell) {
                textCheckCell.setEnabled(true, null);
                if (position == cellGroup.rows.indexOf(disableFilteringRow)) {
                    textCheckCell.setTextAndValueAndCheck(getString(R.string.SensitiveDisableFiltering), getString(R.string.SensitiveAbout), sensitiveEnabled, true, true);
                    textCheckCell.setEnabled(sensitiveCanChange, null);
                }
            } else if (holder.itemView instanceof TextSettingsCell textSettingsCell) {
                textSettingsCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                if (position == cellGroup.rows.indexOf(customAudioBitrateRow)) {
                    String value = NyaConfig.customAudioBitrate.Int() + "kbps";
                    if (NyaConfig.customAudioBitrate.Int() == 32)
                        value += " (" + getString(R.string.Default) + ")";
                    textSettingsCell.setTextAndValue(getString(R.string.customGroupVoipAudioBitrate), value, true);
                }
            }
        }
    }

    private void checkStoriesRows() {
        boolean disabled = NyaConfig.INSTANCE.getDisableStories().Bool();
        if (listAdapter == null) {
            if (disabled) {
                cellGroup.rows.remove(hideFromHeaderRow);
            }
            return;
        }
        if (!disabled) {
            final int index = cellGroup.rows.indexOf(disableStoriesRow);
            if (!cellGroup.rows.contains(hideFromHeaderRow)) {
                cellGroup.rows.add(index + 1, hideFromHeaderRow);
                listAdapter.notifyItemInserted(index + 1);
            }
        } else {
            final int index = cellGroup.rows.indexOf(hideFromHeaderRow);
            if (index != -1) {
                cellGroup.rows.remove(hideFromHeaderRow);
                listAdapter.notifyItemRemoved(index);
            }
        }
    }
}
