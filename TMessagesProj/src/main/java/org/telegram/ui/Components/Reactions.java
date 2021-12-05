package org.telegram.ui.Components;

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
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.MessageSeenView;
import org.telegram.ui.ProfileActivity;

public class Reactions {
    // This is just static utils to reduce the amount of garbage in ChatActivity and other classes

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
        int listViewTotalHeight = AndroidUtilities.dp(8) + AndroidUtilities.dp(44) * listView.getAdapter().getItemCount();

        //backContainer.addView(backItem);
        linearLayout.addView(backItem);

        HorizontalScrollView tabsScrollView = new HorizontalScrollView(ca.contentView.getContext());
        linearLayout.addView(tabsScrollView, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(39)));

        ReactionButtons.ButtonsView tabsView = new ReactionButtons.ButtonsView(ca.contentView.getContext());
        tabsView.buttons.setOptions(ReactionButtons.MODE_INSIDE, false, false, true);
        tabsView.buttons.setReactions(message.messageOwner.reactions);
        tabsView.buttons.setMaxWidth(100000);
        tabsView.buttons.measure();
        tabsScrollView.addView(tabsView);

        View dividerView = new View(ca.contentView.getContext());
        dividerView.setBackgroundResource(R.drawable.menu_divider_bg);
        dividerView.setMinimumHeight(AndroidUtilities.dp(8));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(8));
        dividerView.setLayoutParams(layoutParams);
        linearLayout.addView(dividerView);

        linearLayout.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 320, 0, 0, 0, 0));

        if (listViewTotalHeight > availableHeight) {
            if (availableHeight > AndroidUtilities.dp(620)) {
                listView.getLayoutParams().height = AndroidUtilities.dp(620);
            } else {
                listView.getLayoutParams().height = availableHeight;
            }
        } else {
            listView.getLayoutParams().height = listViewTotalHeight;
        }

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
            TLRPC.User user = messageSeenView.allUsers.get(position);
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
