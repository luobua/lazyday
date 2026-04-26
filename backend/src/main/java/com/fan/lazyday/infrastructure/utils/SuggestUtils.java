package com.fan.lazyday.infrastructure.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author chenbin
 */
@Slf4j
public class SuggestUtils {
    private static final ConcurrentMap<String, Pattern> SUGGEST_PATTERNS = new ConcurrentHashMap<>();

    private SuggestUtils() {
        throw new IllegalStateException("Utility class");
    }

    @Deprecated
    public static String suggest(String s, Set<String> test, Supplier<Long> seq) {
        if (!test.contains(s)) {
            return s;
        }

        String regex = "^(.+?)(\\d*)$";

        Pattern pattern = SUGGEST_PATTERNS.computeIfAbsent(regex, Pattern::compile);

        Matcher matcher = pattern.matcher(s);

        String prefix = s;

        if (matcher.matches()) {
            int count = matcher.groupCount();
            if (count >= 1) {
                prefix = matcher.group(1);
            }
        }

        String v;
        do {
            v = prefix + seq.get();
        } while (test.contains(v));

        return v;
    }

    public static String suggest2(String s, Set<String> test, Supplier<Long> seq) {
        if (!test.contains(s)) {
            return s;
        }

        String regex = "^(.+?)(\\((\\d+)\\))?$";
        Pattern pattern = SUGGEST_PATTERNS.computeIfAbsent(regex, Pattern::compile);

        Matcher matcher = pattern.matcher(s);

        String prefix = s;

        if (matcher.matches()) {
            int count = matcher.groupCount();
            if (count >= 1) {
                prefix = matcher.group(1);
            }
        }

        String v;
        do {
            v = prefix + "(" + seq.get() + ")";
        } while (test.contains(v));

        return v;
    }

    public static String suggestPrefix(String s) {
        String regex = "^(.+?)(\\((\\d+)\\))?$";
        Pattern pattern = SUGGEST_PATTERNS.computeIfAbsent(regex, Pattern::compile);
        Matcher matcher = pattern.matcher(s);
        if (matcher.matches()) {
            int count = matcher.groupCount();
            if (count >= 1) {
                return matcher.group(1);
            }
        }
        return s;
    }

    public static String suggest3(String s, Set<String> test) {
        if (!test.contains(s)) {
            return s;
        }

        String regex = "^(.+?)(\\((\\d+)\\))?$";
        Pattern pattern = SUGGEST_PATTERNS.computeIfAbsent(regex, Pattern::compile);

        Matcher matcher = pattern.matcher(s);

        String prefix = s;
        int seq = 1;

        if (matcher.matches()) {
            int count = matcher.groupCount();
            if (count >= 1) {
                prefix = matcher.group(1);
            }
            if (count >= 3) {
                String g3 = matcher.group(3);
                if (g3 != null) {
                    try {
                        seq = Integer.parseInt(matcher.group(3));
                    } catch (NumberFormatException ignore) {
                    }
                }
            }
        }

        seq += 1;

        String v;
        do {
            v = prefix + "(" + seq++ + ")";
        } while (test.contains(v));

        return v;
    }
}
