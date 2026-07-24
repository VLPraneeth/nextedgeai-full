package com.syncari.utils.file;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface FileManager {
	String uploadFile(InputStream fileStream, final String fileName) throws IOException;

	InputStream readFile(final String fileName) throws IOException;

	void deleteFile(final String fileName) throws IOException;

	void createDirectory(final String name) throws IOException;

	default List<File> list(String dir, String pattern) {
		return List.of();
	}
}
