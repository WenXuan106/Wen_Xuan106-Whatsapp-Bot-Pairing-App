package com.wenxuan106.botpair;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Where things live on the phone, plus the little status protocol between the app and the bot service. */
final class BotFiles {
    private BotFiles() {}

    static final int PORT = 3000;
    static final String BASE_URL = "http://127.0.0.1:" + PORT;

    // States written by the service (it runs in its own process, so a file is the simplest way to share them).
    static final String PREPARING = "PREPARING";
    static final String STARTING = "STARTING";
    static final String RUNNING = "RUNNING";
    static final String STOPPED = "STOPPED";
    static final String ERROR = "ERROR";

    static File botDir(Context c) { return new File(c.getFilesDir(), "bot"); }
    static File logFile(Context c) { return new File(c.getFilesDir(), "bot.log"); }
    static File envFile(Context c) { return new File(botDir(c), ".env"); }
    private static File statusFile(Context c) { return new File(c.getFilesDir(), "status.txt"); }

    // ------------------------------------------------------------- status

    static void writeStatus(Context c, String state, String detail) {
        try {
            File tmp = new File(c.getFilesDir(), "status.tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            try {
                out.write((state + "\t" + detail.replace('\n', ' ') + "\t" + System.currentTimeMillis()).getBytes("UTF-8"));
            } finally {
                out.close();
            }
            tmp.renameTo(statusFile(c));
        } catch (IOException ignored) {
        }
    }

    /** { state, detail, ageInMillis }. A missing or old status means the service is not running. */
    static String[] readStatus(Context c) {
        try {
            String s = new String(readAll(new FileInputStream(statusFile(c))), "UTF-8");
            String[] parts = s.split("\t", 3);
            if (parts.length == 3) {
                long age = System.currentTimeMillis() - Long.parseLong(parts[2].trim());
                if (age > 15000 && !STOPPED.equals(parts[0]) && !ERROR.equals(parts[0])) {
                    return new String[] { STOPPED, "", String.valueOf(age) };
                }
                return new String[] { parts[0], parts[1], String.valueOf(age) };
            }
        } catch (Exception ignored) {
        }
        return new String[] { STOPPED, "", "0" };
    }

    // --------------------------------------------------------- unpacking

    /** Unpacks the bot's files from the app the first time (and after an app update). Never deletes anything, so saved data survives. */
    static void ensureExtracted(Context c) throws IOException {
        String stamp = readAsset(c, "bot.stamp").trim();
        File dir = botDir(c);
        File stampFile = new File(dir, ".stamp");
        if (stampFile.exists() && new File(dir, "main.js").exists() && stamp.equals(new String(readAll(new FileInputStream(stampFile)), "UTF-8").trim())) {
            return;
        }

        int total = 1;
        try {
            total = Math.max(1, Integer.parseInt(readAsset(c, "bot.count").trim()));
        } catch (NumberFormatException ignored) {
        }

        dir.mkdirs();
        String root = dir.getCanonicalPath() + File.separator;
        ZipInputStream zin = new ZipInputStream(new BufferedInputStream(c.getAssets().open("bot.zip"), 65536));
        try {
            byte[] buf = new byte[65536];
            int done = 0;
            int lastPercent = -1;
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                File out = new File(dir, entry.getName());
                if (!out.getCanonicalPath().startsWith(root)) continue; // never write outside the bot folder
                if (entry.isDirectory()) {
                    out.mkdirs();
                } else {
                    out.getParentFile().mkdirs();
                    OutputStream os = new FileOutputStream(out);
                    try {
                        int n;
                        while ((n = zin.read(buf)) > 0) os.write(buf, 0, n);
                    } finally {
                        os.close();
                    }
                    done++;
                    int percent = Math.min(99, done * 100 / total);
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        writeStatus(c, PREPARING, "Unpacking the bot… " + percent + "%");
                    }
                }
            }
        } finally {
            zin.close();
        }

        FileOutputStream sf = new FileOutputStream(stampFile);
        try {
            sf.write(stamp.getBytes("UTF-8"));
        } finally {
            sf.close();
        }

        // First run: create a settings file people can edit from the app.
        File env = envFile(c);
        if (!env.exists()) {
            FileOutputStream ef = new FileOutputStream(env);
            try {
                ef.write(DEFAULT_ENV.getBytes("UTF-8"));
            } finally {
                ef.close();
            }
        }
    }

    static final String DEFAULT_ENV =
        "# Bot settings. Lines starting with # are ignored - remove the # to use a setting.\n"
        + "# Restart the bot after saving.\n"
        + "\n"
        + "#PREFIX=!\n"
        + "#BOT_NAME=My WhatsApp bot\n"
        + "#OWNER_NAME=\n"
        + "#OWNER_NUMBER=\n"
        + "#TIMEZONE=Asia/Singapore\n"
        + "\n"
        + "# Optional extras\n"
        + "#TELEGRAM_BOT_TOKEN=\n"
        + "#TELEGRAM_OWNER_ID=\n"
        + "#GROQ_API_KEY=\n"
        + "#OPENAI_API_KEY=\n"
        + "#OPENWEATHER_API_KEY=\n"
        + "\n"
        + "# Only if the bot crashes right after starting: turns off the image commands (graph, weather, profile).\n"
        + "#BOT_NO_IMAGES=1\n";

    // ---------------------------------------------------------- helpers

    static String readAsset(Context c, String name) throws IOException {
        return new String(readAll(c.getAssets().open(name)), "UTF-8");
    }

    static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    /** GET a page from the bot's own web server. Returns the body, or null if it isn't reachable. */
    static String httpGet(String path, int timeoutMs) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            if (conn.getResponseCode() != 200) return null;
            return new String(readAll(conn.getInputStream()), "UTF-8");
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** The WhatsApp link state reported by the bot ("idle", "connected", ...), or null if the bot isn't answering. */
    static String whatsappState() {
        String body = httpGet("/api/status", 1500);
        if (body == null) return null;
        try {
            return new JSONObject(body).optString("status", "");
        } catch (Exception e) {
            return "";
        }
    }

    /** The last `maxBytes` of the log file. */
    static String tailLog(Context c, int maxBytes) {
        File f = logFile(c);
        if (!f.exists()) return "(no log yet - start the bot first)";
        try {
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "r");
            try {
                long len = raf.length();
                long start = Math.max(0, len - maxBytes);
                raf.seek(start);
                byte[] data = new byte[(int) (len - start)];
                raf.readFully(data);
                String s = new String(data, "UTF-8");
                return start > 0 ? "…" + s.substring(s.indexOf('\n') + 1) : s;
            } finally {
                raf.close();
            }
        } catch (IOException e) {
            return "(couldn't read the log: " + e.getMessage() + ")";
        }
    }
}
