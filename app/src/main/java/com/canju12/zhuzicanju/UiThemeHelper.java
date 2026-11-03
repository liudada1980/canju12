package com.canju12.zhuzicanju;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;

/**
 * 根据 room.png 提取主题色，并把按钮统一成“主色 + 轻遮罩 + 自动黑白字色”。
 */
public final class UiThemeHelper {
    private UiThemeHelper() {}

    /**
     * 行棋指示器优先使用 /canju12/uistyle/rk.png、bk.png，
     * 外置图片不可用时回退到内置 drawable。
     */
    public static void applyTurnIndicatorImage(Context context, ImageView imageView, int side) {
        if (context == null || imageView == null) return;

        boolean redSide = side == 1;
        Bitmap bitmap = UiAssetCache.getBitmap(context,
                redSide ? "rk.png" : "bk.png",
                redSide ? R.drawable.rk : R.drawable.bk);
        if (bitmap != null && !bitmap.isRecycled()) {
            imageView.setImageBitmap(bitmap);
        } else {
            imageView.setImageResource(redSide ? R.drawable.rk : R.drawable.bk);
        }
    }


    public static void applyRoomButtonTheme(Context context, View root, Bitmap preferredRoomBitmap) {
        if (context == null || root == null) return;

        ThemePalette palette = resolvePalette(context, preferredRoomBitmap);
        if (palette == null) return;

        applyRecursive(root, context, palette);
    }

    public static ThemePalette resolvePalette(Context context, Bitmap preferredRoomBitmap) {
        return buildPalette(context, preferredRoomBitmap);
    }

