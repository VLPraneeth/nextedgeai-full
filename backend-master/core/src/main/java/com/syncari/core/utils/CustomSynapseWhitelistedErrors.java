package com.syncari.core.utils;

import java.util.Set;

public class CustomSynapseWhitelistedErrors {
    public static final Set<String> WHITELISTED_SECURITY_ERRORS = Set.of(
            "Paramiko call with policy set to automatically trust the unknown host key.",
            "Possible SQL injection vector through string-based query construction."
    );
}