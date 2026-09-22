package DataStructures;

import java.util.IdentityHashMap;
import java.util.function.Supplier;

/**
 * Shared package-private formatter for Python-like representations used by
 * the custom data structures.
 */
final class PythonRepr {

    // Track only the active traversal path: repeated, non-cyclic values must
    // still be printed in full. Identity avoids invoking container hashCode().
    private static final ThreadLocal<IdentityHashMap<Object, Boolean>> ACTIVE =
        ThreadLocal.withInitial(IdentityHashMap::new);

    private PythonRepr() {
        // Utility class.
    }

    static String format(Object value) {
        if (value == null) {
            return "None";
        }

        if (value instanceof String string) {
            return "'" + escape(string) + "'";
        }

        if (value instanceof Character character) {
            return "'" + escape(character.toString()) + "'";
        }

        if (value instanceof Boolean bool) {
            return bool ? "True" : "False";
        }

        return String.valueOf(value);
    }

    /** Guards both direct and indirect recursive container representations. */
    static String container(Object owner, String recursive, Supplier<String> formatter) {
        IdentityHashMap<Object, Boolean> active = ACTIVE.get();
        if (active.containsKey(owner)) {
            return recursive;
        }

        active.put(owner, Boolean.TRUE);
        try {
            return formatter.get();
        } finally {
            active.remove(owner);
            if (active.isEmpty()) {
                ACTIVE.remove();
            }
        }
    }

    static String sequence(
        Iterable<?> values, String opening, String closing, String recursive,
        boolean singletonComma
    ) {
        return container(values, recursive, () -> {
            StringBuilder result = new StringBuilder(opening);
            int count = 0;
            for (Object value : values) {
                if (count++ > 0) {
                    result.append(", ");
                }
                result.append(format(value));
            }
            if (singletonComma && count == 1) {
                result.append(',');
            }
            return result.append(closing).toString();
        });
    }

    private static String escape(String value) {
        StringBuilder result = new StringBuilder(value.length());

        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);

            switch (character) {
                case '\\' -> result.append("\\\\");
                case '\'' -> result.append("\\'");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                default -> {
                    if (character < 0x20 || (character >= 0x7f && character <= 0x9f)) {
                        result.append("\\x");
                        result.append(Character.forDigit(character >>> 4, 16));
                        result.append(Character.forDigit(character & 0xf, 16));
                    } else {
                        result.append(character);
                    }
                }
            }
        }

        return result.toString();
    }
}
