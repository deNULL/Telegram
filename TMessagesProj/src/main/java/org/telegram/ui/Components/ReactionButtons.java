package org.telegram.ui.Components;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.ReactionsController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.HashMap;

public class ReactionButtons {
    private TLRPC.TL_messageReactions reactions;

    public final int marginX = 6;
    public final int marginY = 6;

    public final int iconTop = 3;
    public final int iconLeft = 7;
    public final int iconSize = 20;
    public final int microIconSize = 15;
    public final int microIconSpacing = 4;
    public final int microIconRight = 5;
    public final int labelLeft = 5;
    public final int labelRight = 9;
    public final int labelBottom = 9;
    public final int avatarLeft = 5;
    public final int avatarTop = 2;
    public final int avatarRight = 2;
    public final int avatarOffset = 8;
    public final int avatarSize = 22;

    public static final int MODE_MICRO = 0; // small static icons (max 2) in the timestamp
    public static final int MODE_INSIDE = 1; // full-sized buttons inside the bubble
    public static final int MODE_OUTSIDE = 2; // full-sized buttons outside the bubble
    public int mode;

    // false: show only the number of reactions, true: show photos of last people reacted (up to 3)
    public boolean showLastReactions;
    public boolean isOutgoingMessage;
    public boolean isTabs;

    // reaction of the current user, or "" if none
    public String activeReaction = "";
    public String highlightedReaction = "";

    public boolean isMeasured; // true if measure was called after the last reactions update
    public int maxWidth;
    public int width;
    public int height;
    public int lastLineWidth;

    public View parentView;

    private ReactionButtons.OnClickListener listener = null;
    public Button pressedButton = null;

    public interface OnClickListener {
        void onClick(Button button, String reaction, boolean longClick);
    }

    public static class ButtonsView extends View {
        public static final int paddingLeft = 10;
        public static final int paddingRight = 10;
        public static final int paddingTop = 2;
        public static final int paddingBottom = 10;
        public ReactionButtons buttons;
        private ReactionButtons.OnClickListener listener = null;
        private Button pressedButton = null;

