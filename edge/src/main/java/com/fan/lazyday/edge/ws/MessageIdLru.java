package com.fan.lazyday.edge.ws;

import java.util.LinkedHashMap;
import java.util.Map;

public class MessageIdLru {

    private final Map<String, Boolean> seen;

    public MessageIdLru(int capacity) {
        this.seen = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > capacity;
            }
        };
    }

    public synchronized boolean addIfAbsent(String msgId) {
        if (seen.containsKey(msgId)) {
            return false;
        }
        seen.put(msgId, Boolean.TRUE);
        return true;
    }

    public synchronized int size() {
        return seen.size();
    }
}
