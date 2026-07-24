package com.syncari.connector.data.iterator;

import com.syncari.connector.MarketoEntityPage;
import com.syncari.connector.data.WatermarkInfo;
import org.jooq.lambda.function.Function2;

public class MarketoHistoricalDataIterator extends MarketoDataIterator {

    public MarketoHistoricalDataIterator(WatermarkInfo baseWatermark,
                               Function2<WatermarkInfo, String, MarketoEntityPage> generator) {
        super(baseWatermark, generator);
    }

    @Override
    public long getLastOffset() {
        return offset;
    }

    @Override
    public Offset getOffsetInfo() {
        return new Offset(Offset.OffsetType.TIMESTAMP, 0);
    }
}
