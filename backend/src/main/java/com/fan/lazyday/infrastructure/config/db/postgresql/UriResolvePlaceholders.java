package com.fan.lazyday.infrastructure.config.db.postgresql;


import jakarta.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UriResolvePlaceholders {
    private static final Pattern NAMES_PATTERN = Pattern.compile("\\{([^/]+?)}");

    public static String expandUriComponent(String source, Charset charset, Object... uriVariableValues) {
        UriTemplateVariables uriTemplateVariables = new VarArgsTemplateVariables(uriVariableValues);
        UnaryOperator<String> variableEncoder = (value) -> {
            try {
                return encodeUriComponent(value, charset, UriResolvePlaceholders.Type.URI);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        };
        return expandUriComponent(source, uriTemplateVariables, variableEncoder);
    }

    @Nullable
    private static String expandUriComponent(@Nullable String source, UriTemplateVariables uriVariables, @Nullable UnaryOperator<String> encoder) {
        if (source == null) {
            return null;
        } else if (source.indexOf(123) == -1) {
            return source;
        } else {
            if (source.indexOf(58) != -1) {
                source = sanitizeSource(source);
            }

            Matcher matcher = NAMES_PATTERN.matcher(source);
            StringBuffer sb = new StringBuffer();

            while(matcher.find()) {
                String match = matcher.group(1);
                String varName = getVariableName(match);
                Object varValue = uriVariables.getValue(varName);
                if (!UriResolvePlaceholders.UriTemplateVariables.SKIP_VALUE.equals(varValue)) {
                    String formatted = getVariableValueAsString(varValue);
                    formatted = encoder != null ? (String)encoder.apply(formatted) : Matcher.quoteReplacement(formatted);
                    matcher.appendReplacement(sb, formatted);
                }
            }

            matcher.appendTail(sb);
            return sb.toString();
        }
    }

    private static String sanitizeSource(String source) {
        int level = 0;
        int lastCharIndex = 0;
        char[] chars = new char[source.length()];

        for(int i = 0; i < source.length(); ++i) {
            char c = source.charAt(i);
            if (c == '{') {
                ++level;
            }

            if (c == '}') {
                --level;
            }

            if (level <= 1 && (level != 1 || c != '}')) {
                chars[lastCharIndex++] = c;
            }
        }

        return new String(chars, 0, lastCharIndex);
    }

    private static String getVariableName(String match) {
        int colonIdx = match.indexOf(58);
        return colonIdx != -1 ? match.substring(0, colonIdx) : match;
    }

    private static String getVariableValueAsString(@Nullable Object variableValue) {
        return variableValue != null ? variableValue.toString() : "";
    }

    private static void notNull(@Nullable Object object, String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String encodeUriComponent(String source, Charset charset, Type type) throws UnsupportedEncodingException {
        if (source != null && !source.isEmpty()) {
            notNull(charset, "Charset must not be null");
            notNull(type, "Type must not be null");
            byte[] bytes = source.getBytes(charset);
            boolean original = true;

            for(int g : bytes) {
                if (!type.isAllowed(g)) {
                    original = false;
                    break;
                }
            }

            if (original) {
                return source;
            } else {
                ByteArrayOutputStream baos = new ByteArrayOutputStream(bytes.length);
                byte[] var13 = bytes;
                int var14 = bytes.length;

                for(int g = 0; g < var14; ++g) {
                    byte var10000 = var13[g];
                    if (type.isAllowed(g)) {
                        baos.write(g);
                    } else {
                        baos.write(37);
                        char hex1 = Character.toUpperCase(Character.forDigit(g >> 4 & 15, 16));
                        char hex2 = Character.toUpperCase(Character.forDigit(g & 15, 16));
                        baos.write(hex1);
                        baos.write(hex2);
                    }
                }

                return baos.toString(charset.name());
            }
        } else {
            return source;
        }
    }

    static class VarArgsTemplateVariables implements UriTemplateVariables {
        private final Iterator<Object> valueIterator;

        public VarArgsTemplateVariables(Object... uriVariableValues) {
            this.valueIterator = Arrays.asList(uriVariableValues).iterator();
        }

        @Nullable
        public Object getValue(@Nullable String name) {
            if (!this.valueIterator.hasNext()) {
                throw new IllegalArgumentException("Not enough variable values available to expand '" + name + "'");
            } else {
                return this.valueIterator.next();
            }
        }
    }

    interface UriTemplateVariables {
        Object SKIP_VALUE = UriTemplateVariables.class;

        @Nullable
        Object getValue(@Nullable String var1);
    }

    static enum Type {
        SCHEME {
            public boolean isAllowed(int c) {
                return this.isAlpha(c) || this.isDigit(c) || 43 == c || 45 == c || 46 == c;
            }
        },
        AUTHORITY {
            public boolean isAllowed(int c) {
                return this.isUnreserved(c) || this.isSubDelimiter(c) || 58 == c || 64 == c;
            }
        },
        USER_INFO {
            public boolean isAllowed(int c) {
                return this.isUnreserved(c) || this.isSubDelimiter(c) || 58 == c;
            }
        },
        HOST_IPV4 {
            public boolean isAllowed(int c) {
                return this.isUnreserved(c) || this.isSubDelimiter(c);
            }
        },
        HOST_IPV6 {
            public boolean isAllowed(int c) {
                return this.isUnreserved(c) || this.isSubDelimiter(c) || 91 == c || 93 == c || 58 == c;
            }
        },
        PORT {
            public boolean isAllowed(int c) {
                return this.isDigit(c);
            }
        },
        PATH {
            public boolean isAllowed(int c) {
                return this.isPchar(c) || 47 == c;
            }
        },
        PATH_SEGMENT {
            public boolean isAllowed(int c) {
                return this.isPchar(c);
            }
        },
        QUERY {
            public boolean isAllowed(int c) {
                return this.isPchar(c) || 47 == c || 63 == c;
            }
        },
        QUERY_PARAM {
            public boolean isAllowed(int c) {
                if (61 != c && 38 != c) {
                    return this.isPchar(c) || 47 == c || 63 == c;
                } else {
                    return false;
                }
            }
        },
        FRAGMENT {
            public boolean isAllowed(int c) {
                return this.isPchar(c) || 47 == c || 63 == c;
            }
        },
        URI {
            public boolean isAllowed(int c) {
                return this.isUnreserved(c);
            }
        };

        private Type() {
        }

        public abstract boolean isAllowed(int var1);

        protected boolean isAlpha(int c) {
            return c >= 97 && c <= 122 || c >= 65 && c <= 90;
        }

        protected boolean isDigit(int c) {
            return c >= 48 && c <= 57;
        }

        protected boolean isGenericDelimiter(int c) {
            return 58 == c || 47 == c || 63 == c || 35 == c || 91 == c || 93 == c || 64 == c;
        }

        protected boolean isSubDelimiter(int c) {
            return 33 == c || 36 == c || 38 == c || 39 == c || 40 == c || 41 == c || 42 == c || 43 == c || 44 == c || 59 == c || 61 == c;
        }

        protected boolean isReserved(int c) {
            return this.isGenericDelimiter(c) || this.isSubDelimiter(c);
        }

        protected boolean isUnreserved(int c) {
            return this.isAlpha(c) || this.isDigit(c) || 45 == c || 46 == c || 95 == c || 126 == c;
        }

        protected boolean isPchar(int c) {
            return this.isUnreserved(c) || this.isSubDelimiter(c) || 58 == c || 64 == c;
        }
    }
}