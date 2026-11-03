package com.canju12.zhuzicanju;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class ToastUtil {
    private static volatile Bitmap circularLogo;
    private static final Object LOGO_LOCK = new Object();

    private ToastUtil() {}

    public static Toast makeText(Context context, CharSequence message, int duration) {
        Context appContext = context == null ? null : context.getApplicationContext();
        if (appContext == null) {
            throw new IllegalArgumentException("context == null");
        }

        LinearLayout toastLayout = new LinearLayout(appContext);
        toastLayout.setOrientation(LinearLayout.HORIZONTAL);
        toastLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);
        toastLayout.setPadding(dp(appContext, 12), dp(appContext, 8), dp(appContext, 12), dp(appContext, 8));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(appContext, 24));
        toastLayout.setBackground(background);
        toastLayout.setElevation(dp(appContext, 4));

        ImageView icon = new ImageView(appContext);
        icon.setImageBitmap(createCircularLogo(appContext));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(appContext, 18), dp(appContext, 18));
        iconParams.setMargins(0, 0, dp(appContext, 6), 0);
        toastLayout.addView(icon, iconParams);

        TextView textView = new TextView(appContext);
        textView.setText(message != null ? message.toString().replace("\r", " ").replace("\n", " ") : "");
        textView.setTextColor(Color.BLACK);
        textView.setTextSize(14);
        textView.setSingleLine(true);
        textView.setMaxLines(1);
        textView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textView.setGravity(android.view.Gravity.CENTER_VERTICAL);
        textView.setIncludeFontPadding(false);
        int maxWidth = Math.max(dp(appContext, 160),
                appContext.getResources().getDisplayMetrics().widthPixels - dp(appContext, 80));
        textView.setMaxWidth(maxWidth);
        toastLayout.addView(textView, new LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

        Toast toast = new Toast(appContext);
        toast.setDuration(duration);
        toast.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL,
                0, dp(appContext, 80));
        toast.setView(toastLayout);
        return toast;
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static Bitmap createCircularLogo(Context context) {
        Bitmap cached = circularLogo;
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }
        synchronized (LOGO_LOCK) {
            cached = circularLogo;
            if (cached != null && !cached.isRecycled()) {
                return cached;
            }

        Bitmap source = android.graphics.BitmapFactory.decodeResource(
                context.getResources(), R.mipmap.logo);
        if (source == null) {
            return null;
        }

        int size = Math.min(source.getWidth(), source.getHeight());
        if (size <= 0) {
            return source;
        }

        int left = Math.max(0, (source.getWidth() - size) / 2);
        int top = Math.max(0, (source.getHeight() - size) / 2);
        Bitmap square = Bitmap.createBitmap(source, left, top, size, size);
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        android.graphics.RectF rect = new android.graphics.RectF(0, 0, size, size);
        canvas.drawARGB(0, 0, 0, 0);
        canvas.drawOval(rect, paint);
        paint.setXfermode(new android.graphics.PorterDuffXfermode(
                android.graphics.PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(square, 0, 0, paint);
        if (square != source && !square.isRecycled()) {
            square.recycle();
        }
        if (source != null && !source.isRecycled()) {
            source.recycle();
        }
            circularLogo = output;
            return output;
        }
    }
}
