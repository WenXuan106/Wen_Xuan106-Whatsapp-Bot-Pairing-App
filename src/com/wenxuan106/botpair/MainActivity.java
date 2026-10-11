package com.wenxuan106.botpair;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** The home screen: shows whether the bot is running and has the buttons for everything else. */
public class MainActivity extends Activity {

    private final Handler handler = new Handler();
    private TextView statusLine;
    private TextView hintLine;
    private Button toggleButton;
    private Button restartButton;
    private Button pairButton;

    private volatile boolean polling;
    private volatile String whatsapp; // latest WhatsApp state from the bot, null if the bot isn't answering
    private boolean running; // service is up (preparing / starting / running)
    private boolean webReady; // the bot's web server answers

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.DARK);
        LinearLayout col = Ui.column(this);
        scroll.addView(col);
        setContentView(scroll);

        col.addView(Ui.text(this, "WhatsApp bot", 26, android.graphics.Color.WHITE));
        statusLine = Ui.text(this, "…", 18, Ui.GREEN);
        col.addView(statusLine, Ui.wide(this, 16));
        hintLine = Ui.text(this, "", 14, Ui.MUTED);
        col.addView(hintLine, Ui.wide(this, 6));

        toggleButton = Ui.button(this, "Start bot", true);
        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (running) stopBot(); else startBot();
            }
        });
        col.addView(toggleButton, Ui.wide(this, 20));

        pairButton = Ui.button(this, "Pair WhatsApp", false);
        pairButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!webReady) {
                    Toast.makeText(MainActivity.this, "Wait until the bot says it is running", Toast.LENGTH_SHORT).show();
                } else {
                    startActivity(new Intent(MainActivity.this, PairActivity.class));
                }
            }
        });
        col.addView(pairButton, Ui.wide(this, 10));

        restartButton = Ui.button(this, "Restart bot", false);
        restartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restartBot();
            }
        });
        col.addView(restartButton, Ui.wide(this, 10));

        Button settings = Ui.button(this, "Settings", false);
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        col.addView(settings, Ui.wide(this, 10));

        Button logs = Ui.button(this, "View logs", false);
        logs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, LogActivity.class));
            }
        });
        col.addView(logs, Ui.wide(this, 10));

        Button battery = Ui.button(this, "Keep running in the background", false);
        battery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askBatteryExemption();
            }
        });
        col.addView(battery, Ui.wide(this, 10));

        TextView note = Ui.text(this,
            "The bot runs on this phone. Pairing links your own WhatsApp number to it, and everything stays on this device. "
            + "Keep the phone online and allow background running, otherwise Android may put the bot to sleep.", 12, Ui.MUTED);
        col.addView(note, Ui.wide(this, 24));

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[] { "android.permission.POST_NOTIFICATIONS" }, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        polling = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (polling) {
                    whatsapp = BotFiles.whatsappState();
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }, "ui-poll").start();
        refresh.run();
    }

    @Override
    protected void onPause() {
        polling = false;
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            String[] st = BotFiles.readStatus(MainActivity.this);
            String state = st[0];
            running = BotFiles.PREPARING.equals(state) || BotFiles.STARTING.equals(state) || BotFiles.RUNNING.equals(state);
            webReady = running && whatsapp != null;

            String line;
            String hint = "";
            if (BotFiles.PREPARING.equals(state)) {
                line = "Getting ready…";
                hint = st[1];
            } else if (BotFiles.STARTING.equals(state) || (running && !webReady)) {
                line = "Starting the bot…";
                hint = "This takes a few seconds.";
            } else if (running) {
                line = "● Bot is running";
                String w = whatsapp == null ? "" : whatsapp;
                if (w.equals("connected") || w.equals("open")) {
                    hint = "WhatsApp is linked and the bot is answering commands.";
                } else {
                    hint = "WhatsApp is not linked yet. Tap “Pair WhatsApp” (status: " + (w.isEmpty() ? "waiting" : w) + ").";
                }
            } else if (BotFiles.ERROR.equals(state)) {
                line = "The bot could not start";
                hint = st[1] + "\nOpen “View logs” for details.";
            } else {
                line = "Bot is stopped";
                hint = st[1].length() > 0 && !"Stopped".equals(st[1]) ? st[1] : "Tap “Start bot” to run it.";
            }
            statusLine.setText(line);
            hintLine.setText(hint);
            toggleButton.setText(running ? "Stop bot" : "Start bot");
            Ui.show(restartButton, running);
            Ui.show(pairButton, running);
            handler.postDelayed(this, 1000);
        }
    };

    // ---------------------------------------------------------- actions

    private void startBot() {
        Intent i = new Intent(this, BotService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }

    private void stopBot() {
        startService(new Intent(this, BotService.class).setAction(BotService.ACTION_STOP));
    }

    private void restartBot() {
        stopBot();
        Toast.makeText(this, "Restarting…", Toast.LENGTH_SHORT).show();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                startBot();
            }
        }, 1800);
    }

    private void askBatteryExemption() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        try {
            if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Toast.makeText(this, "Already allowed to run in the background", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }
}
