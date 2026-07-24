package com.syncari.connector.data;

import com.syncari.utils.ExceptionUtils;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

@Data
@Accessors(chain = true)
public class BatchData {
    private InputStream dataStream;
    private String externalId;
    private String id;
    private String url;


}
