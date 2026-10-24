package com.cleanhome.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Clean Home: a minimal, D-pad friendly TV launcher.
 * - Big clock + greeting
 * - Favorites row (long-press OK on any app to pin / unpin / info / uninstall)
 * - All apps grid
 * - Quick buttons: Google search, Play Store, Settings
 * Entire UI is built in code so there are no layout XML files to go wrong.
 */
public class MainActivity extends Activity {

    private static final int ACCENT = 0xFF7CC4FF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xB3FFFFFF;
    private static final int MAX_FAVORITES = 7;

    // Apps pinned on first run, if installed (in this order).
    private static final String[] DEFAULT_FAVORITES = {
            "com.google.android.youtube.tv",
            "com.google.android.youtube.tvunplugged",
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "com.google.android.videos",
            "com.spotify.tv.android",
            "com.android.vending",
            "com.showmax.tv",
            "com.google.android.apps.youtube.music",
    };

    private static class AppEntry {
        String pkg;
        String label;
        Drawable icon;
        Intent launch;
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private PackageManager pm;
    private float density;

    private TextView clockView, dateView, greetingView;
    private LinearLayout favRow;
    private GridLayout allGrid;
    private ScrollView scroll;
    private TextView favHint;

    private List<AppEntry> apps = new ArrayList<AppEntry>();
    private String lastSignature = "";

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            ui.postDelayed(this, 15000);
        }
    };

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("cleanhome", MODE_PRIVATE);
        pm = getPackageManager();
        density = getResources().getDisplayMetrics().density;
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.post(clockTick);
        refreshApps(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(clockTick);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while we are already showing: jump back to top.
        if (scroll != null) scroll.fullScroll(View.FOCUS_UP);
        if (favRow != null && favRow.getChildCount() > 0) favRow.getChildAt(0).requestFocus();
    }

    @Override
    public void onBackPressed() {
        // A launcher must never exit. Back just returns to the top.
        if (scroll != null) scroll.fullScroll(View.FOCUS_UP);
    }

    // ---------------------------------------------------------------- UI

    private int dp(float v) {
        return Math.round(v * density);
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0B1020, 0xFF111A33, 0xFF1B2B55});
        root.setBackground(bg);

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(48), dp(28), dp(48), dp(32));
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- top bar: clock (left) + quick buttons (right)
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        top.addView(clockBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        clockView = text("", 44, TEXT, Typeface.create("sans-serif-light", Typeface.NORMAL));
        clockBox.addView(clockView);
        dateView = text("", 15, TEXT_DIM, Typeface.DEFAULT);
        clockBox.addView(dateView);
        greetingView = text("", 15, ACCENT, Typeface.DEFAULT);
        greetingView.setPadding(0, dp(2), 0, 0);
        clockBox.addView(greetingView);

        top.addView(pill("Search", new Runnable() {
            public void run() { openSearch(); }
        }));
        top.addView(pill("Play Store", new Runnable() {
            public void run() { openPackage("com.android.vending", "Play Store"); }
        }));
        top.addView(pill("Settings", new Runnable() {
            public void run() { openSettings(); }
        }));

        // ---- favorites
        page.addView(sectionLabel("FAVORITES"));
        favRow = new LinearLayout(this);
        favRow.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(favRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        favHint = text("Hold OK on any app below to pin it here.", 14, TEXT_DIM, Typeface.DEFAULT);
        favHint.setPadding(dp(4), dp(8), 0, dp(8));
        page.addView(favHint);

        // ---- all apps
        page.addView(sectionLabel("ALL APPS"));
        allGrid = new GridLayout(this);
        int cols = Math.max(4, (getResources().getDisplayMetrics().widthPixels - dp(96)) / dp(124));
        allGrid.setColumnCount(cols);
        page.addView(allGrid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        updateClock();
        return root;
    }

    private TextView text(String s, float sp, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        return t;
    }

    private TextView sectionLabel(String s) {
        TextView t = text(s, 12, TEXT_DIM, Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.15f);
        t.setPadding(dp(4), dp(22), 0, dp(8));
        return t;
    }

    /** Rounded highlight that shows when an item has D-pad focus. */
    private Drawable focusBg(float radiusDp) {
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(0x33FFFFFF);
        focused.setCornerRadius(dp(radiusDp));
        focused.setStroke(dp(2), ACCENT);
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(0x00000000);
        normal.setCornerRadius(dp(radiusDp));
        normal.setStroke(dp(2), 0x00000000);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, focused);
        sl.addState(new int[]{android.R.attr.state_pressed}, focused);
        sl.addState(new int[]{}, normal);
        return sl;
    }

    private final View.OnFocusChangeListener grow = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            float s = hasFocus ? 1.08f : 1f;
            v.animate().scaleX(s).scaleY(s).setDuration(120).start();
            if (hasFocus) v.bringToFront();
        }
    };

    private View pill(String label, final Runnable action) {
        TextView t = text(label, 15, TEXT, Typeface.DEFAULT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(18), dp(10), dp(18), dp(10));
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(focusBg(22));
        t.setOnFocusChangeListener(grow);
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { action.run(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private View tile(final AppEntry a, int widthDp, int iconDp) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(dp(6), dp(12), dp(6), dp(10));
        t.setFocusable(true);
        t.setClickable(true);
        t.setLongClickable(true);
        t.setBackground(focusBg(14));
        t.setOnFocusChangeListener(grow);

        ImageView iv = new ImageView(this);
        iv.setImageDrawable(a.icon);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        t.addView(iv, new LinearLayout.LayoutParams(dp(iconDp), dp(iconDp)));

        TextView tv = text(a.label, 13, TEXT, Typeface.DEFAULT);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(8), 0, 0);
        t.addView(tv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { launch(a); }
        });
        t.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                showMenu(a);
                return true;
            }
        });
        t.setLayoutParams(new ViewGroup.LayoutParams(dp(widthDp), ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    private void updateClock() {
        Date now = new Date();
        boolean is24 = DateFormat.is24HourFormat(this);
        SimpleDateFormat tf = new SimpleDateFormat(is24 ? "HH:mm" : "h:mm a", Locale.getDefault());
        SimpleDateFormat df = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault());
        clockView.setText(tf.format(now));
        dateView.setText(df.format(now));
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String g = h < 5 ? "Good night" : h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
        greetingView.setText(g);
    }

    // ---------------------------------------------------------------- apps

    private void refreshApps(final boolean force) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppEntry> found = queryApps();
                final String sig = signature(found);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!force && sig.equals(lastSignature)) return;
                        lastSignature = sig;
                        apps = found;
                        render();
                    }
                });
            }
        }).start();
    }

    private String signature(List<AppEntry> list) {
        StringBuilder sb = new StringBuilder(prefs.getString("favs", "-"));
        sb.append('|');
        for (AppEntry a : list) sb.append(a.pkg).append(',');
        return sb.toString();
    }

    private List<AppEntry> queryApps() {
        List<AppEntry> out = new ArrayList<AppEntry>();
        Set<String> seen = new LinkedHashSet<String>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            List<ResolveInfo> list = pm.queryIntentActivities(q, 0);
            for (ResolveInfo ri : list) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || seen.contains(pkg)) continue;
                seen.add(pkg);
                AppEntry a = new AppEntry();
                a.pkg = pkg;
                a.label = String.valueOf(ri.loadLabel(pm));
                a.icon = ri.loadIcon(pm); // square icon keeps tiles clean
                Intent li = new Intent(Intent.ACTION_MAIN);
                li.addCategory(cat);
                li.setClassName(pkg, ri.activityInfo.name);
                li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                a.launch = li;
                out.add(a);
            }
        }
        Collections.sort(out, new Comparator<AppEntry>() {
            public int compare(AppEntry x, AppEntry y) {
                return x.label.compareToIgnoreCase(y.label);
            }
        });
        return out;
    }

    private List<String> favorites() {
        List<String> favs = new ArrayList<String>();
        if (!prefs.contains("favs")) {
            // First run: pin well-known apps that are actually installed.
            for (String p : DEFAULT_FAVORITES) {
                for (AppEntry a : apps) {
                    if (a.pkg.equals(p) && favs.size() < MAX_FAVORITES) { favs.add(p); break; }
                }
            }
            saveFavorites(favs);
            return favs;
        }
        String s = prefs.getString("favs", "");
        if (s.length() > 0) {
            for (String p : s.split(",")) favs.add(p);
        }
        return favs;
    }

    private void saveFavorites(List<String> favs) {
        prefs.edit().putString("favs", TextUtils.join(",", favs)).apply();
    }

    private AppEntry find(String pkg) {
        for (AppEntry a : apps) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    private void render() {
        List<String> favs = favorites();
        lastSignature = signature(apps);

        favRow.removeAllViews();
        int shown = 0;
        for (String p : favs) {
            AppEntry a = find(p);
            if (a == null) continue;
            View t = tile(a, 124, 76);
            favRow.addView(t);
            shown++;
        }
        favHint.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);

        allGrid.removeAllViews();
        for (AppEntry a : apps) {
            View t = tile(a, 116, 56);
            allGrid.addView(t);
        }
        if (favRow.getChildCount() > 0) favRow.getChildAt(0).requestFocus();
        else if (allGrid.getChildCount() > 0) allGrid.getChildAt(0).requestFocus();
    }

    // ---------------------------------------------------------------- actions

    private void launch(AppEntry a) {
        try {
            startActivity(a.launch);
        } catch (Exception e) {
            toast("Couldn't open " + a.label);
        }
    }

    private void showMenu(final AppEntry a) {
        final List<String> favs = favorites();
        final boolean pinned = favs.contains(a.pkg);
        String[] items = {
                pinned ? "Unpin from favorites" : "Pin to favorites",
                "App info",
                "Uninstall",
        };
        new AlertDialog.Builder(this)
                .setTitle(a.label)
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        if (which == 0) {
                            if (pinned) {
                                favs.remove(a.pkg);
                            } else if (favs.size() >= MAX_FAVORITES) {
                                toast("Favorites are full (" + MAX_FAVORITES + "). Unpin one first.");
                                return;
                            } else {
                                favs.add(a.pkg);
                            }
                            saveFavorites(favs);
                            refreshApps(true);
                        } else if (which == 1) {
                            try {
                                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + a.pkg)));
                            } catch (Exception e) { toast("App info isn't available on this TV."); }
                        } else {
                            try {
                                startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg)));
                            } catch (Exception e) { toast("Can't uninstall this app."); }
                        }
                    }
                })
                .show();
    }

    private void openPackage(String pkg, String name) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) { toast(name + " isn't installed."); return; }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception e) { toast("Couldn't open " + name); }
    }

    private void openSearch() {
        // 1) Google TV / Android TV search app, 2) system assistant, 3) browser fallback.
        String[] searchApps = {"com.google.android.katniss", "com.google.android.googlequicksearchbox"};
        for (String p : searchApps) {
            Intent i = pm.getLeanbackLaunchIntentForPackage(p);
            if (i == null) i = pm.getLaunchIntentForPackage(p);
            if (i != null) {
                try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return; }
                catch (Exception ignored) { }
            }
        }
        try {
            startActivity(new Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        } catch (Exception ignored) { }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            toast("No search app or browser found.");
        }
    }

    private void openSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            toast("Couldn't open Settings.");
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // MENU key on the remote opens the options for the focused app.
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            View f = getCurrentFocus();
            if (f != null && f.isLongClickable()) { f.performLongClick(); return true; }
        }
        return super.onKeyDown(keyCode, event);
    }
}
