package com.wenxuan106.botpair;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small helpers so every screen looks the same without layout files. */
final class Ui {
    private Ui() {}

    static final int DARK = Color.parseColor("#0B141A");
    static final int PANEL = Color.parseColor("#1F2C34");
    static final int GREEN = Color.parseColor("#25D366");
    static final int MUTED = Color.parseColor("#9AA5AB");

    static int dp(Context c, int value) {
        return (int) (value * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    static TextView text(Context c, String value, int sp, int color) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    static Button button(Context c, String label, boolean primary) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(primary ? Color.BLACK : Color.WHITE);
        b.setBackgroundColor(primary ? GREEN : PANEL);
        return b;
    }

    static LinearLayout.LayoutParams wide(Context c, int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, topMarginDp);
        return lp;
    }

    static LinearLayout column(Context c) {
        LinearLayout column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(c, 20), dp(c, 20), dp(c, 20), dp(c, 20));
        column.setBackgroundColor(DARK);
        return column;
    }

    static void mono(TextView t) {
        t.setTypeface(Typeface.MONOSPACE);
        t.setGravity(Gravity.TOP | Gravity.START);
    }

    static void show(View v, boolean visible) {
        v.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
