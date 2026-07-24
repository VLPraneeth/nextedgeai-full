package com.syncari.utils.file;

import org.springframework.stereotype.Component;

@Component
public class FileUtil {
	public static final String INVALID_CHARS = "[!^@#$%&*()+=|<>?{}\\[\\]~\\s]+";

	public String sanitizeFileName(String name) {
		return name.replaceAll(INVALID_CHARS, "_");
	}

}
