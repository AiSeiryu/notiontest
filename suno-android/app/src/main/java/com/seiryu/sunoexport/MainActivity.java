package com.seiryu.sunoexport;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
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
    private TextView sunoResult;
    private EditText sunoUrlInput;
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

        body.addView(text("Suno Export Toolkit", 26, true));
        body.addView(text("Samsung S10e / Android 12 • светлая тема", 14, false));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        Button url = button("Suno URL");
        Button file = button("Файл");
        Button lab = button("Lab");
        nav.addView(url, new LinearLayout.LayoutParams(0, dp(52), 1));
        nav.addView(file, new LinearLayout.LayoutParams(0, dp(52), 1));
        nav.addView(lab, new LinearLayout.LayoutParams(0, dp(52), 1));
        body.addView(nav);

        status = text("Готово", 13, false);
        status.setPadding(0, dp(10), 0, dp(12));
        body.addView(status);

        url.setOnClickListener(v -> buildSunoUrl());
        file.setOnClickListener(v -> buildFile());
        lab.setOnClickListener(v -> buildLab());
        buildSunoUrl();
    }

    private void clearBelowHeader() {
        while (body.getChildCount() > 3) body.removeViewAt(3);
    }

    private void buildSunoUrl() {
        clearBelowHeader();
        body.addView(text("Скачать / открыть песню по Suno URL", 21, true));
        body.addView(text("Вставь публичную ссылку вида https://suno.com/s/… Сначала приложение попробует получить данные трека. Затем можно открыть страницу Suno или внешний UseSuno Downloader.", 14, false));

        sunoUrlInput = field("https://suno.com/s/...");
        body.addView(sunoUrlInput);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button paste = button("Вставить");
        Button load = button("Загрузить данные");
        row.addView(paste, new LinearLayout.LayoutParams(0, dp(52), 1));
        row.addView(load, new LinearLayout.LayoutParams(0, dp(52), 1));
        body.addView(row);

        sunoResult = text("Название и описание появятся здесь.", 14, false);
        sunoResult.setPadding(dp(8), dp(12), dp(8), dp(12));
        body.addView(sunoResult);

        Button openSuno = button("Открыть трек в Suno");
        Button openUseSuno = button("UseSuno Downloader — открыть сайт");
        body.addView(openSuno);
        body.addView(openUseSuno);

        body.addView(text("Для UseSuno приложение копирует Suno-ссылку в буфер обмена и открывает downloader в браузере. На сайте останется только вставить ссылку.", 12, false));

        paste.setOnClickListener(v -> pasteClipboard());
        load.setOnClickListener(v -> loadSunoInfo());
        openSuno.setOnClickListener(v -> openCurrentSuno());
        openUseSuno.setOnClickListener(v -> openUseSuno());
    }

    private void buildFile() {
        clearBelowHeader();
        body.addView(text("Локальный файл / Backup Manager", 21, true));
        body.addView(text("Если аудиофайл уже есть на телефоне, можно сохранить его вместе с metadata и lyrics в отдельную папку и ZIP.", 14, false));
        Button pick = button("Выбрать аудиофайл");
        body.addView(pick);
        pick.setOnClickListener(v -> pickAudio());

        titleInput = field("Название (необязательно)");
        creatorInput = field("Автор (необязательно)");
        lyricsInput = field("Текст песни (необязательно)");
        lyricsInput.setMinLines(5);
        body.addView(titleInput);
        body.addView(creatorInput);
        body.addView(lyricsInput);

        Button export = button("Создать backup package");
        body.addView(export);
        export.setOnClickListener(v -> exportSelected());

        body.addView(text("Результат: оригинальный файл + metadata.json + lyrics.txt + ZIP в Downloads/Suno Export Toolkit.", 12, false));
    }

    private void buildLab() {
        clearBelowHeader();
        body.addView(text("Lab Downloader", 21, true));
        body.addView(text("Технический режим для localhost/private LAN: HTML → media discovery → direct/HLS → сохранение результата.", 14, false));

        labUrlInput = field("http://192.168.1.2:8765/index.html");
        labUrlInput.setText("http://192.168.1.2:8765/index.html");
        assetIndexInput = field("Индекс asset");
        assetIndexInput.setText("0");
        body.addView(labUrlInput);
        body.addView(assetIndexInput);

        Button scan = button("Найти media assets");
        Button download = button("Скачать выбранный asset");
        body.addView(scan);
        body.addView(download);
        scan.setOnClickListener(v -> scanLab());
        download.setOnClickListener(v -> downloadLab());

        body.addView(text("Если lab_server.py запущен на ПК, используй локальный IP компьютера, например 192.168.1.34, а не 127.0.0.1.", 12, false));
    }

    private void pasteClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip() == null || cm.getPrimaryClip().getItemCount() == 0) {
            toast("Буфер обмена пуст.");
            return;
        }
        CharSequence cs = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
        if (cs != null) sunoUrlInput.setText(cs.toString().trim());
    }

    private String currentSunoUrl() {
        return sunoUrlInput == null ? "" : sunoUrlInput.getText().toString().trim();
    }

    private void loadSunoInfo() {
        final String url = currentSunoUrl();
        try { SunoPage.validate(url); }
        catch (Exception e) { setStatus("Ошибка ссылки: " + e.getMessage()); return; }

        setStatus("Получаю данные страницы Suno…");
        sunoResult.setText("Загрузка…");
        worker.submit(() -> {
            try {
                SunoPage.Info info = SunoPage.load(url);
                StringBuilder sb = new StringBuilder();
                sb.append(info.title);
                if (!info.description.isEmpty()) sb.append("\n\n").append(info.description);
                sb.append("\n\n").append(info.finalUrl);
                ui.post(() -> {
                    sunoResult.setText(sb.toString());
                    setStatus("Данные трека получены.");
                });
            } catch (Exception e) {
                ui.post(() -> {
                    sunoResult.setText("Не удалось автоматически прочитать metadata. Ссылку всё равно можно открыть кнопками ниже.");
                    setStatus("Suno: " + e.getMessage());
                });
            }
        });
    }

    private void openCurrentSuno() {
        String url = currentSunoUrl();
        try {
            SunoPage.validate(url);
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast(e.getMessage());
        }
    }

    private void openUseSuno() {
        String url = currentSunoUrl();
        try {
            SunoPage.validate(url);
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Suno URL", url));
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://usesuno.com/tools/downloader/")));
            toast("Suno URL скопирован. Вставь его в поле downloader.");
        } catch (Exception e) {
            toast(e.getMessage());
        }
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
                setStatus("Выбран файл: " + name);
            }
        }
    }

    private void exportSelected() {
        if (selectedAudio == null) { toast("Сначала выбери аудиофайл."); return; }
        final String title = clean(titleInput.getText().toString(), stripExt(displayName(selectedAudio)));
        final String creator = creatorInput.getText().toString().trim();
        final String lyrics = lyricsInput.getText().toString().trim();
        setStatus("Создаю backup…");
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
                        "  \"created_by\": \"Suno Export Toolkit Android URL 0.2\"\n" +
                        "}\n";
                Storage.writeDownload(this, dir, "metadata.json", "application/json", metadata.getBytes(StandardCharsets.UTF_8));
                if (!lyrics.isEmpty()) Storage.writeDownload(this, dir, "lyrics.txt", "text/plain", lyrics.getBytes(StandardCharsets.UTF_8));
                byte[] zip = makeZip(originalName, audio, metadata, lyrics);
                Storage.writeDownload(this, dir, safeTitle + "_backup.zip", "application/zip", zip);
                ui.post(() -> setStatus("Готово: Downloads/Suno Export Toolkit/" + dir));
            } catch (Exception e) {
                ui.post(() -> setStatus("Ошибка backup: " + e.getMessage()));
            }
        });
    }

    private void scanLab() {
        final String page = labUrlInput.getText().toString().trim();
        setStatus("Сканирую…");
        worker.submit(() -> {
            try {
                LabPolicy.validate(page);
                Net.Response r = Net.get(page);
                List<String> media = MediaDiscovery.extract(r.finalUrl, r.text());
                StringBuilder sb = new StringBuilder("Найдено assets: ").append(media.size());
                for (int i = 0; i < media.size(); i++) sb.append("\n[").append(i).append("] ").append(media.get(i));
                ui.post(() -> setStatus(sb.toString()));
            } catch (Exception e) { ui.post(() -> setStatus("Ошибка scan: " + e.getMessage())); }
        });
    }

    private void downloadLab() {
        final String page = labUrlInput.getText().toString().trim();
        final int index;
        try { index = Integer.parseInt(assetIndexInput.getText().toString().trim()); }
        catch (Exception e) { toast("Индекс должен быть числом."); return; }
        setStatus("Скачиваю…");
        worker.submit(() -> {
            try {
                LabPolicy.validate(page);
                Net.Response r = Net.get(page);
                List<String> media = MediaDiscovery.extract(r.finalUrl, r.text());
                if (index < 0 || index >= media.size()) throw new IndexOutOfBoundsException("Найдено assets: " + media.size());
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
                final String msg = "Сохранено: Downloads/Suno Export Toolkit/Lab/" + name + " (" + bytes.length + " bytes)";
                ui.post(() -> setStatus(msg));
            } catch (Exception e) { ui.post(() -> setStatus("Ошибка download: " + e.getMessage())); }
        });
    }

    private byte[] downloadHls(String playlistUrl, int depth) throws Exception {
        if (depth > 3) throw new IllegalStateException("Слишком большая вложенность HLS.");
        Net.Response p = Net.get(playlistUrl);
        HlsParser.Result parsed = HlsParser.parse(p.finalUrl, p.text());
        if (!parsed.segments.isEmpty()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (String seg : parsed.segments) out.write(Net.get(seg).bytes);
            return out.toByteArray();
        }
        if (!parsed.variants.isEmpty()) return downloadHls(parsed.variants.get(0), depth + 1);
        throw new IllegalStateException("HLS segments не найдены.");
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
        z.putNextEntry(new ZipEntry(name));
        z.write(data);
        z.closeEntry();
    }

    private byte[] readAll(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IllegalStateException("Не удалось открыть файл.");
            byte[] buf = new byte[64 * 1024];
            int n;
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
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(16);
        e.setPadding(dp(10), dp(10), dp(10), dp(10));
        e.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return e;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        return b;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setPadding(0, dp(6), 0, dp(6));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private void setStatus(String s) { status.setText(s); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private static String stripExt(String s) { int i = s.lastIndexOf('.'); return i > 0 ? s.substring(0, i) : s; }
    private static String clean(String v, String fallback) { String s = v == null ? "" : v.trim(); return s.isEmpty() ? fallback : s; }
    private static String sanitize(String s) { return s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").replaceAll("\\s+", " ").trim(); }
    private static String json(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r"); }
    private static String mimeFor(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".wav")) return "audio/wav";
        if (n.endsWith(".flac")) return "audio/flac";
        if (n.endsWith(".m4a")) return "audio/mp4";
        if (n.endsWith(".aac")) return "audio/aac";
        if (n.endsWith(".ogg") || n.endsWith(".opus")) return "audio/ogg";
        if (n.endsWith(".ts")) return "video/mp2t";
        if (n.endsWith(".zip")) return "application/zip";
        return "application/octet-stream";
    }
}
