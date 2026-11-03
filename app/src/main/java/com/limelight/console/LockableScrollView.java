package com.limelight.console;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import com.limelight.R;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ScrollView;

/**
 * Scroll host that can pin a footer (the panel legend) inside the bottom padding where the
 * content never scrolls, with a chevron while more rows can scroll into view.
 */
final class LockableScrollView extends ScrollView {
    private View footer;
    private Drawable more;
    private int footerBottom;

    LockableScrollView(Context context) { super(context); }
    LockableScrollView(Context context, AttributeSet attributes) { super(context, attributes); }

    /** {@code footer} is drawn {@code bottomMargin} above the bottom edge; null removes it. */
    void setFooter(View footer, int bottomMargin) {
        if (this.footer == footer && footerBottom == bottomMargin) return;
        this.footer = footer;
        footerBottom = bottomMargin;
        requestLayout();
        invalidate();
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (footer == null) return;
        footer.measure(MeasureSpec.makeMeasureSpec(right - left, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        footer.layout(0, 0, footer.getMeasuredWidth(), footer.getMeasuredHeight());
    }

    @Override public void draw(Canvas canvas) {
        super.draw(canvas);
        if (footer == null) return;
        // The canvas follows the scroll offset; undo it so the footer stays put.
        canvas.save();
        canvas.translate(getScrollX(),
                getScrollY() + getHeight() - footerBottom - footer.getHeight());
        footer.draw(canvas);
        canvas.restore();
        drawMoreMarker(canvas, 1);
        drawMoreMarker(canvas, -1);
    }

    /** A chevron at the top or bottom of the visible rows while more content can scroll that way. */
    private void drawMoreMarker(Canvas canvas, int direction) {
        if (!canScrollVertically(direction)) return;
        if (more == null) {
            more = getContext().getDrawable(R.drawable.ic_console_chevron).mutate();
            more.setColorFilter(0xCCFFFFFF, PorterDuff.Mode.SRC_IN);
        }
        float density = getResources().getDisplayMetrics().density;
        int size = Math.round(18 * density);
        int top = direction > 0 ? getHeight() - getPaddingBottom() - size
                : getPaddingTop() - Math.round(10 * density);
        more.setBounds(0, 0, size, size);
        canvas.save();
        canvas.translate(getScrollX() + (getWidth() - size) / 2f, getScrollY() + top);
        canvas.rotate(direction > 0 ? 90 : -90, size / 2f, size / 2f);
        more.draw(canvas);
        canvas.restore();
    }
}
