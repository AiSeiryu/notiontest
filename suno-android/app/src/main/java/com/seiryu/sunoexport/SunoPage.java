package com.seiryu.sunoexport;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SunoPage {
    private static final Pattern META = Pattern.compile("<meta\\s+[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR = Pattern.compile("([a-zA-Z_:.-]+)\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);

    private SunoPage() {}

    public static URI validate(String raw) throws Exception {
        URI uri = new URI(raw.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("Нужна ссылка https://suno.com/...");
        String host = uri.getHost();
        if (!isSunoHost(host)) throw new IllegalArgumentException("Поддерживаются ссылки suno.com.");
        String p = uri.getPath() == null ? "" : uri.getPath();
        if (!(p.startsWith("/s/") || p.startsWith("/song/") || p.startsWith("/hook/"))) {
            throw new IllegalArgumentException("Ожидается ссылка suno.com/s/…, /song/… или /hook/…");
        }
        return uri;
    }

    private static boolean isSunoHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals("suno.com") || h.endsWith(".suno.com");
    }

    public static Info load(String raw) throws Exception {
        URI validated = validate(raw);
        String current = validated.toString();
        for (int redirects = 0; redirects < 5; redirects++) {
            HttpURLConnection c = (HttpURLConnection) new URL(current).openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(18000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IllegalStateException("Suno вернул redirect без адреса.");
                URI next = new URI(current).resolve(loc);
                if (!isSunoHost(next.getHost())) throw new IllegalStateException("Suno перенаправил на неожиданный домен.");
                current = next.toString();
                continue;
            }
            if (code < 200 || code >= 300) {
                c.disconnect();
                throw new IllegalStateException("Suno HTTP " + code);
            }
            byte[] bytes;
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[32 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    if (out.size() > 2_000_000) break;
                }
                bytes = out.toByteArray();
            } finally {
                c.disconnect();
            }
            String html = new String(bytes, StandardCharsets.UTF_8);
            String title = firstNonEmpty(meta(html, "property", "og:title"), tagTitle(html), "Suno track");
            String description = firstNonEmpty(meta(html, "property", "og:description"), meta(html, "name", "description"), "");
            String image = firstNonEmpty(meta(html, "property", "og:image"), "");
            return new Info(current, decode(title), decode(description), image);
        }
        throw new IllegalStateException("Слишком много redirect.");
    }

    private static String meta(String html, String attrName, String attrValue) {
        Matcher m = META.matcher(html);
        while (m.find()) {
            String tag = m.group();
            String key = null, content = null;
            Matcher a = ATTR.matcher(tag);
            while (a.find()) {
                String name = a.group(1);
                String value = a.group(2);
                if (name.equalsIgnoreCase(attrName)) key = value;
                if (name.equalsIgnoreCase("content")) content = value;
            }
            if (key != null && key.equalsIgnoreCase(attrValue) && content != null) return content;
        }
        return "";
    }

    private static String tagTitle(String html) {
        Matcher m = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        return m.find() ? m.group(1).trim() : "";
    }

    private static String firstNonEmpty(String... values) {
        for (String s : values) if (s != null && !s.trim().isEmpty()) return s.trim();
        return "";
    }

    private static String decode(String s) {
        return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&lt;", "<").replace("&gt;", ">");
    }

    public static final class Info {
        public final String finalUrl;
        public final String title;
        public final String description;
        public final String imageUrl;
        Info(String finalUrl, String title, String description, String imageUrl) {
            this.finalUrl = finalUrl;
            this.title = title;
            this.description = description;
            this.imageUrl = imageUrl;
        }
    }
}
