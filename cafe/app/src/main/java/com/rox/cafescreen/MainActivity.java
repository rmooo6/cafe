package com.rox.cafescreen;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;
import android.media.MediaPlayer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int PORT = 8080;
    private static final String ADMIN_PIN = "1234"; // غيّرها قبل التسليم
    private static final int DEFAULT_IMAGE_DURATION_SECONDS = 8;
    private static final int MAX_UPLOAD_MB = 250;

    private FrameLayout root;
    private ImageView imageView;
    private VideoView videoView;
    private TextView overlayText;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<MediaItem> mediaItems = new ArrayList<>();
    private int currentIndex = 0;
    private File mediaDir;
    private File dbFile;
    private LocalHttpServer server;

    private final Runnable nextRunnable = new Runnable() {
        @Override
        public void run() {
            showNext();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();

        mediaDir = new File(getExternalFilesDir(null), "media");
        if (!mediaDir.exists()) mediaDir.mkdirs();
        dbFile = new File(getExternalFilesDir(null), "media.json");

        buildUi();
        loadDatabase();
        startServer();
        refreshOverlay();
        startPlayback();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (videoView != null) videoView.stopPlayback();
        if (server != null) server.stopServer();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        imageView = new ImageView(this);
        imageView.setBackgroundColor(Color.BLACK);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(imageView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        videoView = new VideoView(this);
        videoView.setBackgroundColor(Color.BLACK);
        root.addView(videoView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        overlayText = new TextView(this);
        overlayText.setTextColor(Color.WHITE);
        overlayText.setTextSize(22);
        overlayText.setGravity(Gravity.CENTER);
        overlayText.setBackgroundColor(0xAA000000);
        overlayText.setPadding(28, 20, 28, 20);
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        textParams.gravity = Gravity.TOP | Gravity.LEFT;
        textParams.setMargins(24, 24, 24, 24);
        root.addView(overlayText, textParams);

        setContentView(root);
    }

    private void refreshOverlay() {
        String ip = getLocalIpAddress();
        String url = "http://" + ip + ":" + PORT;
        String message = "Cafe Screen Local\n" +
                "افتح من الهاتف على نفس الواي فاي:\n" + url + "\n" +
                "PIN: " + ADMIN_PIN;
        overlayText.setText(message);
        overlayText.setVisibility(View.VISIBLE);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!mediaItems.isEmpty()) overlayText.setVisibility(View.GONE);
            }
        }, 20000);
    }

    private void startServer() {
        server = new LocalHttpServer(PORT);
        server.start();
    }

    private synchronized void loadDatabase() {
        mediaItems.clear();
        try {
            if (!dbFile.exists()) return;
            String json = readText(dbFile);
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                MediaItem item = new MediaItem();
                item.id = o.optString("id", UUID.randomUUID().toString());
                item.type = o.optString("type", "image");
                item.file = o.optString("file", "");
                item.title = o.optString("title", item.file);
                item.duration = o.optInt("duration", DEFAULT_IMAGE_DURATION_SECONDS);
                item.active = o.optBoolean("active", true);
                item.order = o.optLong("order", System.currentTimeMillis());
                File f = new File(mediaDir, item.file);
                if (item.active && f.exists()) mediaItems.add(item);
            }
            Collections.sort(mediaItems, new Comparator<MediaItem>() {
                @Override
                public int compare(MediaItem a, MediaItem b) {
                    return Long.compare(a.order, b.order);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private synchronized JSONArray readRawDatabaseArray() {
        try {
            if (!dbFile.exists()) return new JSONArray();
            return new JSONArray(readText(dbFile));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private synchronized void saveDatabase(JSONArray arr) throws Exception {
        writeText(dbFile, arr.toString(2));
        loadDatabase();
    }

    private void startPlayback() {
        handler.removeCallbacks(nextRunnable);
        hideSystemUi();

        if (mediaItems.isEmpty()) {
            videoView.stopPlayback();
            videoView.setVisibility(View.GONE);
            imageView.setVisibility(View.GONE);
            overlayText.setVisibility(View.VISIBLE);
            refreshOverlay();
            return;
        }

        if (currentIndex >= mediaItems.size()) currentIndex = 0;
        displayItem(mediaItems.get(currentIndex));
    }

    private void showNext() {
        if (mediaItems.isEmpty()) {
            startPlayback();
            return;
        }
        currentIndex = (currentIndex + 1) % mediaItems.size();
        displayItem(mediaItems.get(currentIndex));
    }

    private void displayItem(final MediaItem item) {
        handler.removeCallbacks(nextRunnable);
        hideSystemUi();
        overlayText.setVisibility(View.GONE);
        final File file = new File(mediaDir, item.file);
        if (!file.exists()) {
            showNext();
            return;
        }

        if ("video".equalsIgnoreCase(item.type)) {
            imageView.setVisibility(View.GONE);
            videoView.setVisibility(View.VISIBLE);
            videoView.stopPlayback();
            videoView.setVideoURI(Uri.fromFile(file));
            videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.setLooping(false);
                    videoView.start();
                }
            });
            videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    showNext();
                }
            });
            videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    handler.postDelayed(nextRunnable, 3000);
                    return true;
                }
            });
        } else {
            videoView.stopPlayback();
            videoView.setVisibility(View.GONE);
            imageView.setVisibility(View.VISIBLE);
            imageView.setImageURI(Uri.fromFile(file));
            int sec = Math.max(2, item.duration);
            handler.postDelayed(nextRunnable, sec * 1000L);
        }
    }

    private void onMediaChanged() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                loadDatabase();
                currentIndex = 0;
                startPlayback();
            }
        });
    }

    private String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<java.net.InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    java.net.InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        String ip = inetAddress.getHostAddress();
                        if (!ip.startsWith("127.")) return ip;
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    private static String readText(File file) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FileInputStream in = new FileInputStream(file);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

    private static void writeText(File file, String text) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.close();
    }

    private static String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) return "media";
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (name.length() > 80) name = name.substring(name.length() - 80);
        return name;
    }

    private static String extensionOf(String filename) {
        if (filename == null) return "";
        int i = filename.lastIndexOf('.');
        if (i < 0) return "";
        return filename.substring(i + 1).toLowerCase(Locale.US);
    }

    private static String typeFrom(String filename, String contentType) {
        String ext = extensionOf(filename);
        if (contentType != null && contentType.toLowerCase(Locale.US).startsWith("video/")) return "video";
        if (ext.equals("mp4") || ext.equals("m4v") || ext.equals("3gp") || ext.equals("webm")) return "video";
        return "image";
    }

    private static String contentTypeFor(String filename) {
        String ext = extensionOf(filename);
        if (ext.equals("jpg") || ext.equals("jpeg")) return "image/jpeg";
        if (ext.equals("png")) return "image/png";
        if (ext.equals("webp")) return "image/webp";
        if (ext.equals("gif")) return "image/gif";
        if (ext.equals("mp4") || ext.equals("m4v")) return "video/mp4";
        if (ext.equals("webm")) return "video/webm";
        return "application/octet-stream";
    }

    private static int indexOf(byte[] data, byte[] pattern, int start) {
        outer:
        for (int i = Math.max(0, start); i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static Map<String, String> parseQuery(String path) {
        Map<String, String> map = new HashMap<>();
        int q = path.indexOf('?');
        if (q < 0 || q == path.length() - 1) return map;
        String query = path.substring(q + 1);
        String[] pairs = query.split("&");
        for (String p : pairs) {
            int eq = p.indexOf('=');
            if (eq >= 0) map.put(urlDecode(p.substring(0, eq)), urlDecode(p.substring(eq + 1)));
            else map.put(urlDecode(p), "");
        }
        return map;
    }

    private static String pathOnly(String path) {
        int q = path.indexOf('?');
        return q >= 0 ? path.substring(0, q) : path;
    }

    private static String htmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static class MediaItem {
        String id;
        String type;
        String file;
        String title;
        int duration;
        boolean active;
        long order;
    }

    private class LocalHttpServer {
        private final int port;
        private volatile boolean running = false;
        private ServerSocket serverSocket;
        private ExecutorService executor = Executors.newCachedThreadPool();

        LocalHttpServer(int port) {
            this.port = port;
        }

        void start() {
            running = true;
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        serverSocket = new ServerSocket(port);
                        while (running) {
                            final Socket socket = serverSocket.accept();
                            executor.execute(new Runnable() {
                                @Override
                                public void run() {
                                    handleClient(socket);
                                }
                            });
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        void stopServer() {
            running = false;
            try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
            executor.shutdownNow();
        }

        private void handleClient(Socket socket) {
            try {
                socket.setSoTimeout(30000);
                BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
                OutputStream out = new BufferedOutputStream(socket.getOutputStream());
                HttpRequest req = readRequest(in);
                if (req == null) {
                    socket.close();
                    return;
                }
                route(req, out);
                out.flush();
                socket.close();
            } catch (Exception e) {
                try { socket.close(); } catch (Exception ignored) {}
            }
        }

        private HttpRequest readRequest(BufferedInputStream in) throws IOException {
            ByteArrayOutputStream headersOut = new ByteArrayOutputStream();
            int state = 0;
            int b;
            while ((b = in.read()) != -1) {
                headersOut.write(b);
                if (state == 0 && b == '\r') state = 1;
                else if (state == 1 && b == '\n') state = 2;
                else if (state == 2 && b == '\r') state = 3;
                else if (state == 3 && b == '\n') break;
                else state = 0;
                if (headersOut.size() > 64 * 1024) throw new IOException("Headers too large");
            }
            String headersText = headersOut.toString("UTF-8");
            String[] lines = headersText.split("\r?\n");
            if (lines.length == 0 || lines[0].trim().isEmpty()) return null;
            String[] first = lines[0].split(" ");
            if (first.length < 2) return null;
            HttpRequest req = new HttpRequest();
            req.method = first[0].trim();
            req.path = first[1].trim();
            for (int i = 1; i < lines.length; i++) {
                int idx = lines[i].indexOf(':');
                if (idx > 0) {
                    req.headers.put(lines[i].substring(0, idx).trim().toLowerCase(Locale.US), lines[i].substring(idx + 1).trim());
                }
            }
            int len = 0;
            if (req.headers.containsKey("content-length")) {
                try { len = Integer.parseInt(req.headers.get("content-length")); } catch (Exception ignored) {}
            }
            if (len > MAX_UPLOAD_MB * 1024 * 1024) {
                req.tooLarge = true;
                return req;
            }
            if (len > 0) {
                req.body = new byte[len];
                int read = 0;
                while (read < len) {
                    int n = in.read(req.body, read, len - read);
                    if (n < 0) break;
                    read += n;
                }
            } else {
                req.body = new byte[0];
            }
            return req;
        }

        private void route(HttpRequest req, OutputStream out) throws Exception {
            String p = pathOnly(req.path);
            if (req.tooLarge) {
                sendText(out, 413, "text/plain; charset=utf-8", "الملف كبير جداً. الحد الحالي " + MAX_UPLOAD_MB + "MB");
                return;
            }
            if ("GET".equals(req.method) && (p.equals("/") || p.equals("/admin"))) {
                sendText(out, 200, "text/html; charset=utf-8", adminHtml());
            } else if ("GET".equals(req.method) && p.equals("/api/media")) {
                sendText(out, 200, "application/json; charset=utf-8", readRawDatabaseArray().toString());
            } else if ("GET".equals(req.method) && p.startsWith("/media/")) {
                String fileName = urlDecode(p.substring("/media/".length()));
                serveMedia(out, fileName);
            } else if ("POST".equals(req.method) && p.equals("/upload")) {
                handleUpload(req, out);
            } else if ("POST".equals(req.method) && p.equals("/delete")) {
                handleDelete(req, out);
            } else if ("POST".equals(req.method) && p.equals("/clear")) {
                handleClear(req, out);
            } else {
                sendText(out, 404, "text/plain; charset=utf-8", "Not found");
            }
        }

        private void serveMedia(OutputStream out, String fileName) throws IOException {
            fileName = sanitizeFileName(fileName);
            File f = new File(mediaDir, fileName);
            if (!f.exists()) {
                sendText(out, 404, "text/plain; charset=utf-8", "Not found");
                return;
            }
            String headers = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: " + contentTypeFor(fileName) + "\r\n" +
                    "Content-Length: " + f.length() + "\r\n" +
                    "Connection: close\r\n\r\n";
            out.write(headers.getBytes(StandardCharsets.UTF_8));
            FileInputStream fis = new FileInputStream(f);
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) > 0) out.write(buf, 0, n);
            fis.close();
        }

        private void handleUpload(HttpRequest req, OutputStream out) throws Exception {
            String contentType = req.headers.get("content-type");
            if (contentType == null || !contentType.contains("multipart/form-data")) {
                sendText(out, 400, "text/plain; charset=utf-8", "Upload must be multipart/form-data");
                return;
            }
            String boundary = null;
            for (String part : contentType.split(";")) {
                part = part.trim();
                if (part.startsWith("boundary=")) boundary = part.substring("boundary=".length());
            }
            if (boundary == null) {
                sendText(out, 400, "text/plain; charset=utf-8", "No boundary");
                return;
            }

            MultipartResult result = parseMultipart(req.body, boundary);
            if (!ADMIN_PIN.equals(result.pin)) {
                sendText(out, 403, "text/html; charset=utf-8", simplePage("PIN خطأ", "ارجع واكتب PIN الصحيح."));
                return;
            }
            if (result.files.isEmpty()) {
                sendText(out, 400, "text/html; charset=utf-8", simplePage("لا يوجد ملف", "اختر صورة أو فيديو ثم ارفع."));
                return;
            }

            JSONArray arr = readRawDatabaseArray();
            for (UploadedFile uf : result.files) {
                String clean = sanitizeFileName(uf.filename);
                String ext = extensionOf(clean);
                if (ext.isEmpty()) ext = "bin";
                String id = String.valueOf(System.currentTimeMillis()) + "_" + UUID.randomUUID().toString().substring(0, 8);
                String storedName = id + "." + ext;
                File outFile = new File(mediaDir, storedName);
                FileOutputStream fos = new FileOutputStream(outFile);
                fos.write(uf.data);
                fos.close();

                JSONObject obj = new JSONObject();
                obj.put("id", id);
                obj.put("type", typeFrom(clean, uf.contentType));
                obj.put("file", storedName);
                obj.put("title", clean);
                obj.put("duration", result.duration <= 0 ? DEFAULT_IMAGE_DURATION_SECONDS : result.duration);
                obj.put("active", true);
                obj.put("order", System.currentTimeMillis());
                arr.put(obj);
            }
            saveDatabase(arr);
            onMediaChanged();
            sendRedirect(out, "/admin?ok=1");
        }

        private void handleDelete(HttpRequest req, OutputStream out) throws Exception {
            Map<String, String> q = parseQuery(req.path);
            String id = q.get("id");
            String pin = q.get("pin");
            if (!ADMIN_PIN.equals(pin)) {
                sendText(out, 403, "text/plain; charset=utf-8", "PIN خطأ");
                return;
            }
            JSONArray arr = readRawDatabaseArray();
            JSONArray newArr = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o.optString("id").equals(id)) {
                    File f = new File(mediaDir, o.optString("file"));
                    if (f.exists()) f.delete();
                } else {
                    newArr.put(o);
                }
            }
            saveDatabase(newArr);
            onMediaChanged();
            sendRedirect(out, "/admin");
        }

        private void handleClear(HttpRequest req, OutputStream out) throws Exception {
            Map<String, String> q = parseQuery(req.path);
            String pin = q.get("pin");
            if (!ADMIN_PIN.equals(pin)) {
                sendText(out, 403, "text/plain; charset=utf-8", "PIN خطأ");
                return;
            }
            JSONArray arr = readRawDatabaseArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                File f = new File(mediaDir, o.optString("file"));
                if (f.exists()) f.delete();
            }
            saveDatabase(new JSONArray());
            onMediaChanged();
            sendRedirect(out, "/admin");
        }

        private MultipartResult parseMultipart(byte[] body, String boundary) throws Exception {
            MultipartResult result = new MultipartResult();
            byte[] boundaryBytes = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
            int pos = indexOf(body, boundaryBytes, 0);
            while (pos >= 0) {
                int next = indexOf(body, boundaryBytes, pos + boundaryBytes.length);
                if (next < 0) break;
                int partStart = pos + boundaryBytes.length;
                if (partStart + 2 < body.length && body[partStart] == '\r' && body[partStart + 1] == '\n') partStart += 2;
                int headersEnd = indexOf(body, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), partStart);
                if (headersEnd > 0 && headersEnd < next) {
                    String headers = new String(body, partStart, headersEnd - partStart, StandardCharsets.UTF_8);
                    int dataStart = headersEnd + 4;
                    int dataEnd = next - 2; // before CRLF
                    if (dataEnd < dataStart) dataEnd = dataStart;
                    String disposition = "";
                    String pContentType = "";
                    for (String line : headers.split("\r?\n")) {
                        String lower = line.toLowerCase(Locale.US);
                        if (lower.startsWith("content-disposition:")) disposition = line;
                        if (lower.startsWith("content-type:")) pContentType = line.substring(line.indexOf(':') + 1).trim();
                    }
                    String name = attr(disposition, "name");
                    String filename = attr(disposition, "filename");
                    int len = Math.max(0, dataEnd - dataStart);
                    byte[] data = new byte[len];
                    System.arraycopy(body, dataStart, data, 0, len);
                    if ("pin".equals(name)) result.pin = new String(data, StandardCharsets.UTF_8).trim();
                    else if ("duration".equals(name)) {
                        try { result.duration = Integer.parseInt(new String(data, StandardCharsets.UTF_8).trim()); } catch (Exception ignored) {}
                    } else if ("files".equals(name) && filename != null && !filename.trim().isEmpty() && data.length > 0) {
                        UploadedFile uf = new UploadedFile();
                        uf.filename = filename;
                        uf.contentType = pContentType;
                        uf.data = data;
                        result.files.add(uf);
                    }
                }
                pos = next;
            }
            return result;
        }

        private String attr(String header, String key) {
            String look = key + "=\"";
            int i = header.indexOf(look);
            if (i < 0) return null;
            int start = i + look.length();
            int end = header.indexOf('"', start);
            if (end < 0) return null;
            return header.substring(start, end);
        }

        private void sendRedirect(OutputStream out, String location) throws IOException {
            String headers = "HTTP/1.1 303 See Other\r\n" +
                    "Location: " + location + "\r\n" +
                    "Connection: close\r\n\r\n";
            out.write(headers.getBytes(StandardCharsets.UTF_8));
        }

        private void sendText(OutputStream out, int code, String contentType, String body) throws IOException {
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            String status = code == 200 ? "OK" : code == 303 ? "See Other" : code == 400 ? "Bad Request" : code == 403 ? "Forbidden" : code == 404 ? "Not Found" : code == 413 ? "Payload Too Large" : "Error";
            String headers = "HTTP/1.1 " + code + " " + status + "\r\n" +
                    "Content-Type: " + contentType + "\r\n" +
                    "Content-Length: " + data.length + "\r\n" +
                    "Connection: close\r\n\r\n";
            out.write(headers.getBytes(StandardCharsets.UTF_8));
            out.write(data);
        }

        private String simplePage(String title, String message) {
            return "<!doctype html><html lang='ar' dir='rtl'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                    "<body style='font-family:Arial;background:#111;color:#fff;padding:30px'><h1>" + htmlEscape(title) + "</h1><p>" + htmlEscape(message) + "</p><a style='color:#ffd36a' href='/admin'>رجوع</a></body></html>";
        }

        private String adminHtml() {
            JSONArray arr = readRawDatabaseArray();
            StringBuilder list = new StringBuilder();
            for (int i = 0; i < arr.length(); i++) {
                try {
                    JSONObject o = arr.getJSONObject(i);
                    String id = o.optString("id");
                    String type = o.optString("type");
                    String file = o.optString("file");
                    String title = o.optString("title", file);
                    list.append("<div class='card'>")
                            .append("<div><b>").append(htmlEscape(title)).append("</b><br><span>").append(htmlEscape(type)).append("</span></div>")
                            .append("<form method='post' action='/delete?id=").append(htmlEscape(id)).append("&pin=' onsubmit='return addPin(this)'>")
                            .append("<button class='danger'>حذف</button></form>")
                            .append("</div>");
                } catch (Exception ignored) {}
            }
            if (list.length() == 0) list.append("<p class='empty'>لا يوجد صور أو فيديوهات حالياً.</p>");

            String ip = getLocalIpAddress();
            String url = "http://" + ip + ":" + PORT;

            return "<!doctype html>\n" +
                    "<html lang='ar' dir='rtl'>\n" +
                    "<head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>\n" +
                    "<title>Cafe Screen Local</title>\n" +
                    "<style>\n" +
                    "body{margin:0;background:#0f0f0f;color:#fff;font-family:Arial,Tahoma,sans-serif}.wrap{max-width:780px;margin:auto;padding:24px}.hero{background:#1b1b1b;border:1px solid #333;border-radius:22px;padding:22px;margin-bottom:18px}.brand{font-size:28px;font-weight:800;color:#ffd36a}.url{direction:ltr;background:#000;border:1px solid #444;padding:12px;border-radius:12px;margin-top:10px;overflow:auto}.box{background:#191919;border:1px solid #333;border-radius:20px;padding:18px;margin:16px 0}label{display:block;margin:12px 0 7px}input{width:100%;box-sizing:border-box;padding:14px;border-radius:12px;border:1px solid #555;background:#090909;color:#fff;font-size:16px}button{border:0;border-radius:12px;padding:12px 18px;background:#ffd36a;color:#111;font-weight:800;font-size:16px;margin-top:14px}.danger{background:#ff5b5b;color:#fff;margin:0}.card{display:flex;align-items:center;justify-content:space-between;gap:12px;background:#111;border:1px solid #333;border-radius:14px;padding:14px;margin:10px 0}.empty{color:#bbb}.small{color:#aaa;font-size:14px;line-height:1.7}.progress{display:none;margin-top:12px;color:#ffd36a}\n" +
                    "</style></head><body><div class='wrap'>\n" +
                    "<div class='hero'><div class='brand'>Cafe Screen Local</div><div class='small'>ارفع صور وفيديوهات من الهاتف، وستظهر مباشرة على شاشة الكافي. الهاتف والشاشة لازم يكونوا على نفس Wi‑Fi.</div><div class='url'>" + htmlEscape(url) + "</div></div>\n" +
                    "<div class='box'><h2>رفع صورة أو فيديو</h2><form id='uploadForm' method='post' action='/upload' enctype='multipart/form-data'>" +
                    "<label>PIN</label><input name='pin' type='password' value='' placeholder='اكتب PIN'>" +
                    "<label>مدة عرض الصور بالثواني</label><input name='duration' type='number' value='8' min='2' max='120'>" +
                    "<label>اختيار ملفات</label><input name='files' type='file' accept='image/*,video/*' multiple>" +
                    "<button type='submit'>رفع الآن</button><div id='progress' class='progress'>جاري الرفع... لا تغلق الصفحة.</div></form>" +
                    "<p class='small'>الأفضل للفيديو: MP4 أفقي 1920x1080 وحجم أقل من " + MAX_UPLOAD_MB + "MB.</p></div>\n" +
                    "<div class='box'><h2>المحتوى الحالي</h2>" + list + "</div>\n" +
                    "<div class='box'><h2>حذف الكل</h2><form method='post' action='/clear?pin=' onsubmit='return confirmClear(this)'><button class='danger'>حذف كل الملفات</button></form></div>\n" +
                    "</div><script>\n" +
                    "const form=document.getElementById('uploadForm');form.addEventListener('submit',()=>{document.getElementById('progress').style.display='block'});\n" +
                    "function pin(){return document.querySelector('input[name=pin]').value||prompt('اكتب PIN')||''}\n" +
                    "function addPin(f){f.action=f.action+encodeURIComponent(pin());return confirm('حذف هذا الملف؟')}\n" +
                    "function confirmClear(f){f.action=f.action+encodeURIComponent(pin());return confirm('متأكد من حذف كل الملفات؟')}\n" +
                    "</script></body></html>";
        }
    }

    private static class HttpRequest {
        String method;
        String path;
        Map<String, String> headers = new HashMap<>();
        byte[] body = new byte[0];
        boolean tooLarge = false;
    }

    private static class UploadedFile {
        String filename;
        String contentType;
        byte[] data;
    }

    private static class MultipartResult {
        String pin = "";
        int duration = DEFAULT_IMAGE_DURATION_SECONDS;
        List<UploadedFile> files = new ArrayList<>();
    }
}
