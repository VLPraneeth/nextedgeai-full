package com.syncari.connector.custom;

import java.util.List;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.data.iterator.Offset.OffsetType;

import lombok.Data;

@Data
public class ReadResponse {
    Watermark watermark;
    List<EntityData> data;
    OffsetType offsetType;

    WatermarkInfo watermarkInfo;

    public ReadResponse() {
    }

    public ReadResponse(Watermark watermark, List<EntityData> data, OffsetType offsetType) {
        this.watermark = watermark;
        this.watermarkInfo = getWatermarkInfo();
        this.data = data;
        this.offsetType = offsetType;
    }

    // Conversion method from custom watermark to framework watermarkinfo.
    public WatermarkInfo getWatermarkInfo() {
        if (watermarkInfo == null) {
            watermarkInfo = new WatermarkInfo(watermark.getStart(), watermark.getEnd(), watermark.isInitial(), watermark.getOffset());
            watermarkInfo.setChangeStream(watermark.getCursor());
            watermarkInfo.setLimit(watermark.getLimit());
            watermarkInfo.setResync(watermark.isResync());
            watermarkInfo.setTest(watermark.isTest());
        }
        return watermarkInfo;
    }

    public static Watermark fromWatermarkInfo(WatermarkInfo wmInfo) {
        return new Watermark().setStart(wmInfo.getStart()).setEnd(wmInfo.getEnd()).setOffset(wmInfo.getOffset())
                .setCursor(wmInfo.getChangeStream()).setInitial(wmInfo.isInitial()).setLimit(wmInfo.getLimit()).setResync(wmInfo.isResync()).setTest(wmInfo.isTest());
    }
}
