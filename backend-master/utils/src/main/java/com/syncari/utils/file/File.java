package com.syncari.utils.file;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class File {
    private long lastModified;
    private String path;
    private String name;
    private long size;
    private FileType type;

    public boolean isDirectory() {
        return type == FileType.DIRECTORY;
    }

    public File setDirectory(boolean isDirectory) {
        if (isDirectory) {
            this.type = FileType.DIRECTORY;
        } else {
            this.type = FileType.REGULAR;
        }
        return this;
    }

    public boolean isFile() {
        return type == FileType.REGULAR;
    }
}
