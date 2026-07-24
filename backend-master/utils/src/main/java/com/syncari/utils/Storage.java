package com.syncari.utils;

import java.io.InputStream;

public interface Storage {

    String write(InputStream fileStream, final String uri);

    String writeToFolder(InputStream fileStream, final String fileName, final String folderName, String bucketName);

    InputStream read(final String uri);

    void delete(final String uri);

    void delete(final String fileName, final String bucketName);

    long lastModified(final String uri);
}
