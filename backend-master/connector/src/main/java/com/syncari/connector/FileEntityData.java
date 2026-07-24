package com.syncari.connector;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.Wither;

@ToString
@Data
@Accessors(chain = true)
@AllArgsConstructor
@Wither
public class FileEntityData {
    private final EntityData entityData;
    private static final String FILE_TYPE_ATTRIB = "fileType";
    private static final String FILE_NAME_ATTRIB = "name";

    public MediaType getFileMediaType() {
        String fileExtension = getFileExtension();
        switch (fileExtension) {
            case "pdf":
                return MediaType.APPLICATION_PDF;
            case "csv":
            case "excel":
            case "xsl":
            default:
                return MediaType.TEXT_PLAIN;
        }
    }

    public String getFileExtension() {
        String fileName = getFileName();
        if (!StringUtils.isEmpty(fileName) && fileName.contains(".")) {
            String[] fileNameParts = fileName.split("\\.");
            return fileNameParts[1];
        }
        String fileExtension = entityData.getValueAsString(FILE_TYPE_ATTRIB);
        if (StringUtils.isEmpty(fileExtension)) return "txt";
        if (fileExtension.startsWith("_")) fileExtension = fileExtension.substring(1);
        return fileExtension.trim().toLowerCase();
    }

    public String getFileName() {
        return StringUtils.isEmpty(entityData.getValueAsString(FILE_NAME_ATTRIB)) ? 
            entityData.getId() : entityData.getValueAsString(FILE_NAME_ATTRIB);
    }
    

    public String getFullFileName() {
        String fileName = getFileName();
        if (fileName.contains(".")) {
            return fileName;
        }
        // by default send text as extension.
        return fileName + ".txt";
    }

}