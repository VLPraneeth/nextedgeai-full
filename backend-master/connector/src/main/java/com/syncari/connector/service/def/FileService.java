package com.syncari.connector.service.def;

import com.syncari.connector.ConnectorInfo;

import java.io.InputStream;

public interface FileService {

    void writeFile(ConnectorInfo connector, InputStream inputStream, String fileName, String baseFolder);

}