    private static void applyRecursive(View view, Context context, ThemePalette palette) {
        if (view instanceof Button) {
            styleButton((Button) view, context, palette);
        } else if (view instanceof TextView
                && palette.textColor == Color.WHITE
                && !isAnalysisOutputView(view)) {
            ((TextView) view).setTextColor(Color.WHITE);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyRecursive(group.getChildAt(i), context, palette);
            }
        }
    }

    private static boolean isAnalysisOutputView(View view) {
        int id = view.getId();
        if (id == R.id.tvCloudDb
                || id == R.id.tvAnalyzeResult
                || id == R.id.moveListContent) {
            return true;
        }

        ViewParent parent = view.getParent();
        while (parent instanceof View) {
            int parentId = ((View) parent).getId();
            if (parentId == R.id.tvCloudDb
                    || parentId == R.id.tvAnalyzeResult
                    || parentId == R.id.moveListContent) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private static void styleButton(Button button, Context context, ThemePalette palette) {
        if (button == null) return;
        button.setTextColor(palette.textColor);
        button.setBackground(createButtonBackground(context, palette));
    }

    private static StateListDrawable createButtonBackground(Context context, ThemePalette palette) {
        float radius = dp(context, 8);
        int strokeWidth = Math.max(1, Math.round(dp(context, 1)));

        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.RECTANGLE);
        normal.setCornerRadius(radius);
        normal.setColor(palette.normalFillColor);
        normal.setStroke(strokeWidth, palette.strokeColor);

        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.RECTANGLE);
        pressed.setCornerRadius(radius);
        pressed.setColor(palette.pressedFillColor);
        pressed.setStroke(strokeWidth, palette.strokeColor);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{android.R.attr.state_focused}, pressed);
        states.addState(new int[]{}, normal);
        return states;
    }

    private static ThemePalette buildPalette(Context context, Bitmap preferredRoomBitmap) {
        Bitmap bitmap = preferredRoomBitmap;

        if (bitmap == null) {
            bitmap = UiAssetCache.getRoom(context);
        }

        if (bitmap == null) {
            return null;
        }

        return createPalette(extractDominantColor(bitmap));
    }

    private static ThemePalette createPalette(int dominantColor) {
        double luminance = relativeLuminance(dominantColor);
        boolean lightBase = luminance >= 0.58;

        int maskColor = lightBase ? Color.BLACK : Color.WHITE;
        float maskAlpha = 0.20f;
        int buttonBackgroundAlpha = Math.round(255f * 0.70f);
        int normalFillColor = withAlpha(
                blendColors(dominantColor, maskColor, maskAlpha),
                buttonBackgroundAlpha);
        int pressedFillColor = withAlpha(
                blendColors(normalFillColor, Color.BLACK, 0.10f),
                buttonBackgroundAlpha);
        int textColor = bestTextColor(normalFillColor);
        int strokeColor = withAlpha(textColor, 120);

        return new ThemePalette(normalFillColor, pressedFillColor, textColor, strokeColor);
    }


    private static int extractDominantColor(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= 0 || height <= 0) {
            return 0xFFE8D5A2;
        }

        int step = Math.max(1, Math.min(width, height) / 64);
        int[] counts = new int[4096];
        long[] sumR = new long[4096];
        long[] sumG = new long[4096];
        long[] sumB = new long[4096];

        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int color = bitmap.getPixel(x, y);
                int alpha = Color.alpha(color);
                if (alpha < 32) continue;

                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);
                int index = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
                counts[index]++;
                sumR[index] += r;
                sumG[index] += g;
                sumB[index] += b;
            }
        }

        int bestIndex = -1;
        int bestCount = 0;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > bestCount) {
                bestCount = counts[i];
                bestIndex = i;
            }
        }

        if (bestIndex < 0 || bestCount == 0) {
            return 0xFFE8D5A2;
        }

        int r = (int) (sumR[bestIndex] / bestCount);
        int g = (int) (sumG[bestIndex] / bestCount);
        int b = (int) (sumB[bestIndex] / bestCount);
        return Color.rgb(r, g, b);
    }

    private static int bestTextColor(int backgroundColor) {
        double contrastWhite = contrastRatio(backgroundColor, Color.WHITE);
        double contrastBlack = contrastRatio(backgroundColor, Color.BLACK);
        return contrastWhite >= contrastBlack ? Color.WHITE : Color.BLACK;
    }

    private static double contrastRatio(int bgColor, int fgColor) {
        double l1 = relativeLuminance(bgColor);
        double l2 = relativeLuminance(fgColor);
        double lighter = Math.max(l1, l2);
        double darker = Math.min(l1, l2);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double relativeLuminance(int color) {
        double r = channelToLinear(Color.red(color) / 255.0);
        double g = channelToLinear(Color.green(color) / 255.0);
        double b = channelToLinear(Color.blue(color) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channelToLinear(double channel) {
        return channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static int blendColors(int baseColor, int overlayColor, float overlayAlpha) {
        overlayAlpha = clamp01(overlayAlpha);
        float baseAlpha = 1f - overlayAlpha;

        int r = Math.round(Color.red(baseColor) * baseAlpha + Color.red(overlayColor) * overlayAlpha);
        int g = Math.round(Color.green(baseColor) * baseAlpha + Color.green(overlayColor) * overlayAlpha);
        int b = Math.round(Color.blue(baseColor) * baseAlpha + Color.blue(overlayColor) * overlayAlpha);
        int a = Math.round(Color.alpha(baseColor) * baseAlpha + Color.alpha(overlayColor) * overlayAlpha);
        return Color.argb(a, r, g, b);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(clamp(alpha, 0, 255), Color.red(color), Color.green(color), Color.blue(color));
    }

    private static float dp(Context context, float value) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, dm);
    }

    private static float clamp01(float value) {
        if (value < 0f) return 0f;
        if (value > 1f) return 1f;
        return value;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class ThemePalette {
        final int normalFillColor;
        final int pressedFillColor;
        final int textColor;
        final int strokeColor;

        ThemePalette(int normalFillColor, int pressedFillColor, int textColor, int strokeColor) {
            this.normalFillColor = normalFillColor;
            this.pressedFillColor = pressedFillColor;
            this.textColor = textColor;
            this.strokeColor = strokeColor;
        }
    }
}
