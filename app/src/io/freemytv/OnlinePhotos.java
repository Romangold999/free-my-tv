package io.freemytv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Free, freely-licensed photographic backdrops from Wikimedia Commons.
 * Only landscape images whose original is at least 3840x2160 are accepted.
 * The exact source, creator and individual license are kept beside each cached image.
 */
final class OnlinePhotos {
    private OnlinePhotos() {}

    private static final int MIN_W = 3840, MIN_H = 2160, MAX_FILES = 24, FETCH_BATCH = 3;
    private static final long ROTATE_MS = 5 * 60_000L, REFRESH_MS = 30 * 60_000L;
    private static final String[] SEARCHES = {
            "landscape nature mountains ocean forest", "coastline sea sunrise sunset",
            "mountain lake alpine landscape", "forest waterfall river landscape",
            "city skyline architecture night", "desert dunes landscape", "aurora borealis landscape"
    };
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean fetching;
    private static volatile long lastFetch;
    private static File built, building;
    private static Backdrop cachedBackdrop;

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "onlinephotos");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    private static List<File> files(Context c) {
        File[] all = dir(c).listFiles((d, name) -> name.startsWith("commons_") && name.endsWith(".jpg"));
        List<File> out = new ArrayList<>();
        if (all != null) {
            Collections.addAll(out, all);
            Collections.sort(out, Comparator.comparingLong(File::lastModified).reversed());
        }
        return out;
    }

    static boolean has(Context c) {
        return !files(c).isEmpty();
    }

    static List<Recs.Pick> picks(Context c) {
        List<File> images = files(c);
        List<Recs.Pick> out = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            Recs.Pick p = new Recs.Pick();
            p.kind = "photo";
            p.id = i;
            p.photo = images.get(i);
            out.add(p);
        }
        ensure(c);
        return out;
    }

    static synchronized Backdrop backdrop(Context context, Runnable ready) {
        Context c = context.getApplicationContext();
        ensure(c);
        List<File> images = files(c);
        if (images.isEmpty()) return cachedBackdrop;
        long slot = System.currentTimeMillis() / ROTATE_MS;
        File want = images.get((int) (Math.floorMod(slot, images.size())));
        if (!want.equals(built) && !want.equals(building)) {
            building = want;
            WORKER.execute(() -> {
                Backdrop result = null;
                try {
                    Bitmap bitmap = BitmapFactory.decodeFile(want.getAbsolutePath());
                    if (bitmap != null) result = Backdrop.make(bitmap, true, true);
                } catch (RuntimeException | OutOfMemoryError e) {
                    AppLog.w("commons photos: backdrop decode: " + e.getMessage());
                }
                synchronized (OnlinePhotos.class) {
                    building = null;
                    if (result == null) return;
                    cachedBackdrop = result;
                    built = want;
                }
                if (ready != null) MAIN.post(ready);
            });
        }
        return cachedBackdrop;
    }

    private static synchronized void ensure(Context c) {
        List<File> existing = files(c);
        long now = System.currentTimeMillis();
        if (fetching || existing.size() >= 8 || now - lastFetch < REFRESH_MS) return;
        fetching = true;
        lastFetch = now;
        WORKER.execute(() -> {
            try {
                fetch(c, existing);
            } catch (Exception e) {
                AppLog.w("commons photos: fetch: " + e.getMessage());
            } finally {
                fetching = false;
            }
        });
    }

    private static void fetch(Context c, List<File> existing) throws Exception {
        String term = SEARCHES[(int) ((System.currentTimeMillis() / REFRESH_MS) % SEARCHES.length)];
        String query = "filemime:jpeg " + term;
        String api = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrnamespace=6&gsrlimit=30&gsrsearch="
                + URLEncoder.encode(query, "UTF-8")
                + "&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&iiurlwidth=3840&format=json";
        JSONObject root = new JSONObject(request(api, 30_000));
        JSONObject pages = root.optJSONObject("query") == null ? null : root.getJSONObject("query").optJSONObject("pages");
        if (pages == null) return;

        JSONArray credits = readCredits(c);
        int saved = 0;
        java.util.Iterator<String> keys = pages.keys();
        while (keys.hasNext() && saved < FETCH_BATCH) {
            JSONObject page = pages.getJSONObject(keys.next());
            JSONArray info = page.optJSONArray("imageinfo");
            if (info == null || info.length() == 0) continue;
            JSONObject image = info.getJSONObject(0);
            int width = image.optInt("width"), height = image.optInt("height");
            if (width < MIN_W || height < MIN_H || (double) width / Math.max(1, height) < 1.55) continue;
            String title = page.optString("title", "");
            String thumb = image.optString("thumburl", image.optString("url", ""));
            String id = page.optString("pageid", "");
            if (id.isEmpty() || !thumb.startsWith("https://")) continue;
            File target = new File(dir(c), "commons_" + id + ".jpg");
            if (target.isFile() && target.length() > 100_000) continue;
            File part = new File(dir(c), target.getName() + ".part");
            try {
                download(thumb, part);
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(part.getAbsolutePath(), bounds);
                if (bounds.outWidth < 1920 || bounds.outHeight < 1080) {
                    part.delete();
                    continue;
                }
                if (!part.renameTo(target)) {
                    part.delete();
                    continue;
                }
                JSONObject meta = image.optJSONObject("extmetadata");
                JSONObject credit = new JSONObject();
                credit.put("file", target.getName());
                credit.put("title", title);
                credit.put("author", clean(meta, "Artist"));
                credit.put("credit", clean(meta, "Credit"));
                credit.put("license", clean(meta, "LicenseShortName"));
                credit.put("licenseUrl", clean(meta, "LicenseUrl"));
                credit.put("source", image.optString("descriptionurl", ""));
                credit.put("originalWidth", width);
                credit.put("originalHeight", height);
                credits.put(credit);
                saved++;
            } catch (Exception e) {
                part.delete();
                AppLog.w("commons photos: skip " + title + ": " + e.getMessage());
            }
        }
        writeCredits(c, credits);
        trim(c);
    }

    private static String clean(JSONObject object, String key) {
        if (object == null) return "";
        JSONObject value = object.optJSONObject(key);
        return value == null ? "" : value.optString("value", "");
    }

    private static JSONArray readCredits(Context c) {
        File f = new File(dir(c), "credits.json");
        try (InputStream in = new java.io.FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return new JSONArray(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static void writeCredits(Context c, JSONArray credits) {
        File f = new File(dir(c), "credits.json"), part = new File(dir(c), "credits.json.part");
        try (FileOutputStream out = new FileOutputStream(part)) {
            out.write(credits.toString().getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
            part.renameTo(f);
        } catch (Exception e) {
            AppLog.w("commons photos: attribution cache: " + e.getMessage());
        }
    }

    private static void trim(Context c) {
        List<File> all = files(c);
        for (int i = MAX_FILES; i < all.size(); i++) all.get(i).delete();
    }

    private static String request(String address, int timeout) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        connection.setRequestProperty("User-Agent", "FreeMyTV/1.5 (https://github.com/Romangold999/free-my-tv)");
        try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private static void download(String address, File target) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(45_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "FreeMyTV/1.5 (https://github.com/Romangold999/free-my-tv)");
        try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            out.getFD().sync();
        } finally {
            connection.disconnect();
        }
    }
}
