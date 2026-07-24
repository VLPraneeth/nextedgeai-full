package com.syncari.restutils.utils;

import static com.syncari.utils.I18n.i18n;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.syncari.core.exceptions.SyncariValidationException;

@Component
public class ImageUtil {
	private static final List<String> allowedExt = List.of("png", "jpeg", "gif", "jpg");
	private static final List<String> allowedContentType = List.of("image/jpeg", "image/png", "image/gif");

	public void validateFile(MultipartFile file) {
		if (file == null)
			return;
		String[] parts = file.getOriginalFilename().split("[.]");
		if (parts.length <= 1 || !allowedExt.contains(parts[parts.length - 1].toLowerCase())) {
			throw new SyncariValidationException(i18n("unsupported_file_ext"));
		}
		if (!allowedContentType.contains(file.getContentType())) {
			throw new SyncariValidationException(
					String.format(i18n("unsupported_content_type"), file.getContentType()));
		}
	}

}
