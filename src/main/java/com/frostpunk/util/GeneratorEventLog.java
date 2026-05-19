package com.frostpunk.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class GeneratorEventLog {

    private static final int MAX_ENTRIES = 50;
    private static final Deque<String> log = new ArrayDeque<>();
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void log(String type, String message) {
        String timestamp = LocalDateTime.now().format(formatter);
        String entry = "[" + timestamp + "] [" + type + "] " + message;

        synchronized (log) {
            log.addFirst(entry);
            if (log.size() > MAX_ENTRIES) {
                log.removeLast();
            }
        }
    }

    public static List<String> getLog() {
        synchronized (log) {
            return new ArrayList<>(log);
        }
    }

    public static void clear() {
        synchronized (log) {
            log.clear();
        }
    }
}
