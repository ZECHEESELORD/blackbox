package sh.harold.blackbox.core.env;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextRedactorTest {

    @Test
    void defaultPatternsRedactCommonSecrets() {
        TextRedactor redactor = new TextRedactor(List.of());
        String input = "password=hunter2 "
            + "auth=Bearer eyJhbGciOiJIUzI1NiJ9.aaaaaaaaaaaa.bbbbbbbbbbbb "
            + "gh=ghp_1234567890ABCDEFGHIJKLMN "
            + "aws=AKIAABCDEFGHIJKLMNOP";

        String redacted = redactor.redact(input);

        assertTrue(redacted.contains("[REDACTED]"));
        assertFalse(redacted.contains("hunter2"));
        assertFalse(redacted.contains("eyJhbGciOiJIUzI1NiJ9.aaaaaaaaaaaa.bbbbbbbbbbbb"));
        assertFalse(redacted.contains("ghp_1234567890ABCDEFGHIJKLMN"));
        assertFalse(redacted.contains("AKIAABCDEFGHIJKLMNOP"));
    }

    @Test
    void customPatternsAreAdditiveToDefaults() {
        TextRedactor redactor = new TextRedactor(List.of("CUSTOM_SECRET_[A-Z0-9]+"));
        String input = "ip=192.168.10.20 custom=CUSTOM_SECRET_ABC123";

        String redacted = redactor.redact(input);

        assertFalse(redacted.contains("192.168.10.20"));
        assertFalse(redacted.contains("CUSTOM_SECRET_ABC123"));
    }

    @Test
    void invalidCustomPatternDoesNotDisableDefaultRedaction() {
        TextRedactor redactor = new TextRedactor(List.of("["));
        String redacted = redactor.redact("password=swordfish");

        assertFalse(redacted.contains("swordfish"));
    }
}
