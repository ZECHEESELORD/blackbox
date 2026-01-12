package sh.harold.blackbox.core.json;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Minimal JSON writer with stable ordering controlled by call sites.
 */
public final class JsonWriter {
    private final Appendable out;
    private final Deque<Context> stack = new ArrayDeque<>();

    public JsonWriter(Appendable out) {
        this.out = Objects.requireNonNull(out, "out");
    }

    public JsonWriter beginObject() throws IOException {
        beforeValue();
        out.append('{');
        stack.push(new Context(Type.OBJECT));
        return this;
    }

    public JsonWriter endObject() throws IOException {
        Context context = expect(Type.OBJECT);
        if (context.expectingValue) {
            throw new IllegalStateException("Object field name without value.");
        }
        stack.pop();
        out.append('}');
        return this;
    }

    public JsonWriter beginArray() throws IOException {
        beforeValue();
        out.append('[');
        stack.push(new Context(Type.ARRAY));
        return this;
    }

    public JsonWriter endArray() throws IOException {
        expect(Type.ARRAY);
        stack.pop();
        out.append(']');
        return this;
    }

    public JsonWriter name(String name) throws IOException {
        Objects.requireNonNull(name, "name");
        Context context = expect(Type.OBJECT);
        if (context.expectingValue) {
            throw new IllegalStateException("Previous field missing a value.");
        }
        if (!context.first) {
            out.append(',');
        }
        context.first = false;
        context.expectingValue = true;
        writeStringLiteral(name);
        out.append(':');
        return this;
    }

    public JsonWriter value(String value) throws IOException {
        if (value == null) {
            return nullValue();
        }
        beforeValue();
        writeStringLiteral(value);
        return this;
    }

    public JsonWriter value(boolean value) throws IOException {
        beforeValue();
        out.append(value ? "true" : "false");
        return this;
    }

    public JsonWriter value(Number value) throws IOException {
        Objects.requireNonNull(value, "value");
        beforeValue();
        out.append(value.toString());
        return this;
    }

    public JsonWriter nullValue() throws IOException {
        beforeValue();
        out.append("null");
        return this;
    }

    private void beforeValue() throws IOException {
        if (stack.isEmpty()) {
            return;
        }
        Context context = stack.peek();
        if (context.type == Type.ARRAY) {
            if (!context.first) {
                out.append(',');
            }
            context.first = false;
        } else {
            if (!context.expectingValue) {
                throw new IllegalStateException("Object value without field name.");
            }
            context.expectingValue = false;
        }
    }

    private void writeStringLiteral(String value) throws IOException {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c <= 0x1F) {
                        out.append("\\u00");
                        out.append(hex((c >> 4) & 0xF));
                        out.append(hex(c & 0xF));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static char hex(int nibble) {
        return "0123456789abcdef".charAt(nibble & 0xF);
    }

    private Context expect(Type type) {
        if (stack.isEmpty() || stack.peek().type != type) {
            throw new IllegalStateException("JSON write stack mismatch.");
        }
        return stack.peek();
    }

    private enum Type {
        OBJECT,
        ARRAY
    }

    private static final class Context {
        private final Type type;
        private boolean first = true;
        private boolean expectingValue;

        private Context(Type type) {
            this.type = type;
        }
    }
}
