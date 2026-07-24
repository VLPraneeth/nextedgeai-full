package com.syncari.connector;

import static com.syncari.utils.ExceptionUtils.rethrow;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;

import com.syncari.utils.Storage;

public class TestFileStorage implements Storage {
    Map<String, String> payloads = new HashMap<>();

    @Override
    public String write(InputStream inputStream, String uri) {
        rethrow(() -> payloads.put(uri, new String(inputStream.readAllBytes(), "utf-8")));
        return uri;
    }

    @Override
    public InputStream read(String uri) {
        return payloads.get(uri) == null ? null : new ByteArrayInputStream(payloads.get(uri).getBytes());
    }

    @Override
    public void delete(String id) {
        payloads.remove(id);
    }

    @Override
    public long lastModified(String uri) {
        return Instant.EPOCH.toEpochMilli();
    }

    @Override
    public String writeToFolder(InputStream fileStream, String fileName, String folderName, String bucketName) {
        throw new RuntimeException("Not yet implemented");
    }

    @Override
    public void delete(String fileName, String bucketName) {
        throw new RuntimeException("Not yet implemented");
    }
}
