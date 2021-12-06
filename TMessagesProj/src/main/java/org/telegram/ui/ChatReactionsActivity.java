/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.DatePicker;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.ReactionsController;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.DialogRadioCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.PollEditTextCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Cells.TextDetailCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Cells.UserCell2;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ChatReactionsActivity extends BaseFragment {

    private ListAdapter listViewAdapter;
    private RecyclerListView listView;

    private long chatId;
    private TLRPC.Chat currentChat;
    private TLRPC.ChatFull info;
    private boolean isChannel;
    private ArrayList<TLRPC.TL_availableReaction> reactions;

    private int rowCount;

    private int enableReactionsRow;
    private int enableReactionsHintRow;
    private int reactionsHeaderRow;
    private int firstReactionRow;

    private boolean enableReactions;
    private boolean[] enableReaction;

    private ChatReactionsActivityDelegate delegate;

    public interface ChatReactionsActivityDelegate {
        void didSetReactions(ArrayList<String> reactions);
    }

    private final static int done_button = 1;

    public ChatReactionsActivity(long chatId) {
        super();
        this.chatId = chatId;
        currentChat = MessagesController.getInstance(currentAccount).getChat(chatId);
        if (currentChat != null) {
            isChannel = ChatObject.isChannel(currentChat) && !currentChat.megagroup;
        }
        reactions = ReactionsController.getInstance(currentAccount).getReactions();
        enableReaction = new boolean[reactions.size()];
    }

    public void setInfo(TLRPC.ChatFull chatFull) {
        info = chatFull;

        enableReactions = chatFull.available_reactions.size() > 0;
        // Set everything to true, if no reactions is enabled => if user toggles switch on, it will be better than nothing
        Arrays.fill(enableReaction, !enableReactions);
        for (String reaction : chatFull.available_reactions) {
            for (int i = 0; i < enableReaction.length; i++) {
                if (reactions.get(i).reaction.equals(reaction)) {
                    enableReaction[i] = true;
                }
            }
        }
        updateRows(false);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("Reactions", R.string.Reactions));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (checkDiscard()) {
                        finishFragment();
                    }
                } else if (id == done_button) {
                    onDonePressed();
                }
            }
        });

        // TODO: show a done button?
        //ActionBarMenu menu = actionBar.createMenu();
        //menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56), LocaleController.getString("Done", R.string.Done));

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        fragmentView.setFocusableInTouchMode(true);

        listView = new RecyclerListView(context);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        ((DefaultItemAnimator) listView.getItemAnimator()).setDelayAnimations(false);
        listView.setLayoutManager(linearLayoutManager);
        listView.setAdapter(listViewAdapter = new ListAdapter(context));
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                }
            }
        });

        listView.setOnItemClickListener((view, position) -> {
            if (view instanceof TextCheckCell) {
                TextCheckCell checkCell = (TextCheckCell) view;
                if (!checkCell.isEnabled()) {
                    return;
                }
                checkCell.setChecked(!checkCell.isChecked());
                if (position == enableReactionsRow) {
                    enableReactions = !enableReactions;
                } else if (position >= firstReactionRow) {
                    enableReaction[position - firstReactionRow] = !enableReaction[position - firstReactionRow];
                }
                updateRows(true);
            }
        });
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    private void updateRows(boolean update) {
        enableReactionsRow = 0;
        enableReactionsHintRow = 1;

        int firstReactionPrev = firstReactionRow;
        firstReactionRow = -1;
        rowCount = 2;
        if (enableReactions) {
            reactionsHeaderRow = rowCount++;
            firstReactionRow = rowCount;
            rowCount += enableReaction.length;
        }
        if (update) {
            if ((firstReactionPrev == -1) != (firstReactionRow == -1)) {
                listViewAdapter.notifyItemChanged(enableReactionsRow);
            }
            if (firstReactionPrev == -1 && firstReactionRow != -1) {
                listViewAdapter.notifyItemRangeInserted(firstReactionRow - 1, enableReaction.length + 1);
            } else if (firstReactionPrev != -1 && firstReactionRow == -1) {
                listViewAdapter.notifyItemRangeRemoved(firstReactionPrev - 1, enableReaction.length + 1);
            }
        }
    }

    private void onDonePressed() {
        ArrayList<String> enabled = new ArrayList<>();
        if (enableReactions) {
            for (int i = 0; i < enableReaction.length; i++) {
                if (enableReaction[i]) {
                    enabled.add(reactions.get(i).reaction);
                }
            }

        }
        if (delegate != null) {
            delegate.didSetReactions(enabled);
        }
        finishFragment();
    }

    public void setDelegate(ChatReactionsActivityDelegate chatReactionsActivityDelegate) {
        delegate = chatReactionsActivityDelegate;
    }

    private boolean checkDiscard() {
        boolean changed = true;
        if (changed) {
            // TODO: check for changes, ask for confirmation
            onDonePressed();
        }
        return true;
    }

    @Override
    public boolean onBackPressed() {
        return checkDiscard();
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == 0 || type == 3;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new TextCheckCell(mContext);
                    break;
                case 1:
                    view = new TextInfoPrivacyCell(mContext);
                    view.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                case 2:
                    view = new HeaderCell(mContext, Theme.key_windowBackgroundWhiteBlueHeader, 21, 15, true);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                default:
                    view = new TextCheckCell(mContext, 21, false, true);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    checkCell.setHeight(56);
                    checkCell.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                    checkCell.setTextSize(15);
                    checkCell.setColors(Theme.key_windowBackgroundWhite, Theme.key_chat_enableReactionSwitchTrack, Theme.key_chat_enableReactionSwitchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
                    checkCell.setBackgroundColor(Theme.getColor(enableReactions ? Theme.key_chat_enableReactionOnBackground : Theme.key_chat_enableReactionOffBackground));
                    checkCell.setTextAndCheck(LocaleController.getString("EnableReactions", R.string.EnableReactions), enableReactions, false);
                    break;
                case 1:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (isChannel) {
                        privacyCell.setText(LocaleController.getString("EnableReactionsHintChannel", R.string.EnableReactionsHintChannel));
                    } else {
                        privacyCell.setText(LocaleController.getString("EnableReactionsHintGroup", R.string.EnableReactionsHintGroup));
                    }
                    break;
                case 2:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    headerCell.setText(LocaleController.getString("AvailableReactions", R.string.AvailableReactions));
                    break;
                case 3:
                    TextCheckCell reactionCell = (TextCheckCell) holder.itemView;
                    TLRPC.TL_availableReaction reaction = reactions.get(position - firstReactionRow);
                    reactionCell.imageView.setImage(ImageLocation.getForDocument(reaction.static_icon), "50_50", null, null, null);
                    reactionCell.setTextAndCheck(reaction.title, enableReaction[position - firstReactionRow], true);
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == enableReactionsRow) {
                return 0;
            } else
            if (position == enableReactionsHintRow) {
                return 1;
            } else
            if (position == reactionsHeaderRow) {
                return 2;
            } else {
                return 3;
            }
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            if (listView != null) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof UserCell2) {
                        ((UserCell2) child).update(0);
                    }
                }
            }
        };

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{UserCell2.class, TextSettingsCell.class, TextCheckCell2.class, HeaderCell.class, TextDetailCell.class, PollEditTextCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText5));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueImageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switch2Track));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switch2TrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{HeaderCell.class}, new String[]{"textView2"}, null, null, null, Theme.key_windowBackgroundWhiteRedText5));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{HeaderCell.class}, new String[]{"textView2"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{PollEditTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_HINTTEXTCOLOR, new Class[]{PollEditTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteHintText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{UserCell2.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{UserCell2.class}, new String[]{"statusColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteGrayText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{UserCell2.class}, new String[]{"statusOnlineColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteBlueText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{UserCell2.class}, null, Theme.avatarDrawables, null, Theme.key_avatar_text));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink));

        themeDescriptions.add(new ThemeDescription(null, 0, new Class[]{DialogRadioCell.class}, new String[]{"textView"}, null, null, null, Theme.key_dialogTextBlack));
        themeDescriptions.add(new ThemeDescription(null, 0, new Class[]{DialogRadioCell.class}, new String[]{"textView"}, null, null, null, Theme.key_dialogTextGray2));
        themeDescriptions.add(new ThemeDescription(null, ThemeDescription.FLAG_CHECKBOX, new Class[]{DialogRadioCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_dialogRadioBackground));
        themeDescriptions.add(new ThemeDescription(null, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{DialogRadioCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_dialogRadioBackgroundChecked));

        return themeDescriptions;
    }
}