        public ButtonsView(Context context) {
            super(context);
            buttons = new ReactionButtons(this);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.translate(AndroidUtilities.dp(paddingLeft), AndroidUtilities.dp(paddingTop));
            buttons.draw(canvas);
            canvas.restore();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(buttons.width + AndroidUtilities.dp(paddingLeft + paddingRight), buttons.height + AndroidUtilities.dp(paddingTop + paddingBottom));
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                pressedButton = buttons.getButtonAt(event, paddingLeft, paddingTop);
                return pressedButton != null;
            } else
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (pressedButton != null && listener != null) {
                    listener.onClick(pressedButton, pressedButton.reaction, event.getEventTime() - event.getDownTime() > ViewConfiguration.getLongPressTimeout());
                    return true;
                }
                pressedButton = null;
            }
            return false;
        }

        public void setOnClickListener(ReactionButtons.OnClickListener listener) {
            this.listener = listener;
        }
    }

    public class Button {
        public String reaction;
        public int count;
        public RectF rect;
        public ImageReceiver icon;
        public ImageReceiver[] avatars;
        public AvatarDrawable[] drawables;
        public boolean isTotal;

        public Button(int totalReactions) {
            reaction = "-";
            isTotal = true;
            count = totalReactions;
            rect = new RectF(0, 0, 0, 0);
            icon = new ImageReceiver(parentView);
            icon.setImageBitmap(parentView.getResources().getDrawable(R.drawable.msg_reactions_filled));
            icon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_inReactionText), PorterDuff.Mode.MULTIPLY));
        }

        public Button(TLRPC.TL_reactionCount reactionCount, ArrayList<Long> recentUserIds) {
            TLRPC.TL_availableReaction reaction = ReactionsController.getInstance(UserConfig.selectedAccount).getReaction(reactionCount.reaction);

            this.reaction = reactionCount.reaction;
            count = reactionCount.count;
            rect = new RectF(0, 0, 0, 0);
            icon = new ImageReceiver(parentView);
            if (reaction == null) {
                return;
            }

            icon.setImage(ImageLocation.getForDocument(reaction.static_icon), "50_50", null, null, null, 0);
            if (recentUserIds != null) {
                avatars = new ImageReceiver[recentUserIds.size()];
                drawables = new AvatarDrawable[recentUserIds.size()];
                for (int i = 0; i < avatars.length; i++) {
                    TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(recentUserIds.get(i));
                    avatars[i] = new ImageReceiver(parentView);
                    drawables[i] = new AvatarDrawable();
                    drawables[i].setTextSize(AndroidUtilities.dp(avatarSize * 0.5f));
                    avatars[i].setRoundRadius(AndroidUtilities.dp(avatarSize * 0.5f));
                    avatars[i].setForUserOrChat(user, drawables[i]);
                    //avatars[i].setImage(ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL), "50_50", null, null, null, 0);
                }
            }
        }

        public void setRect(float x, float y, float w, float h) {
            rect = new RectF(x, y, x + w, y + h);
            if (mode == MODE_MICRO) {
                icon.setImageCoords(x, y, w, h);
                return;
            }
            icon.setImageCoords(
                x + AndroidUtilities.dp(iconLeft),
                y + AndroidUtilities.dp(iconTop),
                AndroidUtilities.dp(iconSize),
                AndroidUtilities.dp(iconSize)
            );
            if (avatars != null) {
                for (int i = 0; i < avatars.length; i++) {
                    avatars[i].setImageCoords(
                            x + AndroidUtilities.dp(iconLeft + iconSize + avatarLeft + avatarOffset * i),
                            y + AndroidUtilities.dp(avatarTop),
                            AndroidUtilities.dp(avatarSize),
                            AndroidUtilities.dp(avatarSize)
                    );
                }
            }
        }

        public int measureWidth() {
            if (mode == MODE_MICRO) {
                return microIconSize;
            }
            if (avatars != null) {
                return AndroidUtilities.dp(iconLeft + iconSize + avatarLeft + avatarOffset * (avatars.length - 1) + avatarSize + avatarRight);
            }
            String str = String.format("%s", LocaleController.formatShortNumber(Math.max(1, count), null));
            return AndroidUtilities.dp(iconLeft + iconSize + labelLeft) + (int)(Theme.chat_reactionCountPaint.measureText(str)) + AndroidUtilities.dp(labelRight);
        }
    }
    public ArrayList<Button> buttons = new ArrayList<>();

    private final Paint roundPaint;

    public ReactionButtons(View parent) {
        parentView = parent;
        roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        //
    }

    public void setReactions(TLRPC.TL_messageReactions reactions) {
        this.reactions = reactions;
        this.isMeasured = false;
        buttons.clear();
        if (reactions != null && reactions.results != null) {
            if (!reactions.min) {
                this.activeReaction = "";
            }

            HashMap<String, ArrayList<Long>> recentReactions = new HashMap<>();
            for (TLRPC.TL_messageUserReaction userReaction : reactions.recent_reactons) {
                if (!recentReactions.containsKey(userReaction.reaction)) {
                    recentReactions.put(userReaction.reaction, new ArrayList<>());
                }
                recentReactions.get(userReaction.reaction).add(userReaction.user_id);
            }

            boolean allRecentFit = !isTabs;
            int totalReactions = 0;
            for (TLRPC.TL_reactionCount reactionCount : reactions.results) {
                ArrayList<Long> recentUserIds = recentReactions.get(reactionCount.reaction);
                if ((recentUserIds == null ? 0 : recentUserIds.size()) < reactionCount.count) {
                    allRecentFit = false;
                }
                totalReactions += reactionCount.count;
            }

            if (isTabs) {
                // Add "total" button
                buttons.add(new Button(totalReactions));
            }

            for (TLRPC.TL_reactionCount reactionCount : reactions.results) {
                ArrayList<Long> recentUserIds = recentReactions.get(reactionCount.reaction);
                if (reactionCount.chosen) {
                    this.activeReaction = reactionCount.reaction;
                }
                buttons.add(new Button(reactionCount, allRecentFit ? (recentUserIds == null ? new ArrayList<>() : recentUserIds) : null));
            }
        }
    }

    public void setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
    }

    public void setOptions(int mode, boolean showLastReactions, boolean isOutgoingMessage, boolean isTabs) {
        this.mode = mode;
        this.showLastReactions = showLastReactions;
        this.isOutgoingMessage = isOutgoingMessage;
        this.isTabs = isTabs;
    }

    public void setActiveReaction(String activeReaction) {
        this.activeReaction = activeReaction;
        parentView.invalidate();
    }

    public void setHighlightedReaction(String highlightedReaction) {
        this.highlightedReaction = highlightedReaction;
        parentView.invalidate();
    }

    public void measure() {
        width = 0;
        height = 0;
        lastLineWidth = 0;
        isMeasured = true;
        if (buttons.isEmpty()) {
            return;
        }
        if (mode == MODE_MICRO) {
            width = AndroidUtilities.dp(buttons.size() * microIconSize + (buttons.size() - 1) * microIconSpacing + microIconRight);
            height = AndroidUtilities.dp(microIconSize);
            for (int i = 0; i < buttons.size(); i++) {
                Button button = buttons.get(i);
                // Button are right-aligned, so coords are negative
                button.setRect(
                    -width + AndroidUtilities.dp(microIconSize + microIconSpacing) * i, 0,
                    AndroidUtilities.dp(microIconSize), AndroidUtilities.dp(microIconSize)
                );
            }
            return;
        }
        int x = 0;
        int buttonHeight = AndroidUtilities.dp(26);
        for (Button button : buttons) {
            int buttonWidth = button.measureWidth();
            if (x > 0 && x + AndroidUtilities.dp(marginX) + buttonWidth > maxWidth) {
                height += AndroidUtilities.dp(marginY) + buttonHeight;
                x = buttonWidth;
                button.setRect(0, height, buttonWidth, buttonHeight);
            } else {
                x += (x > 0 ? AndroidUtilities.dp(marginX) : 0) + buttonWidth;
                button.setRect(x - buttonWidth, height, buttonWidth, buttonHeight);
            }
            width = Math.max(width, x);
        }
        if (x > 0 || height > 0) {
            height += buttonHeight;
        }
        lastLineWidth = x;
        isMeasured = true;
    }

    public int getInnerHeight() {
        return !buttons.isEmpty() && mode == MODE_INSIDE ? height + AndroidUtilities.dp(10) : 0;
    }

    public int getOuterHeight() {
        return !buttons.isEmpty() && mode == MODE_OUTSIDE ? height + AndroidUtilities.dp(8) : 0;
    }

    public void setOnClickListener(ReactionButtons.OnClickListener listener) {
        this.listener = listener;
    }

    public boolean onTouch(int action, float x, float y, long time) {
        if (action == MotionEvent.ACTION_DOWN) {
            pressedButton = getButtonAt(x, y);
            return pressedButton != null;
        } else
        if (action == MotionEvent.ACTION_UP) {
            if (pressedButton != null && listener != null) {
                listener.onClick(pressedButton, pressedButton.reaction, time > ViewConfiguration.getLongPressTimeout());
                return true;
            }
            pressedButton = null;
        }
        return false;
    }

    public Button getButtonAt(MotionEvent event, float dx, float dy) {
        return getButtonAt(event.getX() - AndroidUtilities.dp(dx), event.getY() - AndroidUtilities.dp(dy));
    }

    public Button getButtonAt(float x, float y) {
        for (Button button : buttons) {
            if (button.rect.contains(x, y)) {
                return button;
            }
        }
        return null;
    }

    public void draw(Canvas canvas) {
        draw(canvas, null);
    }

    public void draw(Canvas canvas, String singleReaction) {
        if (mode == MODE_MICRO) {
            for (Button button : buttons) {
                if (singleReaction != null && !singleReaction.equals(button.reaction)) {
                    continue;
                }
                button.icon.draw(canvas);
            }
            return;
        }

        float r = AndroidUtilities.dp(12);
        for (int i = 0; i < buttons.size(); i++) {
            Button button = buttons.get(i);
            if (singleReaction != null && !singleReaction.equals(button.reaction)) {
                continue;
            }

            roundPaint.setStyle(Paint.Style.FILL);
            if (mode == MODE_OUTSIDE) {
                roundPaint.setColor(Theme.getColor(Theme.key_chat_extReactionBackground));
                Theme.chat_reactionCountPaint.setColor(Theme.getColor(Theme.key_chat_extReactionText));
            } else
            if (isOutgoingMessage) {
                roundPaint.setColor(Theme.getColor(Theme.key_chat_outReactionBackground));
                Theme.chat_reactionCountPaint.setColor(Theme.getColor(Theme.key_chat_outReactionText));
            } else {
                roundPaint.setColor(Theme.getColor(Theme.key_chat_inReactionBackground));
                Theme.chat_reactionCountPaint.setColor(Theme.getColor(Theme.key_chat_inReactionText));
            }

            // Highlight override
            if (button.reaction.equals(highlightedReaction)) {
                roundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            }

            // Draw background
            canvas.drawRoundRect(button.rect, r, r, roundPaint);

            // Draw outline
            if (button.reaction.equals(activeReaction)) {
                roundPaint.setStyle(Paint.Style.STROKE);
                roundPaint.setStrokeWidth(AndroidUtilities.dp(1.33f));
                if (mode == MODE_OUTSIDE) {
                    roundPaint.setColor(Theme.getColor(Theme.key_chat_extReactionBorder));
                } else
                if (isOutgoingMessage) {
                    roundPaint.setColor(Theme.getColor(Theme.key_chat_outReactionBorder));
                } else {
                    roundPaint.setColor(Theme.getColor(Theme.key_chat_inReactionBorder));
                }
                canvas.drawRoundRect(button.rect, r, r, roundPaint);
            }

            // Draw icon
            button.icon.draw(canvas);

            if (button.avatars != null) {
                // Draw avatars (starting from back)
                // TODO: use AvatarsImageView instead
                for (int j = button.avatars.length - 1; j >= 0; j--) {
                    roundPaint.setStyle(Paint.Style.STROKE);
                    roundPaint.setStrokeWidth(AndroidUtilities.dp(1.33f));
                    if (mode == MODE_OUTSIDE) {
                        roundPaint.setColor(Theme.getColor(Theme.key_chat_extReactionBackground));
                    } else
                    if (isOutgoingMessage) {
                        roundPaint.setColor(Theme.getColor(Theme.key_chat_outReactionBackground));
                    } else {
                        roundPaint.setColor(Theme.getColor(Theme.key_chat_inReactionBackground));
                    }
                    ImageReceiver avatar = button.avatars[j];
                    RectF rect = new RectF(avatar.getImageX(), avatar.getImageY(), avatar.getImageX() + avatar.getImageWidth(), avatar.getImageY() + avatar.getImageHeight());
                    canvas.drawRoundRect(rect, r, r, roundPaint);
                    avatar.draw(canvas);
                }
            } else {
                // Draw count
                String str = String.format("%s", LocaleController.formatShortNumber(Math.max(1, button.count), null));
                canvas.drawText(str, button.rect.left + AndroidUtilities.dp(iconLeft + iconSize + labelLeft), button.rect.bottom - AndroidUtilities.dp(labelBottom), Theme.chat_reactionCountPaint);
            }
        }
    }
}
