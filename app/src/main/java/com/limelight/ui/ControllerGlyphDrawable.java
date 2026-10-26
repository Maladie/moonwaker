package com.limelight.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import java.util.Locale;

/** The controller silhouette the console and the stream overlay both draw for a pad. */
public final class ControllerGlyphDrawable extends Drawable {
    /** Battery level ink shared by every controller pill: unknown, charging, critical, low, ok. */
    public static int batteryColor(int percentage, boolean charging) {
        return percentage < 0 ? 0xFFB3B8C8
                : charging ? 0xFF64B5F6
                : percentage <= 10 ? 0xFFFF5252
                : percentage <= 30 ? 0xFFFFB74D : 0xFF69F0AE;
    }

    private enum Kind { DUALSENSE, DUALSHOCK4, XBOX, STEAM, GENERIC }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path body = new Path();
    private final Kind kind;

    public ControllerGlyphDrawable(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (normalized.contains("dualsense")) {
            kind = Kind.DUALSENSE;
        } else if (normalized.contains("dualshock")
                || normalized.contains("wireless controller")) {
            // Android names a DualShock 4 "Wireless Controller"; a DualSense always says so.
            kind = Kind.DUALSHOCK4;
        } else if (normalized.contains("steam")) {
            kind = Kind.STEAM;
        } else if (normalized.contains("xbox") || normalized.contains("xinput")) {
            kind = Kind.XBOX;
        } else {
            kind = Kind.GENERIC;
        }
    }

    @Override public void draw(Canvas canvas) {
        float scale = Math.min(getBounds().width() / 34f, getBounds().height() / 24f);
        float x = getBounds().left + (getBounds().width() - 34f * scale) / 2f;
        float y = getBounds().top + (getBounds().height() - 24f * scale) / 2f;
        canvas.save();
        canvas.translate(x, y);
        canvas.scale(scale, scale);
        body.reset();
        if (kind == Kind.DUALSHOCK4) {
            // Flatter than the DualSense: a straight top edge carrying the light bar.
            body.moveTo(3f, 9f);
            body.cubicTo(4f, 6.5f, 8f, 5.5f, 12f, 6.5f);
            body.lineTo(22f, 6.5f);
            body.cubicTo(26f, 5.5f, 30f, 6.5f, 31f, 9f);
            body.lineTo(33f, 17f);
            body.cubicTo(34f, 21f, 29f, 23f, 26f, 20f);
            body.lineTo(22f, 16f);
            body.lineTo(12f, 16f);
            body.lineTo(8f, 20f);
            body.cubicTo(5f, 23f, 0f, 21f, 1f, 17f);
            body.close();
        } else {
            body.moveTo(3f, 10f);
            body.cubicTo(4f, 5f, 8f, 3f, 12f, 5f);
            body.cubicTo(15f, 6.5f, 19f, 6.5f, 22f, 5f);
            body.cubicTo(26f, 3f, 30f, 5f, 31f, 10f);
            body.lineTo(33f, 18f);
            body.cubicTo(34f, 22f, 29f, 24f, 26f, 21f);
            body.lineTo(22f, 17f);
            body.lineTo(12f, 17f);
            body.lineTo(8f, 21f);
            body.cubicTo(5f, 24f, 0f, 22f, 1f, 18f);
            body.close();
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(kind == Kind.DUALSENSE ? 0xFFE7EDF2
                : kind == Kind.DUALSHOCK4 ? 0xFF2B333B : 0xFF53606B);
        canvas.drawPath(body, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f);
        paint.setColor(0xFFBDEBFF);
        canvas.drawPath(body, paint);

        if (kind == Kind.DUALSENSE) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF202A33);
            canvas.drawRoundRect(new RectF(13f, 6f, 21f, 11f), 1.5f, 1.5f, paint);
            canvas.drawCircle(13f, 14f, 1.5f, paint);
            canvas.drawCircle(21f, 14f, 1.5f, paint);
        } else if (kind == Kind.DUALSHOCK4) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF64B5F6);
            canvas.drawRoundRect(new RectF(12.5f, 4.6f, 21.5f, 6.4f), 0.9f, 0.9f, paint);
            paint.setColor(0xFF11171C);
            canvas.drawRoundRect(new RectF(12.5f, 6.8f, 21.5f, 11.2f), 1f, 1f, paint);
            paint.setColor(0xFFDDE8EF);
            canvas.drawCircle(13f, 13.6f, 1.5f, paint);
            canvas.drawCircle(21f, 13.6f, 1.5f, paint);
        } else if (kind == Kind.STEAM) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.4f);
            paint.setColor(0xFFDDE8EF);
            canvas.drawCircle(10f, 11f, 3.2f, paint);
            canvas.drawCircle(24f, 11f, 3.2f, paint);
            canvas.drawCircle(17f, 15f, 1.4f, paint);
        } else {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.4f);
            paint.setColor(0xFFDDE8EF);
            canvas.drawLine(8f, 11f, 14f, 11f, paint);
            canvas.drawLine(11f, 8f, 11f, 14f, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(24f, 9f, 1.3f, paint);
            canvas.drawCircle(27f, 12f, 1.3f, paint);
            canvas.drawCircle(kind == Kind.XBOX ? 14f : 15f, 15f, 1.4f, paint);
            canvas.drawCircle(kind == Kind.XBOX ? 20f : 19f, 14f, 1.4f, paint);
        }
        canvas.restore();
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(android.graphics.ColorFilter filter) {
        paint.setColorFilter(filter);
    }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return 34; }
    @Override public int getIntrinsicHeight() { return 24; }
}
