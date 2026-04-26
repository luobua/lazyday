package com.fan.lazyday.infrastructure.utils.id;

import com.fan.lazyday.infrastructure.context.SpringContext;
import org.springframework.data.util.Lazy;
import org.springframework.util.StringUtils;

/**
 * @author chenbin
 */
public class IdSequence {
    private static final Lazy<SnowflakeIdWorker> ID_WORKER = Lazy.of(SnowflakeIdWorker::new);

    private IdSequence() {
        throw new IllegalStateException("Utility class");
    }

    public static String nextId() {
        long v = ID_WORKER.get().nextId();
        return Long.toString(v, 35).toUpperCase().replaceAll("I", "Z");
    }

    public static long inverse(String id) {
        if (StringUtils.hasText(id)) {
            String s = id.replaceAll("Z", "I");
            return Long.parseLong(s, 35);
        } else {
            return 0;
        }
    }
}
