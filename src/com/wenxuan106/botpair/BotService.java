package com.wenxuan106.botpair;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;

import java.io.File;
import java.util.TimeZone;

/**
 * Keeps the bot running in the background. It lives in its own process (":bot") because Node.js can
 * only be started once per process - stopping the service ends that process, so a restart is clean.
 */
public class BotService extends Service {

    static final String ACTION_STOP = "com.wenxuan106.botpair.STOP";
    private static final String CHANNEL_ID = "bot";
    private static final int NOTIFICATION_ID = 1;

    private PowerManager.WakeLock wakeLock;
    private boolean started;
    private volatile boolean nodeExited;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startInForeground();
        if (!started) {
            started = true;
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "botpair:bot");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
            }
            startBot();
            startMonitor();
        }
        return START_STICKY;
    }

    private void startInForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Bot running", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(channel);
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        int piFlags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), piFlags);
        Intent stop = new Intent(this, BotService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop, piFlags);

        builder.setContentTitle("WhatsApp bot")
            .setContentText("Running in the background")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPi);

        startForeground(NOTIFICATION_ID, builder.build());
    }

    /** Unpacks the files if needed, then runs Node.js (this call only returns when the bot stops). */
    private void startBot() {
        Thread t = new Thread(null, new Runnable() {
            @Override
            public void run() {
                try {
                    BotFiles.writeStatus(BotService.this, BotFiles.PREPARING, "Unpacking the bot…");
                    BotFiles.ensureExtracted(BotService.this);

                    File log = BotFiles.logFile(BotService.this);
                    if (log.exists() && log.length() > 3 * 1024 * 1024) log.renameTo(new File(log.getPath() + ".1"));

                    BotFiles.writeStatus(BotService.this, BotFiles.STARTING, "Starting…");
                    NodeRunner.loadLibraries();

                    File botDir = BotFiles.botDir(BotService.this);
                    String[] args = { "node", "--max-old-space-size=384", new File(botDir, "main.js").getPath() };
                    String[] env = {
                        "HOME=" + getFilesDir().getPath(),
                        "TMPDIR=" + getCacheDir().getPath(),
                        "PORT=" + BotFiles.PORT,
                        "NODE_ENV=production",
                        "TZ=" + TimeZone.getDefault().getID(),
                    };
                    int code = NodeRunner.start(args, env, log.getPath());

                    nodeExited = true;
                    BotFiles.writeStatus(BotService.this, BotFiles.STOPPED, "The bot stopped (code " + code + ")");
                } catch (Throwable e) {
                    nodeExited = true;
                    BotFiles.writeStatus(BotService.this, BotFiles.ERROR, String.valueOf(e));
                }
                stopSelf();
            }
        }, "bot-node", 8L * 1024 * 1024);
        t.start();
    }

    /** Every few seconds: is the bot's web server answering? Also acts as the heartbeat the app looks at. */
    private void startMonitor() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!nodeExited) {
                    String[] status = BotFiles.readStatus(BotService.this);
                    if (!BotFiles.PREPARING.equals(status[0])) {
                        String wa = BotFiles.whatsappState();
                        if (wa != null) {
                            BotFiles.writeStatus(BotService.this, BotFiles.RUNNING, wa);
                        } else {
                            BotFiles.writeStatus(BotService.this, BotFiles.STARTING, "Starting…");
                        }
                    } else {
                        BotFiles.writeStatus(BotService.this, BotFiles.PREPARING, status[1]);
                    }
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }, "bot-monitor");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        String[] status = BotFiles.readStatus(this);
        if (!BotFiles.ERROR.equals(status[0])) {
            BotFiles.writeStatus(this, BotFiles.STOPPED, "Stopped");
        }
        super.onDestroy();
        // Node can't be started twice in one process, so end this one; a restart gets a fresh process.
        Process.killProcess(Process.myPid());
    }
}
