package com.syncari.core.utils;

import com.syncari.core.GlobalConstants;
import org.springframework.http.MediaType;

public class ImageUtils {

    public static MediaType getMediaType(String photoLocation) {
        var extensionParts = photoLocation.split("\\.");
        var extension = extensionParts.length > 0 ? extensionParts[extensionParts.length - 1] : "png";
        return GlobalConstants.PHOTO_MEDIA_TYPE_MAP.getOrDefault(extension.toLowerCase(), MediaType.IMAGE_PNG);
    }
}
