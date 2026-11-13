package com.limelight.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.os.Build;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.GridView;

import com.limelight.R;

/**
 * Helpers for the Xbox-style dashboard look.
 */
public final class XboxUi {
    private XboxUi() {}

    public static final String BACKGROUND_DARK_GREY = "dark_grey";
    public static final String BACKGROUND_DASHBOARD = "dashboard";

    private static final String PREFS_NAME = "xbox_ui";
    private static final String PREF_BACKGROUND = "background";

    /**
     * The chosen background: Dark Grey by default, or the XBOX Dashboard image.
     */
    public static String getBackground(Context context) {
        String value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_BACKGROUND, BACKGROUND_DARK_GREY);
        // Earlier test builds saved the image option as "default" or "blur_glass"
        if ("default".equals(value) || "blur_glass".equals(value)) {
            return BACKGROUND_DASHBOARD;
        }
        return value;
    }

    public static boolean isDashboardBackground(Context context) {
        return BACKGROUND_DASHBOARD.equals(getBackground(context));
    }

    public static void setBackground(Context context, String background) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_BACKGROUND, background)
                .apply();
    }

    /**
     * Applies the chosen app background (Dark Grey by default, or the XBOX Dashboard image) to this
     * activity's window. Safe to call again in onResume() to pick up a new choice.
     */
    public static void applyBackground(Activity activity) {
        if (isDashboardBackground(activity)) {
            activity.getWindow().setBackgroundDrawableResource(R.drawable.xbox_background);
        }
        else {
            activity.getWindow().setBackgroundDrawableResource(R.color.xbox_dark_grey);
        }
    }

    /**
     * Makes the MoonlightX logo in the header open the background picker.
     */
    public static void attachLogoClick(final Activity activity) {
        View logo = activity.findViewById(R.id.xboxLogo);
        if (logo != null) {
            logo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    activity.startActivity(new Intent(activity, BackgroundPickerActivity.class));
                }
            });
        }
    }

    /**
     * Hides the system status bar for a clean dashboard look and trims the
     * header on narrow screens. Call after setContentView().
     */
    public static void applyChrome(Activity activity) {
        applyBackground(activity);
        attachLogoClick(activity);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = activity.getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
        else {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        // Narrow (portrait phone) screens: keep the logo and buttons, drop the home title text
        if (activity.getResources().getConfiguration().screenWidthDp < 480) {
            View title = activity.findViewById(R.id.xboxTitleBlock);
            if (title != null) {
                title.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Tile highlight and layout for the PC and game grids:
     * - The white outline follows navigation: only the tile selected with the controller/remote
     *   is highlighted, and only while the grid has focus (moving up to the header buttons
     *   clears it). With touch, the outline shows while a tile is pressed.
     * - Tiles are truly centered: the column count is limited to the number of tiles, so one or
     *   two tiles sit in the middle of the screen instead of in the first slots of a wide grid.
     */
    public static void attachTileHighlight(final AbsListView listView) {
        listView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refresh(listView);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                refresh(listView);
            }
        });

        listView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                refresh(listView);
            }
        });

        listView.getViewTreeObserver().addOnTouchModeChangeListener(new ViewTreeObserver.OnTouchModeChangeListener() {
            @Override
            public void onTouchModeChanged(boolean isInTouchMode) {
                refresh(listView);
            }
        });

        // Runs after data changes, scrolling and rotation: re-center and re-apply the highlight
        listView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (listView instanceof GridView) {
                    fitColumns((GridView) listView);
                }
                refresh(listView);
            }
        });
    }

    private static void fitColumns(GridView grid) {
        int count = grid.getCount();
        int columnWidth = grid.getRequestedColumnWidth();
        int spacing = grid.getRequestedHorizontalSpacing();
        if (count <= 0 || columnWidth <= 0 || grid.getWidth() <= 0) {
            return;
        }

        // Remember the side padding from the layout; we add to it to center the tiles
        Object stored = grid.getTag(R.id.xbox_base_padding);
        int basePadding;
        if (stored instanceof Integer) {
            basePadding = (Integer) stored;
        }
        else {
            basePadding = grid.getPaddingLeft();
            grid.setTag(R.id.xbox_base_padding, basePadding);
        }

        int available = grid.getWidth() - 2 * basePadding;
        if (available <= 0) {
            return;
        }

        int fit = Math.max(1, (available + spacing) / (columnWidth + spacing));
        int columns = Math.min(count, fit);
        if (grid.getNumColumns() != columns) {
            grid.setNumColumns(columns);
        }

        // GridView only centers a tile inside its own column, never the row of columns,
        // so pad both sides equally to put the row of tiles in the middle of the screen
        int used = columns * columnWidth + (columns - 1) * spacing;
        int side = basePadding + Math.max(0, (available - used) / 2);
        if (grid.getPaddingLeft() != side || grid.getPaddingRight() != side) {
            grid.setPadding(side, grid.getPaddingTop(), side, grid.getPaddingBottom());
        }
    }

    private static void refresh(AbsListView listView) {
        int highlighted = AdapterView.INVALID_POSITION;
        if (listView.hasFocus() && !listView.isInTouchMode()) {
            highlighted = listView.getSelectedItemPosition();
        }

        int first = listView.getFirstVisiblePosition();
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            boolean activated = (first + i) == highlighted;
            if (child.isActivated() != activated) {
                child.setActivated(activated);
            }
        }
    }

    /**
     * Builds the controller shortcut tips shown under the game list: three centered rows,
     * with the Menu, View and Xbox (Guide) buttons drawn as icons sized to the text.
     */
    public static CharSequence buildShortcutsText(Context context, float textSizePx) {
        String html = context.getString(R.string.xbox_shortcuts_html);
        Spanned parsed;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            parsed = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        }
        else {
            parsed = Html.fromHtml(html);
        }

        SpannableStringBuilder text = new SpannableStringBuilder(parsed);
        int iconSize = Math.round(textSizePx * 1.25f);
        replaceTokenWithIcon(context, text, "{menu}", R.drawable.ic_xbox_btn_menu, iconSize);
        replaceTokenWithIcon(context, text, "{view}", R.drawable.ic_xbox_btn_view, iconSize);
        replaceTokenWithIcon(context, text, "{xbox}", R.drawable.moonlightx_icon, iconSize);
        return text;
    }

    private static void replaceTokenWithIcon(Context context, SpannableStringBuilder text,
                                             String token, int drawableId, int size) {
        int index;
        while ((index = text.toString().indexOf(token)) >= 0) {
            Drawable icon = context.getResources().getDrawable(drawableId).mutate();
            icon.setBounds(0, 0, size, size);
            text.replace(index, index + token.length(), "\u00A0");
            text.setSpan(new CenteredImageSpan(icon), index, index + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    /**
     * Image span that is vertically centered on the text line instead of sitting on the baseline.
     */
    private static class CenteredImageSpan extends ImageSpan {
        CenteredImageSpan(Drawable drawable) {
            super(drawable);
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            Rect bounds = getDrawable().getBounds();
            if (fm != null) {
                Paint.FontMetricsInt textFm = paint.getFontMetricsInt();
                int textCenter = (textFm.descent + textFm.ascent) / 2;
                int half = bounds.height() / 2;
                fm.ascent = Math.min(textFm.ascent, textCenter - half);
                fm.descent = Math.max(textFm.descent, textCenter + half);
                fm.top = fm.ascent;
                fm.bottom = fm.descent;
            }
            return bounds.right;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x,
                         int top, int y, int bottom, Paint paint) {
            Drawable drawable = getDrawable();
            Paint.FontMetricsInt fm = paint.getFontMetricsInt();
            int transY = y + (fm.descent + fm.ascent) / 2 - drawable.getBounds().height() / 2;
            canvas.save();
            canvas.translate(x, transY);
            drawable.draw(canvas);
            canvas.restore();
        }
    }
}
