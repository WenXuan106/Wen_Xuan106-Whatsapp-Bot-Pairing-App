package com.wenxuan106.botpair;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Shows the end of the bot's log so problems can be seen (and copied) without a computer. */
public class LogActivity extends Activity {

    private final Handler handler = new Handler();
    private TextView logView;
    private ScrollView scroll;
    private String shown = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout col = Ui.column(this);
        setContentView(col);

        col.addView(Ui.text(this, "Logs", 24, android.graphics.Color.WHITE));

        scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.PANEL);
        logView = Ui.text(this, "", 11, android.graphics.Color.parseColor("#D0D7DB"));
        Ui.mono(logView);
        logView.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        logView.setTextIsSelectable(true);
        scroll.addView(logView);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.topMargin = Ui.dp(this, 12);
        col.addView(scroll, lp);

        Button copy = Ui.button(this, "Copy log", true);
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("bot log", shown));
                    Toast.makeText(LogActivity.this, "Copied", Toast.LENGTH_SHORT).show();
                }
            }
        });
        col.addView(copy, Ui.wide(this, 12));
    }

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            String log = BotFiles.tailLog(LogActivity.this, 60000);
            if (!log.equals(shown)) {
                boolean atBottom = scroll.getChildAt(0).getBottom() - (scroll.getHeight() + scroll.getScrollY()) < 80;
                shown = log;
                logView.setText(log);
                if (atBottom) {
                    scroll.post(new Runnable() {
                        @Override
                        public void run() {
                            scroll.fullScroll(View.FOCUS_DOWN);
                        }
                    });
                }
            }
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        refresh.run();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }
}
