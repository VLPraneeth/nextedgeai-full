package com.syncari.core.model;

import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.lang.StringUtils;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Data
@Accessors(chain = true)
public class Documentation implements Serializable {
    private String content;
    private Format format = Format.MARKDOWN;
    private static final String DEFAULT_DOC = "# Pipeline Overview\n" +
            "\n" +
            "This pipeline  has no documentation.\n\n " +
            "The editor supports  **[Markdown](https://www.markdownguide.org/cheat-sheet/)** a" +
            "nd you can generate documentation using **SyncAI** using the `Generate Docs` button";

    public String toBase64() {
        final Base64.Encoder encoder = Base64.getEncoder();
        if (StringUtils.isBlank(content)) {
            return encoder.encodeToString(DEFAULT_DOC.getBytes(StandardCharsets.UTF_8));
        }
        return encoder.encodeToString(content.getBytes(StandardCharsets.UTF_8));
    }
}
