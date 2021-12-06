package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.ReactionsController;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.MessageSeenView;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.HashMap;

public class Reactions {
    // This is just static utils to reduce the amount of garbage in ChatActivity
    // The downside is having to make a lot of fields in ChatActivity publicly accessible (which breaks encapsulation), but I consider that a lesser evil

    public static void sendReaction(ChatActivity ca, int currentAccount, MessageObject message, String reaction, float x, float y) {
        TLRPC.TL_messages_sendReaction req = new TLRPC.TL_messages_sendReaction();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(message.messageOwner.dialog_id);
        req.msg_id = message.messageOwner.id;
        if (reaction != null) {
            req.flags = 1;
            req.reaction = reaction;
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null) {
                return;
            }
            MessagesController.getInstance(currentAccount).processUpdates((TLRPC.Updates) response, false);
        }));
    }

    public static void longPressReaction(ChatActivity ca, ChatMessageCell cell, ReactionButtons buttons, ReactionButtons.Button button, float x, float y, float y1) {
        buttons.setHighlightedReaction(button.reaction);

        int totalHeight = ca.contentView.getHeightWithKeyboard();
        int availableHeight = totalHeight - ca.scrimPopupY - AndroidUtilities.dp(46 + 16);

        if (SharedConfig.messageSeenHintCount > 0 && ca.contentView.getKeyboardHeight() < AndroidUtilities.dp(20)) {
            availableHeight -= AndroidUtilities.dp(52);
            Bulletin bulletin = BulletinFactory.of(ca).createErrorBulletin(AndroidUtilities.replaceTags(LocaleController.getString("MessageSeenTooltipMessage", R.string.MessageSeenTooltipMessage)));
            bulletin.tag = 1;
            bulletin.setDuration(4000);
            bulletin.show();
            SharedConfig.updateMessageSeenHintCount(SharedConfig.messageSeenHintCount - 1);
        } else if (ca.contentView.getKeyboardHeight() > AndroidUtilities.dp(20)) {
            availableHeight -= ca.contentView.getKeyboardHeight() / 3f;
        }

        MessageObject message = cell.getMessageObject();

        Rect backgroundPaddings = new Rect();
        Drawable shadowDrawable = ca.getParentActivity().getResources().getDrawable(R.drawable.popup_fixed_alert).mutate();
        shadowDrawable.getPadding(backgroundPaddings);

        Rect rect = new Rect();

        MessageSeenView messageSeenView = new MessageSeenView(ca.contentView.getContext(), ca.currentAccount, message, ca.currentChat, button.reaction);

        LinearLayout linearLayout = new LinearLayout(ca.contentView.getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(260), MeasureSpec.AT_MOST), heightMeasureSpec);
                setPivotX(getMeasuredWidth() - AndroidUtilities.dp(8));
                setPivotY(AndroidUtilities.dp(8));
            }

            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0 && ca.scrimPopupWindow != null && ca.scrimPopupWindow.isShowing()) {
                    ca.scrimPopupWindow.dismiss();
                }
                return super.dispatchKeyEvent(event);
            }
        };
        linearLayout.setOnTouchListener(new View.OnTouchListener() {

            private int[] pos = new int[2];

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    if (ca.scrimPopupWindow != null && ca.scrimPopupWindow.isShowing()) {
                        View contentView = ca.scrimPopupWindow.getContentView();
                        contentView.getLocationInWindow(pos);
                        rect.set(pos[0], pos[1], pos[0] + contentView.getMeasuredWidth() - AndroidUtilities.dp(36), pos[1] + contentView.getMeasuredHeight());
                        if (!rect.contains((int) event.getX(), (int) event.getY())) {
                            ca.scrimPopupWindow.dismiss();
                        }
                    }
                } else if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                    if (ca.scrimPopupWindow != null && ca.scrimPopupWindow.isShowing()) {
                        ca.scrimPopupWindow.dismiss();
                    }
                }
                return false;
            }
        });
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        RecyclerListView listView = messageSeenView.createListView();

        linearLayout.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 320, 0, 0, 0, 0));

        messageSeenView.availableHeight = availableHeight;
        messageSeenView.updateListViewHeight();

        Drawable shadowDrawable3 = ContextCompat.getDrawable(ca.contentView.getContext(), R.drawable.popup_fixed_alert).mutate();
        shadowDrawable3.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), PorterDuff.Mode.MULTIPLY));
        linearLayout.setBackground(shadowDrawable3);

        ca.scrimPopupWindow = new ActionBarPopupWindow(linearLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss();
                buttons.setHighlightedReaction(null);
                if (ca.scrimPopupWindow != this) {
                    return;
                }
                ca.scrimPopupWindow = null;
                ca.scrimPopupWindowItems = null;
                if (ca.scrimAnimatorSet != null) {
                    ca.scrimAnimatorSet.cancel();
                    ca.scrimAnimatorSet = null;
                }
                if (ca.scrimView instanceof ChatMessageCell) {
                    ChatMessageCell cell = (ChatMessageCell) ca.scrimView;
                    cell.setInvalidatesParent(false);
                }
                ca.chatLayoutManager.setCanScrollVertically(true);
                ca.scrimAnimatorSet = new AnimatorSet();
                ArrayList<Animator> animators = new ArrayList<>();
                animators.add(ObjectAnimator.ofInt(ca.scrimPaint, AnimationProperties.PAINT_ALPHA, 0));
                if (ca.pagedownButton.getTag() != null) {
                    animators.add(ObjectAnimator.ofFloat(ca.pagedownButton, View.ALPHA, 1.0f));
                }
                if (ca.mentiondownButton.getTag() != null) {
                    animators.add(ObjectAnimator.ofFloat(ca.mentiondownButton, View.ALPHA, 1.0f));
                }
                ca.scrimAnimatorSet.playTogether(animators);
                ca.scrimAnimatorSet.setDuration(220);
                ca.scrimAnimatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        ca.scrimView = null;
                        ca.contentView.invalidate();
                        ca.chatListView.invalidate();
                    }
                });
                ca.scrimAnimatorSet.start();
                if (ca.chatActivityEnterView != null) {
                    ca.chatActivityEnterView.getEditField().setAllowDrawCursor(true);
                }
                if (ca.messageSeenUsersPopupWindow != null) {
                    ca.messageSeenUsersPopupWindow.dismiss();
                }
            }
        };
        ca.scrimPopupWindow.setPauseNotifications(true);
        ca.scrimPopupWindow.setDismissAnimationDuration(220);
        ca.scrimPopupWindow.setOutsideTouchable(true);
        ca.scrimPopupWindow.setClippingEnabled(true);
        ca.scrimPopupWindow.setAnimationStyle(R.style.PopupContextAnimation);
        ca.scrimPopupWindow.setFocusable(true);
        linearLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
        ca.scrimPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        ca.scrimPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        ca.scrimPopupWindow.getContentView().setFocusableInTouchMode(true);

        int popupX = cell.getLeft() + (int) x - backgroundPaddings.left/* - linearLayout.getMeasuredWidth() + backgroundPaddings.left - AndroidUtilities.dp(28)*/;
        if (popupX < AndroidUtilities.dp(6)) {
            popupX = AndroidUtilities.dp(6);
        } else if (popupX > ca.chatListView.getMeasuredWidth() - AndroidUtilities.dp(6) - linearLayout.getMeasuredWidth()) {
            popupX = ca.chatListView.getMeasuredWidth() - AndroidUtilities.dp(6) - linearLayout.getMeasuredWidth();
        }
        if (AndroidUtilities.isTablet()) {
            int[] location = new int[2];
            ca.fragmentView.getLocationInWindow(location);
            popupX += location[0];
        }
        int height = linearLayout.getMeasuredHeight();
        int keyboardHeight = ca.contentView.measureKeyboardHeight();
        if (keyboardHeight > AndroidUtilities.dp(20)) {
            totalHeight += keyboardHeight;
        }
        int popupY;
        if (height < totalHeight) {
            popupY = (int) (ca.chatListView.getY() + cell.getTop() + y - AndroidUtilities.dp(3));
            if (height - backgroundPaddings.top - backgroundPaddings.bottom > AndroidUtilities.dp(240)) {
                popupY += AndroidUtilities.dp(240) - height;
            }
            if (popupY < ca.chatListView.getY() + AndroidUtilities.dp(24)) {
                popupY = (int) (ca.chatListView.getY() + AndroidUtilities.dp(24));
            } else if (popupY > totalHeight - height - AndroidUtilities.dp(8)) {
                popupY = totalHeight - height - AndroidUtilities.dp(8);
            }
        } else {
            popupY = ca.inBubbleMode ? 0 : AndroidUtilities.statusBarHeight;
        }
        ca.scrimPopupWindow.showAtLocation(ca.chatListView, Gravity.LEFT | Gravity.TOP, ca.scrimPopupX = popupX, ca.scrimPopupY = popupY);
        ca.chatListView.stopScroll();
        ca.chatLayoutManager.setCanScrollVertically(false);
        ca.scrimView = cell;
        ca.scrimViewReaction = button.reaction;
        cell.setInvalidatesParent(true);
        ca.contentView.invalidate();
        ca.chatListView.invalidate();
        if (ca.scrimAnimatorSet != null) {
            ca.scrimAnimatorSet.cancel();
        }
        ca.scrimAnimatorSet = new AnimatorSet();
        ArrayList<Animator> animators = new ArrayList<>();
        animators.add(ObjectAnimator.ofInt(ca.scrimPaint, AnimationProperties.PAINT_ALPHA, 0, 50));
        if (ca.pagedownButton.getTag() != null) {
            animators.add(ObjectAnimator.ofFloat(ca.pagedownButton, View.ALPHA, 0));
        }
        if (ca.mentiondownButton.getTag() != null) {
            animators.add(ObjectAnimator.ofFloat(ca.mentiondownButton, View.ALPHA, 0));
        }
        ca.scrimAnimatorSet.playTogether(animators);
        ca.scrimAnimatorSet.setDuration(150);
        ca.scrimAnimatorSet.start();
        ca.hideHints(false);
        if (ca.topUndoView != null) {
            ca.topUndoView.hide(true, 1);
        }
        if (ca.undoView != null) {
            ca.undoView.hide(true, 1);
        }
        if (ca.chatActivityEnterView != null) {
            ca.chatActivityEnterView.getEditField().setAllowDrawCursor(false);
        }
    }

    public static void openReactionsMenu(ChatActivity ca, MessageSeenView messageSeenView, Rect rect, MessageObject message) {
        if (ca.scrimPopupWindow == null || messageSeenView.allUsers.isEmpty()) {
            return;
        }
        int totalHeight = ca.contentView.getHeightWithKeyboard();
        int availableHeight = totalHeight - ca.scrimPopupY - AndroidUtilities.dp(46 + 16);

        if (SharedConfig.messageSeenHintCount > 0 && ca.contentView.getKeyboardHeight() < AndroidUtilities.dp(20)) {
            availableHeight -= AndroidUtilities.dp(52);
            Bulletin bulletin = BulletinFactory.of(ca).createErrorBulletin(AndroidUtilities.replaceTags(LocaleController.getString("MessageSeenTooltipMessage", R.string.MessageSeenTooltipMessage)));
            bulletin.tag = 1;
            bulletin.setDuration(4000);
            bulletin.show();
            SharedConfig.updateMessageSeenHintCount(SharedConfig.messageSeenHintCount - 1);
        } else if (ca.contentView.getKeyboardHeight() > AndroidUtilities.dp(20)) {
            availableHeight -= ca.contentView.getKeyboardHeight() / 3f;
        }
        View previousPopupContentView = ca.scrimPopupWindow.getContentView();

        ActionBarMenuSubItem backItem = new ActionBarMenuSubItem(ca.getParentActivity(), true, true, ca.themeDelegate);
        backItem.setItemHeight(44);
        backItem.setTextAndIcon(LocaleController.getString("Back", R.string.Back), R.drawable.msg_arrow_back);
        backItem.getTextView().setPadding(LocaleController.isRTL ? 0 : AndroidUtilities.dp(40), 0, LocaleController.isRTL ? AndroidUtilities.dp(40) : 0, 0);

        //Drawable shadowDrawable2 = ContextCompat.getDrawable(contentView.getContext(), R.drawable.popup_fixed_alert).mutate();
        //shadowDrawable2.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), PorterDuff.Mode.MULTIPLY));
        //FrameLayout backContainer = new FrameLayout(contentView.getContext());
        //backContainer.setBackground(shadowDrawable2);

        LinearLayout linearLayout = new LinearLayout(ca.contentView.getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(260), MeasureSpec.AT_MOST), heightMeasureSpec);
                setPivotX(getMeasuredWidth() - AndroidUtilities.dp(8));
                setPivotY(AndroidUtilities.dp(8));
            }

            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0 && ca.scrimPopupWindow != null && ca.scrimPopupWindow.isShowing()) {
                    if (ca.messageSeenUsersPopupWindow != null) {
                        ca.messageSeenUsersPopupWindow.dismiss();
                    }
                }
                return super.dispatchKeyEvent(event);
            }
        };
        linearLayout.setOnTouchListener(new View.OnTouchListener() {

            private int[] pos = new int[2];

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    if (ca.messageSeenUsersPopupWindow != null && ca.messageSeenUsersPopupWindow.isShowing()) {
                        View contentView = ca.messageSeenUsersPopupWindow.getContentView();
                        contentView.getLocationInWindow(pos);
                        rect.set(pos[0], pos[1], pos[0] + contentView.getMeasuredWidth(), pos[1] + contentView.getMeasuredHeight());
                        if (!rect.contains((int) event.getX(), (int) event.getY())) {
                            ca.messageSeenUsersPopupWindow.dismiss();
                        }
                    }
                } else if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                    if (ca.messageSeenUsersPopupWindow != null && ca.messageSeenUsersPopupWindow.isShowing()) {
                        ca.messageSeenUsersPopupWindow.dismiss();
                    }
                }
                return false;
            }
        });
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        RecyclerListView listView = messageSeenView.createListView();

        //backContainer.addView(backItem);
        linearLayout.addView(backItem);


        int total = 0;
        for (TLRPC.TL_reactionCount reactionCount : message.messageOwner.reactions.results) {
            total += reactionCount.count;
        }
        boolean hasTabs = total > 10;
        if (hasTabs) {
            HorizontalScrollView tabsScrollView = new HorizontalScrollView(ca.contentView.getContext());
            linearLayout.addView(tabsScrollView, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(39)));

            ReactionButtons.ButtonsView tabsView = new ReactionButtons.ButtonsView(ca.contentView.getContext());
            tabsView.buttons.setOptions(ReactionButtons.MODE_INSIDE, false, false, true);
            tabsView.buttons.setReactions(message.messageOwner.reactions);
            tabsView.buttons.setMaxWidth(100000);
            tabsView.buttons.setActiveReaction("-");
            tabsView.buttons.measure();
            tabsView.setOnClickListener(new ReactionButtons.OnClickListener() {
                @Override
                public void onClick(ReactionButtons.Button button, String reaction, boolean longClick) {
                    tabsView.buttons.setActiveReaction(reaction);
                    messageSeenView.updateFilteredUsers(reaction, true);
                }
            });
            tabsScrollView.addView(tabsView);
        }

        View dividerView = new View(ca.contentView.getContext());
        dividerView.setBackgroundResource(R.drawable.menu_divider_bg);
        dividerView.setMinimumHeight(AndroidUtilities.dp(hasTabs ? 1 : 8));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(hasTabs ? 1 : 8));
        dividerView.setLayoutParams(layoutParams);
        linearLayout.addView(dividerView);

        linearLayout.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 320, 0, 0, 0, 0));

        messageSeenView.availableHeight = availableHeight;
        messageSeenView.updateListViewHeight();

        Drawable shadowDrawable3 = ContextCompat.getDrawable(ca.contentView.getContext(), R.drawable.popup_fixed_alert).mutate();
        shadowDrawable3.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), PorterDuff.Mode.MULTIPLY));
        linearLayout.setBackground(shadowDrawable3);

        boolean[] backButtonPressed = new boolean[1];

        ca.messageSeenUsersPopupWindow = new ActionBarPopupWindow(linearLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss(boolean animated) {
                super.dismiss(animated);
                if (backButtonPressed[0]) {
                    linearLayout.animate().alpha(0).scaleX(0).scaleY(0).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(350);
                    previousPopupContentView.animate().alpha(1f).scaleX(1).scaleY(1).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(350);
                } else {
                    if (ca.scrimPopupWindow != null) {
                        ca.scrimPopupWindow.dismiss();

                        ca.contentView.invalidate();
                        ca.chatListView.invalidate();
                    }
                }
                if (Bulletin.getVisibleBulletin() != null && Bulletin.getVisibleBulletin().tag == 1) {
                    Bulletin.getVisibleBulletin().hide();
                }
                ca.messageSeenUsersPopupWindow = null;
            }
        };
        ca.messageSeenUsersPopupWindow.setOutsideTouchable(true);
        ca.messageSeenUsersPopupWindow.setClippingEnabled(true);
        ca.messageSeenUsersPopupWindow.setFocusable(true);
        ca.messageSeenUsersPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        ca.messageSeenUsersPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        ca.messageSeenUsersPopupWindow.getContentView().setFocusableInTouchMode(true);

        ca.messageSeenUsersPopupWindow.showAtLocation(ca.chatListView, Gravity.LEFT | Gravity.TOP, ca.scrimPopupX, ca.scrimPopupY);
        previousPopupContentView.setPivotX(AndroidUtilities.dp(8));
        previousPopupContentView.setPivotY(AndroidUtilities.dp(8));
        previousPopupContentView.animate().alpha(0).scaleX(0f).scaleY(0f).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(350);

        linearLayout.setAlpha(0f);
        linearLayout.setScaleX(0f);
        linearLayout.setScaleY(0f);
        linearLayout.animate().alpha(1f).scaleX(1f).scaleY(1f).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(350);

        backItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ca.messageSeenUsersPopupWindow != null) {
                    ca.messageSeenUsersPopupWindow.setEmptyOutAnimation(250);
                    backButtonPressed[0] = true;
                    ca.messageSeenUsersPopupWindow.dismiss(true);
                }
            }
        });

        listView.setOnItemClickListener((view1, position) -> {
            TLRPC.User user = messageSeenView.filteredUsers.get(position);
            if (user == null) {
                return;
            }
            Bundle args = new Bundle();
            args.putLong("user_id", user.id);
            ProfileActivity fragment = new ProfileActivity(args);
            ca.presentFragment(fragment);
            if (ca.messageSeenUsersPopupWindow != null) {
                ca.messageSeenUsersPopupWindow.dismiss();
            }
        });
    }
}
