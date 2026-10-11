package com.wenxuan106.botpair;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/** Edit the bot's settings file (.env): bot name, owner number, API keys, time zone, ... */
public class SettingsActivity extends Activity {

    private EditText editor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout col = Ui.column(this);
        setContentView(col);

        col.addView(Ui.text(this, "Settings", 24, android.graphics.Color.WHITE));
        col.addView(Ui.text(this, "One setting per line, like OWNER_NUMBER=6591234567. Remove the # in front of a line to use it. Keys you type here stay on this phone.", 13, Ui.MUTED), Ui.wide(this, 6));

        editor = new EditText(this);
        editor.setTextColor(android.graphics.Color.WHITE);
        editor.setBackgroundColor(Ui.PANEL);
        editor.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setHorizontallyScrolling(true);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setTypeface(android.graphics.Typeface.MONOSPACE);
        editor.setTextSize(13);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.topMargin = Ui.dp(this, 12);
        col.addView(editor, lp);

        Button save = Ui.button(this, "Save", true);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (save()) {
                    Toast.makeText(SettingsActivity.this, "Saved. Restart the bot to use the new settings.", Toast.LENGTH_LONG).show();
                }
            }
        });
        col.addView(save, Ui.wide(this, 12));

        Button saveRestart = Ui.button(this, "Save and restart bot", false);
        saveRestart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!save()) return;
                startService(new Intent(SettingsActivity.this, BotService.class).setAction(BotService.ACTION_STOP));
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Intent i = new Intent(SettingsActivity.this, BotService.class);
                        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
                    }
                }, 1800);
                Toast.makeText(SettingsActivity.this, "Restarting…", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        col.addView(saveRestart, Ui.wide(this, 8));

        load();
    }

    private void load() {
        File f = BotFiles.envFile(this);
        try {
            if (f.exists()) {
                editor.setText(new String(BotFiles.readAll(new FileInputStream(f)), "UTF-8"));
            } else {
                editor.setText(BotFiles.DEFAULT_ENV);
            }
        } catch (Exception e) {
            editor.setText(BotFiles.DEFAULT_ENV);
        }
    }

    private boolean save() {
        try {
            File dir = BotFiles.botDir(this);
            dir.mkdirs();
            FileOutputStream out = new FileOutputStream(BotFiles.envFile(this));
            try {
                out.write(editor.getText().toString().getBytes("UTF-8"));
            } finally {
                out.close();
            }
            return true;
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't save: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }
}
