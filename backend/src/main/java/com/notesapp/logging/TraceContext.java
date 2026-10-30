package com.notesapp.logging;

import java.util.UUID;
import java.util.regex.Pattern;

/** W3C trace-context correlation; does not create or export tracing spans. */
public record TraceContext(String traceId, String parent) {
    private static final Pattern VALID = Pattern.compile("00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})");

    public static TraceContext from(String header) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        String flags = "00";
        if (header != null) {
            var match = VALID.matcher(header);
            if (match.matches() && !match.group(1).equals("0".repeat(32))
                    && !match.group(2).equals("0".repeat(16))) {
                traceId = match.group(1);
                flags = match.group(3);
            }
        }
        String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return new TraceContext(traceId, "00-" + traceId + "-" + spanId + "-" + flags);
    }
}
