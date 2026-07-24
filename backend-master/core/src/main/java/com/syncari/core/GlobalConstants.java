package com.syncari.core;

import java.util.Map;

import org.springframework.http.MediaType;

public class GlobalConstants {
    public static final String SYNCARI_LOGO = "%s/assets/icons/nextedge-mark.svg";
    public static final String USER_LOGO = "%s/user-add_2X.png";
    public static final String PASSWORD_LOGO = "%s/password_2X.png";
    public static final String THUMBS_UP_LOGO = "%s/thumbs-up.png";
    public static final String GRAPH_ICON_LOGO = "%s/graph.png";
    public static final String BUSINESS_LOGO = "business-logo.png";
    public static final String LOGIN_URL = "%s/login";
    public static final String SET_PWD_URL = "%s/invited-user/setpassword/";
    public static final Map<String, MediaType> PHOTO_MEDIA_TYPE_MAP = Map.of("png", MediaType.IMAGE_PNG, "jpg",
            MediaType.IMAGE_JPEG, "jpeg", MediaType.IMAGE_JPEG, "gif", MediaType.IMAGE_GIF);
}
