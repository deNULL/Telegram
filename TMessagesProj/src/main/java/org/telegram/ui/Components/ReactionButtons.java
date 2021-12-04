package org.telegram.ui.Components;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
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

    public final int iconTop = 4;
    public final int iconLeft = 8;
    public final int iconSize = 18;
    public final int labelLeft = 5;
    public final int labelRight = 9;
    public final int labelBottom = 9;
    public final int avatarLeft = 5;
    public final int avatarOffset = 8;
    public final int avatarSize = 16;

    public static final int MODE_MICRO = 0; // small static icons (max 2) in the timestamp
    public static final int MODE_INSIDE = 1; // full-sized buttons inside the bubble
    public static final int MODE_OUTSIDE = 2; // full-sized buttons outside the bubble
    public int mode;

    // false: show only the number of reactions, true: show photos of last people reacted (up to 3)
    public boolean showLastReactions;
    public boolean isOutgoingMessage;

    // reaction of the current user, or "" if none
    public String activeReaction = "";

    public float maxWidth;
    public float width;
    public float height;
    public float lastLineWidth;

    public View parentView;

    public class Button {
        public int count;
        public RectF rect;
        public ImageReceiver icon;
        public ImageReceiver[] avatars;

        public Button(TLRPC.TL_reactionCount reactionCount, ArrayList<Long> recentUserIds) {
            TLRPC.TL_availableReaction reaction = ReactionsController.getInstance(UserConfig.selectedAccount).getReaction(reactionCount.reaction);

            count = reactionCount.count;
            rect = new RectF(0, 0, 0, 0);
            icon = new ImageReceiver();
            icon.setParentView(parentView);
            if (reaction == null) {
                return;
            }

            icon.setImage(ImageLocation.getForDocument(reaction.static_icon), "50_50", null, null, null, 0);
            if (recentUserIds != null) {
                avatars = new ImageReceiver[recentUserIds.size()];
                for (int i = 0; i < avatars.length; i++) {
                    TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(recentUserIds.get(i));
                    avatars[i] = new ImageReceiver();
                    avatars[i].setParentView(parentView);
                    avatars[i].setRoundRadius(AndroidUtilities.dp(avatarSize));
                    avatars[i].setImage(ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL), "50_50", null, null, null, 0);
                }
            }
        }

        public void setRect(float x, float y, float w, float h) {
            rect = new RectF(x, y, x + w, y + h);
            icon.setImageCoords(
                x + AndroidUtilities.dp(iconLeft),
                y + AndroidUtilities.dp(iconTop),
                AndroidUtilities.dp(iconSize),
                AndroidUtilities.dp(iconSize)
            );
            if (avatars != null) {
                for (int i = 0; i < avatars.length; i++) {
                    avatars[i].setImageCoords(
                            x + AndroidUtilities.dp(iconLeft + avatarLeft + avatarOffset * i),
                            y + AndroidUtilities.dp(iconTop),
                            AndroidUtilities.dp(avatarSize),
                            AndroidUtilities.dp(avatarSize)
                    );
                }
            }
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
        buttons.clear();
        if (reactions != null && reactions.results != null) {
            HashMap<String, ArrayList<Long>> recentReactions = new HashMap<>();
            for (TLRPC.TL_messageUserReaction userReaction : reactions.recent_reactons) {
                if (!recentReactions.containsKey(userReaction.reaction)) {
                    recentReactions.put(userReaction.reaction, new ArrayList<>());
                }
                recentReactions.get(userReaction.reaction).add(userReaction.user_id);
            }

            boolean allRecentFit = true;
            for (TLRPC.TL_reactionCount reactionCount : reactions.results) {
                ArrayList<Long> recentUserIds = recentReactions.get(reactionCount.reaction);
                if ((recentUserIds == null ? 0 : recentUserIds.size()) < reactionCount.count) {
                    allRecentFit = false;
                }
            }

            for (TLRPC.TL_reactionCount reactionCount : reactions.results) {
                ArrayList<Long> recentUserIds = recentReactions.get(reactionCount.reaction);
                buttons.add(new Button(reactionCount, allRecentFit ? (recentUserIds == null ? new ArrayList<>() : recentUserIds) : null));
            }
        }
    }

    public void setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
    }

    public void setOptions(int mode, boolean showLastReactions, boolean isOutgoingMessage) {
        this.mode = mode;
        this.showLastReactions = showLastReactions;
        this.isOutgoingMessage = isOutgoingMessage;
    }

    public float measureButtonWidth(int count) {
        String str = String.format("%s", LocaleController.formatShortNumber(Math.max(1, count), null));
        return AndroidUtilities.dp(iconLeft + iconSize + labelLeft) + Theme.chat_reactionCountPaint.measureText(str) + AndroidUtilities.dp(labelRight);
    }

    public float measureButtonHeight() {
        return AndroidUtilities.dp(26);
    }

    public void measure() {
        width = 0;
        height = 0;
        float x = 0;
        float buttonHeight = measureButtonHeight();
        for (Button button : buttons) {
            float buttonWidth = measureButtonWidth(button.count);
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
    }

    public float getInnerHeight() {
        return mode == MODE_INSIDE ? height + AndroidUtilities.dp(12) : 0;
    }

    public float getOuterHeight() {
        return mode == MODE_OUTSIDE ? height + AndroidUtilities.dp(4) : 0;
    }

    public boolean onTouch(int action, float x, float y) {

        return true;
    }

    public void draw(Canvas canvas) {
        float r = AndroidUtilities.dp(12);
        for (int i = 0; i < buttons.size(); i++) {
            Button button = buttons.get(i);
            TLRPC.TL_reactionCount reactionCount = reactions.results.get(i);

            if (mode == MODE_OUTSIDE) {
                roundPaint.setColor(Theme.getColor(Theme.key_chat_extReactionBackground));
                Theme.chat_reactionCountPaint.setColor(Theme.getColor(Theme.key_chat_extReactionText));
            } else {
                roundPaint.setColor(Theme.getColor(Theme.key_chat_inReactionBackground));
                Theme.chat_reactionCountPaint.setColor(Theme.getColor(Theme.key_chat_inReactionText));
            }

            // Draw background
            if (mode != MODE_MICRO) {
                canvas.drawRoundRect(button.rect, r, r, roundPaint);
            }

            // Draw icon
            button.icon.draw(canvas);

            if (button.avatars != null) {
                // Draw avatars
                for (ImageReceiver avatar : button.avatars) {
                    avatar.draw(canvas);
                }
            } else {
                // Draw count
                String str = String.format("%s", LocaleController.formatShortNumber(Math.max(1, reactionCount.count), null));
                canvas.drawText(str, button.rect.left + AndroidUtilities.dp(iconLeft + iconSize + labelLeft), button.rect.bottom - AndroidUtilities.dp(labelBottom), Theme.chat_reactionCountPaint);
            }
        }
    }
}
