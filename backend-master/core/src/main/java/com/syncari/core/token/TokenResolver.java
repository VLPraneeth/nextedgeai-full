package com.syncari.core.token;

import java.util.Map;

public interface TokenResolver {
    TokenResolution resolveToken(Map<String, Object> context);
}
