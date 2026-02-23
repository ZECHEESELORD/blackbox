package sh.harold.blackbox.core.env;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class TextRedactor {
    private static final System.Logger LOGGER = System.getLogger(TextRedactor.class.getName());

    public static final List<String> DEFAULT_PATTERNS = List.of(
        "(?<!\\d)(?:\\d{1,3}\\.){3}\\d{1,3}(?!\\d)",
        "(?<![0-9A-Fa-f:])(?:[0-9A-Fa-f]{0,4}:){2,7}[0-9A-Fa-f]{0,4}(?![0-9A-Fa-f:])",
        "\\[(?:[0-9A-Fa-f]{0,4}:){2,7}[0-9A-Fa-f]{0,4}(?:%[0-9A-Za-z_.\\-]+)?\\](?::\\d{1,5})?",
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
        "(?i)\\b(?:password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key|client[_-]?secret|session(?:id)?|cookie)\\b\\s*[:=]\\s*[^\\s,;]+",
        "(?i)\\b(?:authorization|proxy-authorization)\\s*:\\s*bearer\\s+[A-Za-z0-9._\\-~+/]+=*",
        "\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b",
        "https://(?:ptb\\.|canary\\.)?discord(?:app)?\\.com/api/webhooks/[0-9]{17,20}/[A-Za-z0-9._\\-]+",
        "\\bgh[pousr]_[A-Za-z0-9]{20,}\\b",
        "\\b(?:AKIA|ASIA)[A-Z0-9]{16}\\b",
        "\\b[a-zA-Z][a-zA-Z0-9+.-]*://[^\\s/@:]+:[^\\s/@]+@",
        "-----BEGIN(?: [A-Z0-9]+)? PRIVATE KEY-----[\\s\\S]*?-----END(?: [A-Z0-9]+)? PRIVATE KEY-----"
    );

    private final List<Pattern> patterns;

    public TextRedactor(List<String> regexes) {
        Objects.requireNonNull(regexes, "regexes");
        List<String> mergedRegexes = mergeWithDefaults(regexes);
        List<Pattern> compiled = new ArrayList<>(mergedRegexes.size());
        for (String regex : mergedRegexes) {
            if (regex == null || regex.isBlank()) {
                continue;
            }
            try {
                compiled.add(Pattern.compile(regex));
            } catch (PatternSyntaxException e) {
                LOGGER.log(System.Logger.Level.WARNING,
                    "Skipping invalid redact pattern: " + regex, e);
            }
        }
        this.patterns = List.copyOf(compiled);
    }

    public String redact(String text) {
        if (text == null || text.isEmpty() || patterns.isEmpty()) {
            return text;
        }
        for (Pattern p : patterns) {
            text = p.matcher(text).replaceAll("[REDACTED]");
        }
        return text;
    }

    public byte[] redact(byte[] data) {
        if (data == null || data.length == 0 || patterns.isEmpty()) {
            return data;
        }
        String text = new String(data, StandardCharsets.UTF_8);
        String redacted = redact(text);
        if (text.equals(redacted)) {
            return data;
        }
        return redacted.getBytes(StandardCharsets.UTF_8);
    }

    public boolean hasPatterns() {
        return !patterns.isEmpty();
    }

    private static List<String> mergeWithDefaults(List<String> regexes) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(DEFAULT_PATTERNS);
        merged.addAll(regexes);
        return List.copyOf(merged);
    }
}
