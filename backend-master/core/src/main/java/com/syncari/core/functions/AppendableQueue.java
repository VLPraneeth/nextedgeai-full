package com.syncari.core.functions;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;

public class AppendableQueue implements Appendable {
    private final Queue<Integer> queue = new ArrayDeque<>();

    @Override
    public Appendable append(CharSequence csq) throws IOException {
        for (byte b : csq.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            queue.offer((int) b);
        }
        return this;
    }

    @Override
    public Appendable append(CharSequence csq, int start, int end) throws IOException {
        for (byte b : csq.subSequence(start, end).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            queue.offer((int) b);
        }
        return this;
    }

    @Override
    public Appendable append(char c) throws IOException {
        for (byte b : String.valueOf(c).getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            queue.offer((int) b);
        }
        return this;
    }

    public boolean hasMore() {
        return !queue.isEmpty();
    }

    public int next() {
        return queue.remove();
    }
}