package hev.sockstun;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

/** Presentation-only primitives shared by the native screens. */
public final class PremiumUi {
    public static final int BG = 0xFF091323;
    public static final int CARD = 0xFF132338;
    public static final int BORDER = 0xFF263B50;
    public static final int TEXT = 0xFFEDF6FF;
    public static final int MUTED = 0xFF8AA2BA;
    public static final int CYAN = 0xFF00DCEB;
    public static final int RED = 0xFFFF6278;

    private PremiumUi() { }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static Drawable surface(Context context, int fill, int border, float radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(context, radiusDp));
        if (border != Color.TRANSPARENT) shape.setStroke(dp(context, 1), border);
        return shape;
    }

    public static void setupWindow(Activity activity) {
        activity.getWindow().setStatusBarColor(BG);
        activity.getWindow().setNavigationBarColor(BG);
        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        View decor = activity.getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(flags);
        if (Build.VERSION.SDK_INT >= 29) {
            activity.getWindow().setStatusBarContrastEnforced(false);
            activity.getWindow().setNavigationBarContrastEnforced(false);
        }
    }

    public static void styleInput(EditText input) {
        Context context = input.getContext();
        input.setTextColor(new ColorStateList(new int[][] {
                new int[] {-android.R.attr.state_enabled}, new int[] {}},
                new int[] {MUTED, TEXT}));
        input.setHintTextColor(MUTED);
        input.setTextSize(15);
        input.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        input.setMinHeight(dp(context, 52));
        input.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        input.setCompoundDrawablePadding(dp(context, 12));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {-android.R.attr.state_enabled}, surface(context, 0xFF102033, BORDER, 12));
        states.addState(new int[] {android.R.attr.state_focused}, surface(context, CARD, CYAN, 12));
        states.addState(new int[] {}, surface(context, CARD, BORDER, 12));
        input.setBackgroundTintList(null);
        input.setBackground(states);
    }

    public static void styleButton(Button button, String iconName, boolean primary, boolean danger) {
        Context context = button.getContext();
        int accent = danger ? RED : CYAN;
        int text = primary ? BG : (danger ? RED : TEXT);
        button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextSize(15);
        button.setMinHeight(dp(context, 52));
        button.setMinimumHeight(dp(context, 52));
        button.setMinimumWidth(0);
        button.setMinWidth(0);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));
        button.setTextColor(new ColorStateList(new int[][] {
                new int[] {-android.R.attr.state_enabled}, new int[] {}},
                new int[] {MUTED, text}));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {-android.R.attr.state_enabled}, surface(context, 0xFF102033, BORDER, 12));
        states.addState(new int[] {android.R.attr.state_focused}, surface(context, primary ? accent : CARD, accent, 12));
        states.addState(new int[] {}, surface(context, primary ? accent : CARD, danger ? RED : (primary ? accent : BORDER), 12));
        button.setBackgroundTintList(null);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x336FDFF1), states,
                surface(context, Color.WHITE, Color.TRANSPARENT, 12)));
        button.setStateListAnimator(null);
        button.setElevation(0);
        button.setCompoundDrawablePadding(dp(context, 10));
        button.setCompoundDrawablesRelative(iconName == null || iconName.isEmpty() ? null : icon(context, iconName, text), null, null, null);
    }

    public static LinearLayout bottomNav(final Activity activity, String active) {
        Context context = activity;
        LinearLayout nav = new LinearLayout(context);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(context, 4), dp(context, 5), dp(context, 4), dp(context, 5));
        nav.setBackground(surface(context, BG, BORDER, 16));
        final String[] labels = {"Proxies", "Numbers", "Apps", "Quick Copy"};
        String[] icons = {"proxy", "phone", "apps", "copy"};
        int[] ids = {R.id.tab_proxies, R.id.tab_numbers, R.id.tab_apps, R.id.tab_quick_copy};
        final Class<?>[] destinations = {MainActivity.class, NumbersActivity.class,
                AppListActivity.class, QuickCopyActivity.class};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            boolean selected = labels[i].equalsIgnoreCase(active);
            int color = selected ? CYAN : MUTED;
            Button button = new Button(context);
            button.setId(ids[i]);
            button.setText(labels[i]);
            button.setAllCaps(false);
            button.setTextSize(11);
            button.setTextColor(color);
            button.setTypeface(Typeface.create(selected ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
            button.setGravity(Gravity.CENTER);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setMinHeight(dp(context, 62));
            button.setMinimumHeight(dp(context, 62));
            button.setPadding(dp(context, 2), dp(context, 6), dp(context, 2), dp(context, 6));
            button.setCompoundDrawablePadding(dp(context, 5));
            button.setCompoundDrawables(null, icon(context, icons[i], color), null, null);
            button.setBackgroundTintList(null);
            button.setStateListAnimator(null);
            button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x226FDFF1),
                    surface(context, selected ? 0xFF0D293A : BG, Color.TRANSPARENT, 10), null));
            button.setContentDescription(labels[i] + (selected ? ", selected" : ""));
            button.setSelected(selected);
            if (!selected) button.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    Intent intent = new Intent(activity, destinations[index]);
                    if (destinations[index] == MainActivity.class) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    }
                    activity.startActivity(intent);
                    if (!(activity instanceof MainActivity)) activity.finish();
                }
            });
            nav.addView(button, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        }
        return nav;
    }

    public static Drawable icon(Context context, String name, int color) {
        return new OutlineIcon(name, color, dp(context, 20));
    }

    private static final class OutlineIcon extends Drawable {
        private final String name;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int size;
        private int alpha = 255;
        OutlineIcon(String name, int color, int size) {
            this.name = name == null ? "" : name;
            this.size = size;
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.8f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            setBounds(0, 0, size, size);
        }
        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
        @Override public void draw(Canvas canvas) {
            int save = canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
            paint.setAlpha(alpha);
            if ("proxy".equals(name)) {
                path(canvas, 12, 2, 21, 6, 20, 14, 17, 19, 12, 22, 7, 19, 4, 14, 3, 6, 12, 2);
            } else if ("users".equals(name)) {
                canvas.drawCircle(12, 7, 3, paint);
                canvas.drawArc(5, 13, 19, 25, 180, 180, false, paint);
                canvas.drawArc(1, 4, 8, 11, 230, 200, false, paint);
                canvas.drawArc(16, 4, 23, 11, 110, 200, false, paint);
                path(canvas, 2, 20, 2, 17, 4, 14); path(canvas, 22, 20, 22, 17, 20, 14);
            } else if ("lock".equals(name)) {
                canvas.drawRoundRect(4, 10, 20, 22, 2, 2, paint);
                canvas.drawArc(7, 2, 17, 14, 180, 180, false, paint);
            } else if ("key".equals(name)) {
                canvas.drawCircle(16, 7, 5, paint);
                path(canvas, 12, 10, 3, 19, 3, 22, 6, 22, 6, 19, 9, 19, 9, 16, 12, 13);
                canvas.drawCircle(17, 6, .6f, paint);
            } else if ("reset".equals(name)) {
                canvas.drawArc(3, 3, 21, 21, 220, 310, false, paint);
                path(canvas, 3, 2, 3, 8, 9, 8);
            } else if ("overlay".equals(name)) {
                canvas.drawCircle(12, 12, 9, paint); canvas.drawCircle(12, 12, 5, paint);
            } else if ("stop".equals(name)) {
                canvas.drawCircle(12, 12, 9, paint); canvas.drawRect(8, 8, 16, 16, paint);
            } else if ("back".equals(name)) {
                path(canvas, 10, 4, 2, 12, 10, 20); path(canvas, 2, 12, 22, 12);
            } else if ("chevron".equals(name)) {
                path(canvas, 9, 5, 16, 12, 9, 19);
            } else if ("chevron_left".equals(name)) {
                path(canvas, 15, 5, 8, 12, 15, 19);
            } else if ("menu".equals(name)) {
                canvas.drawCircle(12, 4, 1, paint);
                canvas.drawCircle(12, 12, 1, paint);
                canvas.drawCircle(12, 20, 1, paint);
            } else if ("phone".equals(name)) {
                path(canvas, 5, 3, 2, 6, 4, 12, 9, 18, 16, 22, 21, 19, 17, 14, 14, 16, 10, 13, 8, 9, 10, 7, 7, 2, 5, 3);
            } else if ("apps".equals(name)) {
                canvas.drawRect(3, 3, 9, 9, paint); canvas.drawRect(15, 3, 21, 9, paint);
                canvas.drawRect(3, 15, 9, 21, paint); canvas.drawRect(15, 15, 21, 21, paint);
            } else if ("copy".equals(name)) {
                path(canvas, 7, 2, 16, 2, 21, 7, 21, 21, 7, 21, 7, 2);
                path(canvas, 16, 2, 16, 7, 21, 7); path(canvas, 11, 11, 17, 11);
                path(canvas, 11, 15, 17, 15); path(canvas, 3, 6, 3, 22);
            } else if ("save".equals(name)) {
                path(canvas, 3, 2, 18, 2, 22, 6, 22, 22, 3, 22, 3, 2);
                canvas.drawRect(7, 2, 16, 9, paint); canvas.drawRect(7, 14, 18, 22, paint);
            } else if ("server".equals(name)) {
                canvas.drawRoundRect(3, 3, 21, 10, 1, 1, paint);
                canvas.drawRoundRect(3, 14, 21, 21, 1, 1, paint);
                path(canvas, 6, 6.5f, 7, 6.5f); path(canvas, 6, 17.5f, 7, 17.5f);
                path(canvas, 15, 6.5f, 18, 6.5f); path(canvas, 15, 17.5f, 18, 17.5f);
            } else if ("power".equals(name)) {
                canvas.drawArc(3, 3, 21, 21, -48, 276, false, paint); path(canvas, 12, 1, 12, 11);
            } else if ("close".equals(name)) {
                path(canvas, 5, 5, 19, 19); path(canvas, 19, 5, 5, 19);
            } else if ("dns".equals(name)) {
                canvas.drawCircle(12, 12, 9, paint);
                canvas.drawOval(8, 3, 16, 21, paint); path(canvas, 3, 12, 21, 12);
            } else if ("port".equals(name)) {
                path(canvas, 8, 3, 16, 3); path(canvas, 12, 3, 12, 12);
                path(canvas, 5, 16, 5, 12, 19, 12, 19, 16);
                canvas.drawRect(2, 16, 8, 22, paint); canvas.drawRect(16, 16, 22, 22, paint);
            } else {
                canvas.drawCircle(12, 12, 8, paint);
            }
            canvas.restoreToCount(save);
        }
        private void path(Canvas canvas, float... points) {
            Path path = new Path();
            path.moveTo(points[0], points[1]);
            for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
            canvas.drawPath(path, paint);
        }
        @Override public void setAlpha(int alpha) { this.alpha = alpha; invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
