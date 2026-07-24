package com.syncari.core.service;

import java.util.List;

public interface SharingService {

    public void share(String sourceId, List<String> instances);

    public void unshare(String sourceId, List<String> instances);
}
