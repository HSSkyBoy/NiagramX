package top.nkbe.niagram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.Cells.TextCell;

import top.nkbe.niagram.DatacenterActivity;
import top.nkbe.niagram.ui.cells.HeaderCell;

public class NekoAboutActivity extends BaseNekoSettingsActivity {

    private int communityHeaderRow;
    private int xChannelRow;
    private int channelRow;
    private int channelTipsRow;

    private int developmentHeaderRow;
    private int sourceCodeRow;
    private int translationRow;

    private int diagnosticsHeaderRow;
    private int datacenterStatusRow;

    @Override
    protected void updateRows() {
        super.updateRows();

        communityHeaderRow = addRow();
        xChannelRow = addRow();
        channelRow = addRow();
        channelTipsRow = addRow();

        developmentHeaderRow = addRow();
        sourceCodeRow = addRow();
        translationRow = addRow();

        diagnosticsHeaderRow = addRow();
        datacenterStatusRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.About);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == xChannelRow) {
            MessagesController.getInstance(currentAccount).openByUserName("niagramx_channel", NekoAboutActivity.this, 1);
        } else if (position == channelRow) {
            MessagesController.getInstance(currentAccount).openByUserName("NiagramX", NekoAboutActivity.this, 1);
        } else if (position == channelTipsRow) {
            MessagesController.getInstance(currentAccount).openByUserName("NagramTips", NekoAboutActivity.this, 1);
        } else if (position == translationRow) {
            Browser.openUrl(getParentActivity(), "https://crowdin.com/project/NagramX");
        } else if (position == sourceCodeRow) {
            Browser.openUrl(getParentActivity(), "https://github.com/HSSkyBoy/NiagramX");
        } else if (position == datacenterStatusRow) {
            presentFragment(new DatacenterActivity(0));
        }
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == communityHeaderRow) {
                        headerCell.setText(getString(R.string.NekoAboutSectionCommunity));
                    } else if (position == developmentHeaderRow) {
                        headerCell.setText(getString(R.string.NekoAboutSectionDevelopment));
                    } else if (position == diagnosticsHeaderRow) {
                        headerCell.setText(getString(R.string.NekoAboutSectionDiagnostics));
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == xChannelRow) {
                        textCell.setTextAndValueAndIcon(getString(R.string.XChannel), "@NiagramX_Channel", R.drawable.msg_channel, true);
                    } else if (position == channelRow) {
                        textCell.setTextAndValueAndIcon(getString(R.string.OfficialChannel), "@NiagramX", R.drawable.msg_channel, true);
                    } else if (position == channelTipsRow) {
                        textCell.setTextAndValueAndIcon(getString(R.string.TipsChannel), "@NagramTips", R.drawable.msg_help, false);
                    } else if (position == sourceCodeRow) {
                        textCell.setTextAndValueAndIcon(getString(R.string.SourceCode), "GitHub", R.drawable.msg_pin_code, translationRow != -1);
                    } else if (position == translationRow) {
                        textCell.setTextAndValueAndIcon(getString(R.string.TransSite), "Crowdin", R.drawable.msg_translate, false);
                    } else if (position == datacenterStatusRow) {
                        textCell.setTextAndIcon(getString(R.string.DatacenterStatus), R.drawable.msg_stats, false);
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == communityHeaderRow || position == developmentHeaderRow || position == diagnosticsHeaderRow) {
                return TYPE_HEADER;
            }
            return TYPE_TEXT;
        }
    }
}
