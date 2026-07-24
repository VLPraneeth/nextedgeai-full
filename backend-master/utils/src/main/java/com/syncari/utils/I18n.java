package com.syncari.utils;

import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URL;
import java.util.*;

public class I18n {
    private static final Locale enLocale = new Locale("en", "US");

    private static final ResourceBundle.Control control = new MultifileControl();

    public static String i18n(String key,Locale locale) {
        //TODO when we have support for user local, lookup the key from the corresponding locale
        try {
            return ResourceBundle.getBundle("i18n/messages", locale, control).getString(key);
        } catch (Exception e) {
            return key;
        }
    }

    public static String i18n(String key, Object... params) {
        return params == null || params.length == 0 ? i18n(key, enLocale) : String.format(i18n(key, enLocale), params);
    }

    public static String i18nWithDefault(String key, String defaultValue, Object... params) {
        final String resolved = i18n(key, params);
        return StringUtils.isBlank(resolved) || resolved.equals(key) ? defaultValue : resolved;
    }

}

class MultifileControl extends ResourceBundle.Control {
    public ResourceBundle newBundle(String baseName, Locale locale, String format,
                                    ClassLoader loader, boolean reload) {

        PropertiesBundle bundle = new PropertiesBundle();
        try {
            String bundleName = toBundleName(baseName, locale);
            Enumeration<URL> resources = loader.getResources(bundleName+".properties");
            while (resources.hasMoreElements()) {
                Properties properties = new Properties();
                properties.load(resources.nextElement().openStream());
                bundle.addMessages(properties);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bundle;
    }

}

class PropertiesBundle extends ResourceBundle {
    private Map<String, Object> messages = new HashMap<>();

    public void addMessages(Properties props) {
        props.forEach((key, value) -> messages.put(key.toString(), value));
    }

    @Override
    protected Object handleGetObject(String key) {
        return messages.get(key);
    }

    @Override
    public Enumeration<String> getKeys() {
        return IteratorUtils.asEnumeration(messages.keySet().iterator());
    }
}