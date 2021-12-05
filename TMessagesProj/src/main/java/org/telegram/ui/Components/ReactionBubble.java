package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class ReactionBubble extends FrameLayout {
    public static final int paddingLeft = 14;
    public static final int paddingRight = 14;
    public static final int paddingTop = 7;
    public static final int paddingBottom = 7; // total h = 44
    public static final int buttonSize = 30;
    public static final int buttonSpacing = 7;
    public static final int borderRadius = 22;
    public static final int overhangBottom = 20;
    public static final int marginLeft = 5;
    public static final int marginBottom = -10;
    public static final int marginRight = -44;

    // {x, y, size} for each of small decorative bubbles
    public static final int[][] bubbles = {{38, 43, 14}, {33, 57, 7}};

    public int width;
    public int height;
    public int innerWidth;
    public RectF bounds;

    private FrameLayout wrapperView;
    private HorizontalScrollView scrollView;
    private View buttonContainer;
    private ImageReceiver[] buttons;

    private Paint backPaint;
    private Path backPath;
    private Path clipPath;

    ArrayList<TLRPC.TL_availableReaction> reactions;

    public ReactionBubble(Context context, ArrayList<TLRPC.TL_availableReaction> reactions) {
        super(context);
        this.reactions = reactions;
        buttons = new ImageReceiver[reactions.size()];

        innerWidth = AndroidUtilities.dp(paddingLeft + buttons.length * buttonSize + (buttons.length - 1) * buttonSpacing + paddingRight);

        height = AndroidUtilities.dp(paddingTop + buttonSize + paddingBottom + overhangBottom);

        backPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        backPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        backPaint.setStyle(Paint.Style.FILL);
        backPaint.setShadowLayer(AndroidUtilities.dp(2), 0, AndroidUtilities.dp(0.3f), 0x3c000000);
        bounds = new RectF(0, 0, 0, 0);
        clipPath = new Path();
        backPath = new Path();

        wrapperView = new FrameLayout(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                backPath.reset();
                backPath.addRoundRect(bounds, AndroidUtilities.dp(borderRadius), AndroidUtilities.dp(borderRadius), Path.Direction.CW);
                for (int[] bubble : bubbles) {
                    backPath.addCircle(width - AndroidUtilities.dp(bubble[0]), AndroidUtilities.dp(bubble[1]), 0.5f * (float) AndroidUtilities.dp(bubble[2]), Path.Direction.CW);
                }
                canvas.drawPath(backPath, backPaint);
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                boolean result;
                canvas.save();
                clipPath.reset();
                clipPath.addRoundRect(bounds, AndroidUtilities.dp(borderRadius), AndroidUtilities.dp(borderRadius), Path.Direction.CW);
                canvas.clipPath(clipPath);
                result = super.drawChild(canvas, child, drawingTime);
                canvas.restore();
                return result;
            }
        };
        wrapperView.setWillNotDraw(false);
        addView(wrapperView, new FrameLayout.LayoutParams(0, height, Gravity.RIGHT | Gravity.TOP));

        scrollView = new HorizontalScrollView(context);
        wrapperView.addView(scrollView, new FrameLayout.LayoutParams(0, height));

        buttonContainer = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                for (ImageReceiver button : buttons) {
                    button.draw(canvas);
                }
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                setMeasuredDimension(innerWidth, height);
            }
        };
        buttonContainer.setWillNotDraw(false);
        scrollView.addView(buttonContainer, new FrameLayout.LayoutParams(innerWidth, height));

        for (int i = 0; i < buttons.length; i++) {
            TLRPC.TL_availableReaction reaction = reactions.get(i);
            buttons[i] = new ImageReceiver(buttonContainer);
            buttons[i].setImageCoords(
                AndroidUtilities.dp(paddingLeft + i * (buttonSize + buttonSpacing)),
                AndroidUtilities.dp(paddingTop),
                AndroidUtilities.dp(buttonSize),
                AndroidUtilities.dp(buttonSize)
            );
            buttons[i].setImage(ImageLocation.getForDocument(reaction.static_icon), "50_50", null, null, null, 0);
        }
    }

    public void setWidth(int maxWidth) {
        getLayoutParams().width = maxWidth;
        width = Math.min(innerWidth, maxWidth);
        bounds = new RectF(0, 0, width, height - AndroidUtilities.dp(overhangBottom));
        wrapperView.getLayoutParams().width = width;
        scrollView.getLayoutParams().width = width;
    }
}
