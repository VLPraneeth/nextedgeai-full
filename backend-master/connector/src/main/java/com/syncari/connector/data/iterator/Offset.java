package com.syncari.connector.data.iterator;

import lombok.Getter;

@Getter
public class Offset {
    public enum OffsetType {
        NONE,
        PAGE_NUMBER,
        RECORD_COUNT,
        CUSTOM,
        TIMESTAMP
    }

    public final int pageSize;
    public final OffsetType type;

    public Offset(OffsetType type, int pageSize) {
        this.type = type;
        this.pageSize = pageSize;
    }

    public static long recomputeOffset(Offset offsetInfo, long offset, int prunedSize) {
        // any offset <= 1, we do not mind moving.
        if (offset <= 1) return offset;
        if (offsetInfo.getType() == Offset.OffsetType.PAGE_NUMBER) {
            int pageSize = offsetInfo.getPageSize() > 0 ? offsetInfo.getPageSize() : 1;
            int pagesToRewind = (prunedSize / pageSize) + 1;
            if (pagesToRewind <= 0) {
                offset = 1;
            } else if (offset > 0) {
                offset = (offset - pagesToRewind) > 0 ? offset - pagesToRewind : 1;
            }
        } else if (offsetInfo.getType() == Offset.OffsetType.RECORD_COUNT) {
            offset = (offset - prunedSize) > 1 ? offset - prunedSize - 1: 1;
        }
        return offset;
    }

    public String toString() {
        return String.format("OffsetType: %s; pageSize: %d", type, pageSize);
    }
}
