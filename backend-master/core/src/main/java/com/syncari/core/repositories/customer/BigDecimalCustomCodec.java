package com.syncari.core.repositories.customer;

import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.types.Decimal128;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class BigDecimalCustomCodec implements Codec<BigDecimal> {
    @Override
    public BigDecimal decode(BsonReader bsonReader, DecoderContext decoderContext) {
        return bsonReader.readDecimal128().bigDecimalValue();
    }

    @Override
    public void encode(BsonWriter bsonWriter, BigDecimal bigDecimal, EncoderContext encoderContext) {
        bigDecimal = bigDecimal.setScale(10, RoundingMode.CEILING);
        bsonWriter.writeDecimal128(new Decimal128(bigDecimal));
    }

    @Override
    public Class<BigDecimal> getEncoderClass() {
        return BigDecimal.class;
    }
}
