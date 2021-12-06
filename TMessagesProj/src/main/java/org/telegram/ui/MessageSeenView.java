package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.ReactionsController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.AvatarsImageView;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.HideViewAfterAnimation;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

public class MessageSeenView extends FrameLayout {

    ArrayList<Long> reactedPeerIds = new ArrayList<>();
    public ArrayList<TLRPC.User> reactedUsers = new ArrayList<>();
    public HashMap<Long, TLRPC.TL_availableReaction> userReaction = new HashMap<>();
    public ArrayList<TLRPC.TL_messageUserReaction> reactions = new ArrayList<>();

    ArrayList<Long> peerIds = new ArrayList<>();
    public ArrayList<TLRPC.User> users = new ArrayList<>();
    public ArrayList<TLRPC.User> allUsers = new ArrayList<>();
    public ArrayList<TLRPC.User> filteredUsers = new ArrayList<>();
    AvatarsImageView avatarsImageView;
    TextView titleView;
    ImageView iconView;
    BackupImageView reactionView;
    int currentAccount;
    boolean isVoice;
    int reactedCount;
    String currentReactionFilter = "-";
    String currentNextOffset = null;
    long dialogId;
    int messageId;

    FlickerLoadingView flickerLoadingView;

    RecyclerListView recyclerListView;
    public int availableHeight;
    boolean loadingNext;

