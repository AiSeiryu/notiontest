package com.seiryu.sunoexport;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public final class HlsParser {
    private HlsParser() {}

    public static Result parse(String playlistUrl, String text) throws Exception {
        URI base = new URI(playlistUrl);
        List<String> variants = new ArrayList<>();
        List<String> segments = new ArrayList<>();
        boolean expectVariant = false;
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                expectVariant = true;
                continue;
            }
            if (line.startsWith("#")) continue;
            String absolute = base.resolve(line).toString();
            LabPolicy.validate(absolute);
            if (expectVariant || absolute.toLowerCase().contains(".m3u8")) {
                variants.add(absolute);
                expectVariant = false;
            } else {
                segments.add(absolute);
            }
        }
        return new Result(variants, segments);
    }

    public static final class Result {
        public final List<String> variants;
        public final List<String> segments;
        Result(List<String> variants, List<String> segments) {
            this.variants = variants;
            this.segments = segments;
        }
    }
}
