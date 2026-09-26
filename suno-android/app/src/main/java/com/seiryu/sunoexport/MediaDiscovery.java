package com.seiryu.sunoexport;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MediaDiscovery {
    private static final Pattern ABS_URL = Pattern.compile("https?://[^\\s\\\"'<>]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR = Pattern.compile("(?:src|href|content|url)\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']", Pattern.CASE_INSENSITIVE);
    private static final String[] EXT = {".mp3", ".m4a", ".aac", ".wav", ".flac", ".ogg", ".opus", ".mp4", ".webm", ".m3u8"};

    private MediaDiscovery() {}

    public static List<String> extract(String pageUrl, String html) throws Exception {
        URI base = new URI(pageUrl);
        Set<String> found = new LinkedHashSet<>();
        Matcher abs = ABS_URL.matcher(html.replace("\\/", "/"));
        while (abs.find()) addCandidate(base, abs.group(), found);
        Matcher attr = ATTR.matcher(html);
        while (attr.find()) addCandidate(base, attr.group(1).replace("\\/", "/"), found);
        List<String> out = new ArrayList<>(found);
        Collections.sort(out);
        return out;
    }

    private static void addCandidate(URI base, String raw, Set<String> out) {
        try {
            URI u = base.resolve(raw);
            String s = u.toString();
            String lower = s.toLowerCase();
            boolean looksMedia = lower.contains("audio") || lower.contains("stream") || lower.contains("media") || lower.contains("playlist");
            String path = u.getPath() == null ? "" : u.getPath().toLowerCase();
            for (String ext : EXT) if (path.endsWith(ext)) looksMedia = true;
            if (looksMedia) {
                LabPolicy.validate(s);
                out.add(s);
            }
        } catch (Exception ignored) {
            // Public or malformed candidates are intentionally ignored.
        }
    }
}
