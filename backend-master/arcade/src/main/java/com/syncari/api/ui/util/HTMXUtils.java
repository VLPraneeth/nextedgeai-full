package com.syncari.api.ui.util;

import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;

public class HTMXUtils {

    private HTMXUtils() {
        // Utility class
    }

    /**
     * Returns the appropriate template name for HTMX requests.
     * If the request has an HX-Target header, returns a fragment reference.
     * Otherwise, returns the full template name.
     *
     * @param templateName the base template name
     * @param request      the HTTP request
     * @return the template reference (with or without fragment notation)
     */
    public static String htmx(String templateName, HttpServletRequest request) {
        final String header = request.getHeader("HX-Target");
        if (StringUtils.isNotEmpty(header)) {
            return templateName + " :: " + header;
        } else {
            return templateName;
        }
    }
}