    public MessageSeenView(@NonNull Context context, int currentAccount, MessageObject messageObject, TLRPC.Chat chat) {
        super(context);
        this.currentAccount = currentAccount;
        this.dialogId = messageObject.getDialogId();
        this.messageId = messageObject.getId();
        isVoice = (messageObject.isRoundVideo() || messageObject.isVoice());
        flickerLoadingView = new FlickerLoadingView(context);
        flickerLoadingView.setColors(Theme.key_actionBarDefaultSubmenuBackground, Theme.key_listSelector, null);
        flickerLoadingView.setViewType(FlickerLoadingView.MESSAGE_SEEN_TYPE);
        flickerLoadingView.setIsSingleCell(false);
        addView(flickerLoadingView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);

        addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 40, 0, 62, 0));

        avatarsImageView = new AvatarsImageView(context, false);
        avatarsImageView.setStyle(AvatarsImageView.STYLE_MESSAGE_SEEN);
        addView(avatarsImageView, LayoutHelper.createFrame(24 + 12 + 12 + 8, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 0, 0));

        titleView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));


        reactedCount = 0;
        if (messageObject.messageOwner.reactions != null) {
            for (TLRPC.TL_reactionCount reactionCount : messageObject.messageOwner.reactions.results) {
                reactedCount += reactionCount.count;
            }
        }


        iconView = new ImageView(context);
        addView(iconView, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL, 11, 0, 0, 0));
        Drawable drawable = ContextCompat.getDrawable(context, reactedCount > 0 ? R.drawable.msg_reactions : (isVoice ? R.drawable.msg_played : R.drawable.msg_seen)).mutate();
        drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon), PorterDuff.Mode.MULTIPLY));
        iconView.setImageDrawable(drawable);

        reactionView = new BackupImageView(context);
        addView(reactionView, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL, 11, 0, 0, 0));
        reactionView.setVisibility(GONE);

        avatarsImageView.setAlpha(0);
        titleView.setAlpha(0);
        long fromId = 0;
        if (messageObject.messageOwner.from_id != null) {
            fromId = messageObject.messageOwner.from_id.user_id;
        }
        long finalFromId = fromId;

        TLRPC.TL_messages_getMessageReactionsList rreq = new TLRPC.TL_messages_getMessageReactionsList();
        rreq.id = messageObject.getId();
        rreq.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        rreq.limit = 100;
        ConnectionsManager.getInstance(currentAccount).sendRequest(rreq, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            FileLog.e("MessageSeenView reactions request completed");
            if (error != null) {
                updateView();
                return;
            }

            TLRPC.TL_messages_messageReactionsList res = (TLRPC.TL_messages_messageReactionsList) response;
            HashMap<Long, TLRPC.User> usersLocal = new HashMap<>();
            for (TLRPC.User user : res.users) {
                MessagesController.getInstance(currentAccount).putUser(user, false);
                usersLocal.put(user.id, user);
            }

            currentNextOffset = res.next_offset;

            reactions = res.reactions;
            ReactionsController reactionsController = ReactionsController.getInstance(currentAccount);
            for (TLRPC.TL_messageUserReaction reaction : reactions) {
                reactedPeerIds.add(reaction.user_id);
                userReaction.put(reaction.user_id, reactionsController.getReaction(reaction.reaction));
                TLRPC.User user = usersLocal.get(reaction.user_id);
                if (user == null) {
                    user = MessagesController.getInstance(currentAccount).getUser(reaction.user_id);
                }
                reactedUsers.add(user);
            }
            updateView();
        }));


        TLRPC.TL_messages_getMessageReadParticipants req = new TLRPC.TL_messages_getMessageReadParticipants();
        req.msg_id = messageObject.getId();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(messageObject.getDialogId());
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            FileLog.e("MessageSeenView request completed");
            if (error != null) {
                updateView();
                return;
            }
            TLRPC.Vector vector = (TLRPC.Vector) response;
            ArrayList<Long> unknownUsers = new ArrayList<>();
            HashMap<Long, TLRPC.User> usersLocal = new HashMap<>();
            ArrayList<Long> allPeers = new ArrayList<>();
            for (int i = 0, n = vector.objects.size(); i < n; i++) {
                Object object = vector.objects.get(i);
                if (object instanceof Long) {
                    Long peerId = (Long) object;
                    if (finalFromId == peerId) {
                        continue;
                    }
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(peerId);
                    allPeers.add(peerId);
                    if (true || user == null) {
                        unknownUsers.add(peerId);
                    } else {
                        usersLocal.put(peerId, user);
                    }
                }
            }

            if (unknownUsers.isEmpty()) {
                for (int i = 0; i < allPeers.size(); i++) {
                    peerIds.add(allPeers.get(i));
                    users.add(usersLocal.get(allPeers.get(i)));
                }
                updateView();
            } else {
                if (ChatObject.isChannel(chat)) {
                    TLRPC.TL_channels_getParticipants usersReq = new TLRPC.TL_channels_getParticipants();
                    usersReq.limit = 50;
                    usersReq.offset = 0;
                    usersReq.filter = new TLRPC.TL_channelParticipantsRecent();
                    usersReq.channel = MessagesController.getInstance(currentAccount).getInputChannel(chat.id);
                    ConnectionsManager.getInstance(currentAccount).sendRequest(usersReq, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                        if (response1 != null) {
                            TLRPC.TL_channels_channelParticipants users = (TLRPC.TL_channels_channelParticipants) response1;
                            for (int i = 0; i < users.users.size(); i++) {
                                TLRPC.User user = users.users.get(i);
                                MessagesController.getInstance(currentAccount).putUser(user, false);
                                usersLocal.put(user.id, user);
                            }
                            for (int i = 0; i < allPeers.size(); i++) {
                                peerIds.add(allPeers.get(i));
                                this.users.add(usersLocal.get(allPeers.get(i)));
                            }
                        }
                        updateView();
                    }));
                } else {
                    TLRPC.TL_messages_getFullChat usersReq = new TLRPC.TL_messages_getFullChat();
                    usersReq.chat_id = chat.id;
                    ConnectionsManager.getInstance(currentAccount).sendRequest(usersReq, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                        if (response1 != null) {
                            TLRPC.TL_messages_chatFull chatFull = (TLRPC.TL_messages_chatFull) response1;
                            for (int i = 0; i < chatFull.users.size(); i++) {
                                TLRPC.User user = chatFull.users.get(i);
                                MessagesController.getInstance(currentAccount).putUser(user, false);
                                usersLocal.put(user.id, user);
                            }
                            for (int i = 0; i < allPeers.size(); i++) {
                                peerIds.add(allPeers.get(i));
                                this.users.add(usersLocal.get(allPeers.get(i)));
                            }
                        }
                        updateView();
                    }));
                }
            }
        }));
        setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), AndroidUtilities.dp(4), AndroidUtilities.dp(4)));
        setEnabled(false);
    }

    boolean ignoreLayout;

    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (flickerLoadingView.getVisibility() == View.VISIBLE) {
            ignoreLayout = true;
            flickerLoadingView.setVisibility(View.GONE);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            flickerLoadingView.getLayoutParams().width = getMeasuredWidth();
            flickerLoadingView.setVisibility(View.VISIBLE);
            ignoreLayout = false;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    public void updateListViewHeight() {
        if (recyclerListView == null) {
            return;
        }
        int listViewTotalHeight = AndroidUtilities.dp(8) + AndroidUtilities.dp(44) * filteredUsers.size();
        if (listViewTotalHeight > availableHeight) {
            if (availableHeight > AndroidUtilities.dp(620)) {
                recyclerListView.getLayoutParams().height = AndroidUtilities.dp(620);
            } else {
                recyclerListView.getLayoutParams().height = availableHeight;
            }
        } else {
            recyclerListView.getLayoutParams().height = listViewTotalHeight;
        }
    }

    public void updateFilteredUsers(String reaction, boolean load) {
        if (!currentReactionFilter.equals(reaction)) {
            currentReactionFilter = reaction;
            currentNextOffset = null;
        }
        filteredUsers.clear();
        for (TLRPC.User user : allUsers) {
            // Do not display viewers until we loaded all reactions!
            if (currentNextOffset != null && reaction.equals("-") && !userReaction.containsKey(user.id)) {
                break;
            }
            if (reaction.equals("-") || (userReaction.get(user.id) != null && userReaction.get(user.id).reaction.equals(reaction))) {
                filteredUsers.add(user);
            }
        }
        if (recyclerListView != null) {
            recyclerListView.getAdapter().notifyDataSetChanged();
        }

        if (!reaction.equals("-") && load) {
            TLRPC.TL_messages_getMessageReactionsList rreq = new TLRPC.TL_messages_getMessageReactionsList();
            rreq.flags = 1;
            rreq.id = messageId;
            rreq.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            rreq.limit = 50;
            rreq.reaction = reaction;
            ConnectionsManager.getInstance(currentAccount).sendRequest(rreq, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error != null) {
                    updateView();
                    return;
                }

                TLRPC.TL_messages_messageReactionsList res = (TLRPC.TL_messages_messageReactionsList) response;
                HashMap<Long, TLRPC.User> usersLocal = new HashMap<>();
                for (TLRPC.User user : res.users) {
                    MessagesController.getInstance(currentAccount).putUser(user, false);
                    usersLocal.put(user.id, user);
                }

                currentNextOffset = res.next_offset;
                reactions = res.reactions;
                ReactionsController reactionsController = ReactionsController.getInstance(currentAccount);
                filteredUsers.clear();
                for (TLRPC.TL_messageUserReaction r : reactions) {
                    userReaction.put(r.user_id, reactionsController.getReaction(r.reaction));
                    TLRPC.User user = usersLocal.get(r.user_id);
                    if (user == null) {
                        user = MessagesController.getInstance(currentAccount).getUser(r.user_id);
                    }
                    filteredUsers.add(user);
                }
                if (recyclerListView != null) {
                    recyclerListView.getAdapter().notifyDataSetChanged();
                    updateListViewHeight();
                }
            }));
        } else {
            updateListViewHeight();
        }
    }

    public void loadNextUsers() {
        if (currentNextOffset == null || loadingNext) {
            return;
        }
        loadingNext = true;
        TLRPC.TL_messages_getMessageReactionsList rreq = new TLRPC.TL_messages_getMessageReactionsList();
        rreq.flags = 2;
        rreq.id = messageId;
        rreq.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        rreq.limit = 50;
        rreq.offset = currentNextOffset;
        if (currentReactionFilter != null && !currentReactionFilter.equals("-")) {
            rreq.flags |= 1;
            rreq.reaction = currentReactionFilter;
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(rreq, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null) {
                currentNextOffset = null;
                loadingNext = false;
                return;
            }

            TLRPC.TL_messages_messageReactionsList res = (TLRPC.TL_messages_messageReactionsList) response;
            HashMap<Long, TLRPC.User> usersLocal = new HashMap<>();
            for (TLRPC.User user : res.users) {
                MessagesController.getInstance(currentAccount).putUser(user, false);
                usersLocal.put(user.id, user);
            }

            currentNextOffset = res.next_offset;
            ReactionsController reactionsController = ReactionsController.getInstance(currentAccount);
            for (TLRPC.TL_messageUserReaction r : res.reactions) {
                userReaction.put(r.user_id, reactionsController.getReaction(r.reaction));
                TLRPC.User user = usersLocal.get(r.user_id);
                if (user == null) {
                    user = MessagesController.getInstance(currentAccount).getUser(r.user_id);
                }
                filteredUsers.add(user);
            }
            if (recyclerListView != null) {
                recyclerListView.getAdapter().notifyDataSetChanged();
            }
            loadingNext = false;
        }));
    }

    private void updateAllUsers() {
        allUsers.clear();
        for (TLRPC.User user : reactedUsers) {
            allUsers.add(user);
        }
        for (TLRPC.User user : users) {
            if (!userReaction.containsKey(user.id)) {
                allUsers.add(user);
            }
        }
        updateFilteredUsers(currentReactionFilter, false);
    }

    private void updateView() {
        updateAllUsers();
        setEnabled(allUsers.size() > 0);
        for (int i = 0; i < 3; i++) {
            if (i < allUsers.size()) {
                avatarsImageView.setObject(i, currentAccount, allUsers.get(i));
            } else {
                avatarsImageView.setObject(i, currentAccount, null);
            }
        }
        if (allUsers.size() == 1) {
            avatarsImageView.setTranslationX(AndroidUtilities.dp(24));
        } else if (allUsers.size() == 2) {
            avatarsImageView.setTranslationX(AndroidUtilities.dp(12));
        } else {
            avatarsImageView.setTranslationX(0);
        }

        avatarsImageView.commitTransition(false);
        if (allUsers.size() == 1 && allUsers.get(0) != null) {
            titleView.setText(ContactsController.formatName(allUsers.get(0).first_name, allUsers.get(0).last_name));
            if (userReaction.containsKey(allUsers.get(0).id)) {
                iconView.setVisibility(GONE);
                reactionView.setVisibility(VISIBLE);

                TLRPC.TL_availableReaction reaction = userReaction.get(allUsers.get(0).id);
                if (reaction != null) {
                    reactionView.setImage(ImageLocation.getForDocument(reaction.static_icon), "50_50", null, null, null);
                }
            }
        } else
        if (reactedCount > 0) {
            if (allUsers.size() > 0) {
                titleView.setText(LocaleController.formatString("MessageReacted", R.string.MessageReacted, String.format("%d", reactedCount)));
            } else {
                titleView.setText(LocaleController.formatString("MessageReacted", R.string.MessageReacted, String.format("%d/%d", reactedCount, peerIds.size())));
            }
        } else {
            titleView.setText(LocaleController.formatPluralString(isVoice ? "MessagePlayed" : "MessageSeen", peerIds.size()));
        }
        titleView.animate().alpha(1f).setDuration(220).start();
        avatarsImageView.animate().alpha(1f).setDuration(220).start();
        flickerLoadingView.animate().alpha(0f).setDuration(220).setListener(new HideViewAfterAnimation(flickerLoadingView)).start();
    }

    public RecyclerListView createListView() {
        recyclerListView = new RecyclerListView(getContext());
        recyclerListView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerListView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int p = parent.getChildAdapterPosition(view);
                if (p == 0) {
                    outRect.top = AndroidUtilities.dp(4);
                }
                if (p == allUsers.size() - 1) {
                    outRect.bottom = AndroidUtilities.dp(4);
                }
            }
        });
        recyclerListView.setAdapter(new RecyclerListView.SelectionAdapter() {

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return true;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                UserCell userCell = new UserCell(parent.getContext());
                userCell.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                return new RecyclerListView.Holder(userCell);
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                UserCell cell = (UserCell) holder.itemView;
                cell.setUser(filteredUsers.get(position));
                cell.setReaction(userReaction.get(filteredUsers.get(position).id));

                if (position > filteredUsers.size() - 10) {
                    loadNextUsers();
                }
            }

            @Override
            public int getItemCount() {
                return filteredUsers.size();
            }

        });
        return recyclerListView;
    }

    private static class UserCell extends FrameLayout {

        BackupImageView avatarImageView;
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        BackupImageView reactionImageView;
        TextView nameView;

        public UserCell(Context context) {
            super(context);
            avatarImageView = new BackupImageView(context);
            addView(avatarImageView, LayoutHelper.createFrame(32, 32, Gravity.CENTER_VERTICAL, 13, 0, 0, 0));
            avatarImageView.setRoundRadius(AndroidUtilities.dp(16));
            nameView = new TextView(context);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            nameView.setLines(1);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            addView(nameView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 59, 0, 13, 0));

            nameView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));


            reactionImageView = new BackupImageView(context);
            addView(reactionImageView, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL, 205, 0, 13, 0));
            reactionImageView.setVisibility(GONE);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(44), View.MeasureSpec.EXACTLY));
        }

        public void setUser(TLRPC.User user) {
            if (user != null) {
                avatarDrawable.setInfo(user);
                ImageLocation imageLocation = ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL);
                avatarImageView.setImage(imageLocation, "50_50", avatarDrawable, user);
                nameView.setText(ContactsController.formatName(user.first_name, user.last_name));
            }
        }

        public void setReaction(TLRPC.TL_availableReaction reaction) {
            if (reaction != null) {
                reactionImageView.setVisibility(VISIBLE);
                reactionImageView.setImage(ImageLocation.getForDocument(reaction.static_icon), "50_50", null, null, null);
            } else {
                reactionImageView.setVisibility(GONE);
            }
        }
    }
}
