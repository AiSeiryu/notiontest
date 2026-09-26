package com.seiryu.sunoexport;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class Net {
    private Net() {}

    public static Response get(String url) throws Exception {
        LabPolicy.validate(url);
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent", "SunoExportToolkit-Android-Lab/0.1");
        int code = c.getResponseCode();
        if (code >= 300 && code < 400) {
            String loc = c.getHeaderField("Location");
            if (loc == null) throw new IllegalStateException("Redirect without Location header.");
            String next = new URL(new URL(url), loc).toString();
            LabPolicy.validate(next);
            c.disconnect();
            return get(next);
        }
        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return new Response(out.toByteArray(), c.getURL().toString(), c.getContentType());
        } finally {
            c.disconnect();
        }
    }

    public static final class Response {
        public final byte[] bytes;
        public final String finalUrl;
        public final String contentType;
        Response(byte[] bytes, String finalUrl, String contentType) {
            this.bytes = bytes;
            this.finalUrl = finalUrl;
            this.contentType = contentType;
        }
        public String text() { return new String(bytes, java.nio.charset.StandardCharsets.UTF_8); }
    }
}
