package com.hyperring;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AppIconLoader {

    private static final Map<String, Bitmap> iconCache = new ConcurrentHashMap<>();

    public static Bitmap getAppIcon(Context ctx, String pkgOrName, int sizePx) {
        if (pkgOrName == null || pkgOrName.isEmpty()) {
            return getDefaultNotificationIcon(sizePx);
        }

        String cacheKey = pkgOrName.toLowerCase().trim() + "_" + sizePx;
        Bitmap cached = iconCache.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }

        String pkg = resolvePackageName(pkgOrName);
        Bitmap loaded = null;

        if (ctx != null && pkg != null && !pkg.isEmpty()) {
            try {
                PackageManager pm = ctx.getPackageManager();
                Drawable drawable = pm.getApplicationIcon(pkg);
                if (drawable != null) {
                    loaded = drawableToSquircleBitmap(drawable, sizePx);
                }
            } catch (Throwable ignored) {}
        }

        if (loaded == null) {
            loaded = generateBrandedFallbackIcon(pkgOrName, sizePx);
        }

        if (loaded != null) {
            iconCache.put(cacheKey, loaded);
        }

        return loaded != null ? loaded : getDefaultNotificationIcon(sizePx);
    }

    public static String resolvePackageName(String nameOrPkg) {
        if (nameOrPkg == null) return "";
        String s = nameOrPkg.toLowerCase().trim();
        if (s.contains(".")) {
            return nameOrPkg.trim();
        }
        if (s.contains("telegram")) return "org.telegram.messenger";
        if (s.contains("whatsapp")) return "com.whatsapp";
        if (s.contains("chrome")) return "com.android.chrome";
        if (s.contains("play") || s.contains("vending")) return "com.android.vending";
        if (s.contains("gmail") || s.contains("email") || s.contains("mail")) return "com.google.android.gm";
        if (s.contains("youtube")) return "com.google.android.youtube";
        if (s.contains("instagram")) return "com.instagram.android";
        if (s.contains("discord")) return "com.discord";
        if (s.contains("twitter") || s.equals("x")) return "com.twitter.android";
        if (s.contains("spotify")) return "com.spotify.music";
        if (s.contains("message") || s.contains("sms")) return "com.google.android.apps.messaging";
        if (s.contains("dialer") || s.contains("phone") || s.contains("call")) return "com.google.android.dialer";
        if (s.contains("download")) return "com.android.providers.downloads.ui";
        return nameOrPkg;
    }

    public static Bitmap drawableToSquircleBitmap(Drawable drawable, int sizePx) {
        if (drawable == null) return null;

        Bitmap raw = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(raw);
        drawable.setBounds(0, 0, sizePx, sizePx);
        drawable.draw(c);

        Bitmap output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas outCanvas = new Canvas(output);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        RectF rect = new RectF(0, 0, sizePx, sizePx);
        float radius = sizePx * 0.24f; // standard squircle radius

        outCanvas.drawRoundRect(rect, radius, radius, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        outCanvas.drawBitmap(raw, 0, 0, paint);

        if (!raw.isRecycled()) raw.recycle();
        return output;
    }

    private static Bitmap generateBrandedFallbackIcon(String name, int sizePx) {
        String lower = name.toLowerCase().trim();
        int bgColor = Color.parseColor("#3B82F6");
        String glyph = "bell";

        if (lower.contains("telegram")) {
            bgColor = Color.parseColor("#2AABEE");
            glyph = "send";
        } else if (lower.contains("whatsapp")) {
            bgColor = Color.parseColor("#25D366");
            glyph = "chat";
        } else if (lower.contains("chrome") || lower.contains("browser")) {
            bgColor = Color.parseColor("#0F9D58");
            glyph = "globe";
        } else if (lower.contains("play") || lower.contains("vending")) {
            bgColor = Color.parseColor("#01875F");
            glyph = "play";
        } else if (lower.contains("youtube")) {
            bgColor = Color.parseColor("#FF0000");
            glyph = "play";
        } else if (lower.contains("mail") || lower.contains("gmail")) {
            bgColor = Color.parseColor("#EA4335");
            glyph = "mail";
        } else if (lower.contains("spotify")) {
            bgColor = Color.parseColor("#1DB954");
            glyph = "music";
        } else if (lower.contains("discord")) {
            bgColor = Color.parseColor("#5865F2");
            glyph = "chat";
        } else if (lower.contains("instagram")) {
            bgColor = Color.parseColor("#E1306C");
            glyph = "camera";
        } else if (lower.contains("download")) {
            bgColor = Color.parseColor("#0284C7");
            glyph = "download";
        }

        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(bgColor);
        RectF rect = new RectF(0, 0, sizePx, sizePx);
        float radius = sizePx * 0.24f;
        canvas.drawRoundRect(rect, radius, radius, paint);

        Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        iconPaint.setColor(Color.WHITE);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(Math.max(1.5f, sizePx * 0.08f));
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);

        float cx = sizePx / 2.0f;
        float cy = sizePx / 2.0f;
        float s = sizePx * 0.26f;

        if ("chat".equals(glyph)) {
            RectF bubble = new RectF(cx - s, cy - s * 0.8f, cx + s, cy + s * 0.8f);
            canvas.drawRoundRect(bubble, s * 0.4f, s * 0.4f, iconPaint);
            Path tail = new Path();
            tail.moveTo(cx - s * 0.4f, cy + s * 0.8f);
            tail.lineTo(cx - s * 0.7f, cy + s * 1.2f);
            tail.lineTo(cx - s * 0.1f, cy + s * 0.8f);
            canvas.drawPath(tail, iconPaint);
        } else if ("send".equals(glyph)) {
            Path plane = new Path();
            plane.moveTo(cx + s, cy - s * 0.8f);
            plane.lineTo(cx - s, cy);
            plane.lineTo(cx - s * 0.3f, cy + s * 0.4f);
            plane.lineTo(cx + s, cy - s * 0.8f);
            plane.close();
            canvas.drawPath(plane, iconPaint);
        } else if ("play".equals(glyph)) {
            iconPaint.setStyle(Paint.Style.FILL);
            Path play = new Path();
            play.moveTo(cx - s * 0.6f, cy - s);
            play.lineTo(cx + s * 0.9f, cy);
            play.lineTo(cx - s * 0.6f, cy + s);
            play.close();
            canvas.drawPath(play, iconPaint);
        } else if ("mail".equals(glyph)) {
            RectF env = new RectF(cx - s, cy - s * 0.7f, cx + s, cy + s * 0.7f);
            canvas.drawRoundRect(env, s * 0.2f, s * 0.2f, iconPaint);
            Path flap = new Path();
            flap.moveTo(cx - s, cy - s * 0.7f);
            flap.lineTo(cx, cy + s * 0.1f);
            flap.lineTo(cx + s, cy - s * 0.7f);
            canvas.drawPath(flap, iconPaint);
        } else if ("download".equals(glyph)) {
            canvas.drawLine(cx, cy - s, cx, cy + s * 0.6f, iconPaint);
            Path arr = new Path();
            arr.moveTo(cx - s * 0.6f, cy + s * 0.1f);
            arr.lineTo(cx, cy + s * 0.7f);
            arr.lineTo(cx + s * 0.6f, cy + s * 0.1f);
            canvas.drawPath(arr, iconPaint);
            canvas.drawLine(cx - s * 0.8f, cy + s, cx + s * 0.8f, cy + s, iconPaint);
        } else {
            // Default bell icon
            Path bell = new Path();
            bell.moveTo(cx, cy - s);
            bell.cubicTo(cx - s * 0.8f, cy - s, cx - s * 0.8f, cy + s * 0.5f, cx - s, cy + s * 0.7f);
            bell.lineTo(cx + s, cy + s * 0.7f);
            bell.cubicTo(cx + s * 0.8f, cy + s * 0.5f, cx + s * 0.8f, cy - s, cx, cy - s);
            canvas.drawPath(bell, iconPaint);
            canvas.drawCircle(cx, cy + s * 1.05f, s * 0.25f, iconPaint);
        }

        return bmp;
    }

    private static Bitmap getDefaultNotificationIcon(int sizePx) {
        String key = "_default_notif_" + sizePx;
        Bitmap b = iconCache.get(key);
        if (b != null && !b.isRecycled()) return b;

        Bitmap created = generateBrandedFallbackIcon("generic", sizePx);
        iconCache.put(key, created);
        return created;
    }

    public static void clearCache() {
        for (Bitmap b : iconCache.values()) {
            if (b != null && !b.isRecycled()) b.recycle();
        }
        iconCache.clear();
    }
}
