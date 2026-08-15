package com.fantest.pokervision;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/**
 * Native playing-card renderer. It does not rely on Unicode playing-card-face glyphs,
 * so the same card appearance is used across Android devices and fonts.
 */
public final class CardFaceView extends View {
    private static final int RED = Color.rgb(196, 42, 42);
    private static final int BLACK = Color.rgb(18, 22, 20);
    private static final int GOLD = Color.rgb(245, 200, 76);
    private static final int EMPTY_BG = Color.rgb(229, 233, 231);
    private static final int EMPTY_TEXT = Color.rgb(130, 140, 135);

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();

    private PokerMath.Card card;
    private boolean selected;
    private boolean compact;

    public CardFaceView(Context context) { super(context); init(); }
    public CardFaceView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setContentDescription("Poker card slot");
        setClickable(true);
        setFocusable(true);
    }

    public void setCard(PokerMath.Card card) {
        this.card = card;
        updateDescription();
        invalidate();
    }

    public PokerMath.Card getCard() { return card; }

    public void setSelectedSlot(boolean selected) {
        this.selected = selected;
        setElevation(selected ? dp(3) : 0f);
        invalidate();
    }

    public void setCompact(boolean compact) {
        this.compact = compact;
        invalidate();
    }

    private void updateDescription() {
        setContentDescription(card == null ? "Empty poker card slot" : card.pretty());
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = selected ? dp(2.0f) : dp(1.0f);
        box.set(inset, inset, getWidth() - inset, getHeight() - inset);
        float radius = dp(8);

        fill.setStyle(Paint.Style.FILL);
        fill.setColor(card == null ? EMPTY_BG : Color.WHITE);
        canvas.drawRoundRect(box, radius, radius, fill);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(selected ? dp(3) : dp(1));
        stroke.setColor(selected ? GOLD : Color.rgb(175, 184, 180));
        if (selected) {
            stroke.setShadowLayer(dp(4), 0, 0, 0x88F5C84C);
        } else {
            stroke.clearShadowLayer();
        }
        canvas.drawRoundRect(box, radius, radius, stroke);

        if (card == null) {
            drawCentered(canvas, "+", compact ? 20 : 23, EMPTY_TEXT, true);
            return;
        }

        int color = (card.suit == 1 || card.suit == 2) ? RED : BLACK;
        String rank = PokerMath.rankText(card.rank);
        String suit = PokerMath.suitSymbol(card.suit);

        float left = box.left + dp(6);
        float top = box.top + dp(compact ? 12 : 14);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(color);
        text.setTextSize(sp(compact ? 13 : 15));
        canvas.drawText(rank, left, top, text);
        text.setTextSize(sp(compact ? 15 : 18));
        canvas.drawText(suit, left, top + dp(compact ? 16 : 19), text);

        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        text.setTextSize(sp(compact ? 24 : 30));
        canvas.drawText(suit, box.centerX(), baselineForCenter(text, box.centerY() + dp(3)), text);

        // Mirrored lower corner, like a physical playing card.
        canvas.save();
        canvas.rotate(180f, box.centerX(), box.centerY());
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(sp(compact ? 10 : 12));
        canvas.drawText(rank, left, top, text);
        text.setTextSize(sp(compact ? 12 : 14));
        canvas.drawText(suit, left, top + dp(compact ? 13 : 16), text);
        canvas.restore();
    }

    private void drawCentered(Canvas canvas, String value, float sp, int color, boolean bold) {
        text.setColor(color);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL));
        text.setTextSize(sp(sp));
        canvas.drawText(value, box.centerX(), baselineForCenter(text, box.centerY()), text);
    }

    private float baselineForCenter(Paint p, float centerY) {
        Paint.FontMetrics fm = p.getFontMetrics();
        return centerY - (fm.ascent + fm.descent) / 2f;
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }
}
