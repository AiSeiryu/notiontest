package com.seiryu.sunoexport;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {
    private static final int PICK_AUDIO = 1001;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private LinearLayout body;
    private TextView status;
    private Uri selectedAudio;
    private EditText titleInput;
    private EditText creatorInput;
    private EditText lyricsInput;
    private EditText labUrlInput;
    private EditText assetIndexInput;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        showHome();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void showHome() {
        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(body);
        setContentView(scroll);

        TextView h = text("Suno Export Toolkit", 26, true);
        body.addView(h);
        body.addView(text("Android 10+ • official-file manager + private-network downloader lab", 14, false));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        Button v1 = button("v1 Export Manager");
        Button v2 = button("v2 Lab Downloader");
        nav.addView(v1, new LinearLayout.LayoutParams(0, dp(52), 1));
        nav.addView(v2, new LinearLayout.LayoutParams(0, dp(52), 1));
        body.addView(nav);

        status = text("Ready", 13, false);
        status.setPadding(0, dp(10), 0, dp(12));
        body.addView(status);
        v1.setOnClickListener(v -> buildV1());
        v2.setOnClickListener(v -> buildV2());
        buildV1();
    }

    private void clearBelowHeader() {
        while (body.getChildCount() > 3) body.removeViewAt(3);
    }

    private void buildV1() {
        clearBelowHeader();
        body.addView(text("v1 • Official Export Manager", 21, true));
        body.addView(text("Import an audio file you already downloaded, then create a visible Android backup in Downloads/Suno Export Toolkit.", 14, false));
        Button pick = button("Choose audio file");
        body.addView(pick);
        pick.setOnClickListener(v -> pickAudio());
        titleInput = field("Title (optional)");
        creatorInput = field("Creator (optional)");
        lyricsInput = field("Lyrics (optional)");
        lyricsInput.setMinLines(5);
        body.addView(titleInput);
        body.addView(creatorInput);
        body.addView(lyricsInput);
        Button export = button("Create backup package");
        body.addView(export);
        export.setOnClickListener(v -> exportSelected());
        body.addView(text("Output: original audio + metadata.json + lyrics.txt (when supplied) + ZIP. Audio transcoding is intentionally left to the desktop build in this first Android release.", 12, false));
    }

    private void buildV2() {
        clearBelowHeader();
        body.addView(text("v2 • Lab Downloader", 21, true));
        body.addView(text("Fetches a page, discovers direct audio/HLS assets, downloads the selected asset and assembles HLS segments. It accepts only localhost/private LAN hosts.", 14, false));
        labUrlInput = field("Lab page URL");
        labUrlInput.setText("http://192.168.1.2:8765/index.html");
        assetIndexInput = field("Asset index");
        assetIndexInput.setText("0");
        body.addView(labUrlInput);
        body.addView(assetIndexInput);
        Button scan = button("Scan assets");
        Button download = button("Download selected asset");
        body.addView(scan);
        body.addView(download);
        scan.setOnClickListener(v -> scanLab());
        download.setOnClickListener(v -> downloadLab());
        body.addView(text("Tip: run lab_server.py on your PC and replace 192.168.1.2 with that PC's private LAN IP. Android cannot reach the PC's 127.0.0.1.", 12, false));
    }

    private void pickAudio() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        startActivityForResult(i, PICK_AUDIO);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_AUDIO && resultCode == RESULT_OK && data != null) {
            selectedAudio = data.getData();
            if (selectedAudio != null) {
                try { getContentResolver().takePersistableUriPermission(selectedAudio, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
                String name = displayName(selectedAudio);
                if (titleInput != null && titleInput.getText().toString().trim().isEmpty()) titleInput.setText(stripExt(name));
                setStatus("Selected: " + name);
            }
        }
    }

    private void exportSelected() {
        if (selectedAudio == null) { toast("Choose an audio file first."); return; }
        final String title = clean(titleInput.getText().toString(), stripExt(displayName(selectedAudio)));
        final String creator = creatorInput.getText().toString().trim();
        final String lyrics = lyricsInput.getText().toString().trim();
        setStatus("Exporting…");
        worker.submit(() -> {
            try {
                byte[] audio = readAll(selectedAudio);
                String originalName = displayName(selectedAudio);
                String safeTitle = sanitize(title);
                String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                String dir = safeTitle + "_" + stamp;
                Storage.writeDownload(this, dir, originalName, mimeFor(originalName), audio);
                String metadata = "{\n" +
                        "  \"title\": \"" + json(title) + "\",\n" +
                        "  \"creator\": \"" + json(creator) + "\",\n" +
                        "  \"original_file\": \"" + json(originalName) + "\",\n" +
                        "  \"created_by\": \"Suno Export Toolkit Android 0.1\"\n" +
                        "}\n";
                Storage.writeDownload(this, dir, "metadata.json", "application/json", metadata.getBytes(StandardCharsets.UTF_8));
                if (!lyrics.isEmpty()) Storage.writeDownload(this, dir, "lyrics.txt", "text/plain", lyrics.getBytes(StandardCharsets.UTF_8));
                byte[] zip = makeZip(originalName, audio, metadata, lyrics);
                Storage.writeDownload(this, dir, safeTitle + "_backup.zip", "application/zip", zip);
                ui.post(() -> setStatus("Done. Saved under Downloads/Suno Export Toolkit/" + dir));
            } catch (Exception e) {
                ui.post(() -> setStatus("Export error: " + e.getMessage()));
            }
        });
    }

    private void scanLab() {
        final String page = labUrlInput.getText().toString().trim();
        setStatus("Scanning…");
        worker.submit(() -> {
            try {
                LabPolicy.validate(page);
                Net.Response r = Net.get(page);
                List<String> media = MediaDiscovery.extract(r.finalUrl, r.text());
                StringBuilder sb = new StringBuilder("Found ").append(media.size()).append(" assets");
                for (int i = 0; i < media.size(); i++) sb.append("\n[").append(i).append("] ").append(media.get(i));
                ui.post(() -> setStatus(sb.toString()));
            } catch (Exception e) { ui.post(() -> setStatus("Scan error: " + e.getMessage())); }
        });
    }

    private void downloadLab() {
        final String page = labUrlInput.getText().toString().trim();
        final int index;
        try { index = Integer.parseInt(assetIndexInput.getText().toString().trim()); }
        catch (Exception e) { toast("Asset index must be a number."); return; }
        setStatus("Downloading…");
        worker.submit(() -> {
            try {
                LabPolicy.validate(page);
                Net.Response r = Net.get(page);
                List<String> media = MediaDiscovery.extract(r.finalUrl, r.text());
                if (index < 0 || index >= media.size()) throw new IndexOutOfBoundsException("Found " + media.size() + " assets.");
                String selected = media.get(index);
                byte[] bytes;
                String name;
                if (new java.net.URI(selected).getPath().toLowerCase().endsWith(".m3u8")) {
                    bytes = downloadHls(selected, 0);
                    name = "hls_capture.ts";
                } else {
                    bytes = Net.get(selected).bytes;
                    String path = new java.net.URI(selected).getPath();
                    int slash = path.lastIndexOf('/');
                    name = slash >= 0 ? path.substring(slash + 1) : "download.bin";
                    if (name.isEmpty()) name = "download.bin";
                }
                Storage.writeDownload(this, "Lab", name, mimeFor(name), bytes);
                final String msg = "Saved Downloads/Suno Export Toolkit/Lab/" + name + " (" + bytes.length + " bytes)";
                ui.post(() -> setStatus(msg));
            } catch (Exception e) { ui.post(() -> setStatus("Download error: " + e.getMessage())); }
        });
    }

    private byte[] downloadHls(String playlistUrl, int depth) throws Exception {
        if (depth > 3) throw new IllegalStateException("HLS nesting too deep.");
        Net.Response p = Net.get(playlistUrl);
        HlsParser.Result parsed = HlsParser.parse(p.finalUrl, p.text());
        if (!parsed.segments.isEmpty()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (String seg : parsed.segments) out.write(Net.get(seg).bytes);
            return out.toByteArray();
        }
        if (!parsed.variants.isEmpty()) return downloadHls(parsed.variants.get(0), depth + 1);
        throw new IllegalStateException("No HLS media segments found.");
    }

    private byte[] makeZip(String audioName, byte[] audio, String metadata, String lyrics) throws Exception {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(raw)) {
            putZip(z, audioName, audio);
            putZip(z, "metadata.json", metadata.getBytes(StandardCharsets.UTF_8));
            if (!lyrics.isEmpty()) putZip(z, "lyrics.txt", lyrics.getBytes(StandardCharsets.UTF_8));
        }
        return raw.toByteArray();
    }

    private static void putZip(ZipOutputStream z, String name, byte[] data) throws Exception {
        z.putNextEntry(new ZipEntry(name)); z.write(data); z.closeEntry();
    }

    private byte[] readAll(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IllegalStateException("Cannot open selected file.");
            byte[] buf = new byte[64 * 1024]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {}
        return "audio.bin";
    }

    private EditText field(String hint) {
        EditText e = new EditText(this); e.setHint(hint); e.setTextSize(16); e.setPadding(dp(10), dp(10), dp(10), dp(10));
        e.setLayoutParams(new LinearLayout.LayoutParams(-1, -2)); return e;
    }
    private Button button(String label) { Button b = new Button(this); b.setText(label); b.setAllCaps(false); return b; }
    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setPadding(0, dp(6), 0, dp(6));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }
    private void setStatus(String s) { status.setText(s); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private static String stripExt(String s) { int i=s.lastIndexOf('.'); return i>0?s.substring(0,i):s; }
    private static String clean(String v, String fallback) { String s=v==null?"":v.trim(); return s.isEmpty()?fallback:s; }
    private static String sanitize(String s) { return s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").replaceAll("\\s+", " ").trim(); }
    private static String json(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r"); }
    private static String mimeFor(String name) {
        String n=name.toLowerCase();
        if(n.endsWith(".mp3"))return"audio/mpeg"; if(n.endsWith(".wav"))return"audio/wav"; if(n.endsWith(".flac"))return"audio/flac";
        if(n.endsWith(".m4a"))return"audio/mp4"; if(n.endsWith(".aac"))return"audio/aac"; if(n.endsWith(".ogg")||n.endsWith(".opus"))return"audio/ogg";
        if(n.endsWith(".ts"))return"video/mp2t"; if(n.endsWith(".zip"))return"application/zip"; return"application/octet-stream";
    }
}
