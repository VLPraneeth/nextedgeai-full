package com.syncari.core.utils;

import com.github.javafaker.Faker;
import com.syncari.connector.EntityData;
import com.syncari.core.datatype.*;
import com.syncari.core.model.EntityDefinition;
import org.bson.types.ObjectId;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class RecordHelper {

    public static EntityData createRecord(EntityDefinition entityDefinition){
        EntityData entityData = new EntityData(entityDefinition.getApiName())
                .setId(UUID.randomUUID().toString())
                .setSyncariEntityId(ObjectId.get().toHexString())
                .setConnectorId(entityDefinition.getConnectorId())
                .setCreatedAt(System.currentTimeMillis())
                .setLastModified(System.currentTimeMillis());
        Faker faker = new Faker();
        entityDefinition.getActiveAttributes().forEach(a->{
            if(a.getDataType().getName().equals(StringType.NAME)){
                if(a.getApiName().toLowerCase().contains("city")) {
                    entityData.addValue(a.getApiName(), faker.address().city());
                }else if(a.getApiName().toLowerCase().contains("name")){
                    entityData.addValue(a.getApiName(), faker.name().name());
                }else if(a.getApiName().toLowerCase().contains("country")){
                    entityData.addValue(a.getApiName(), faker.country().name());
                }else{
                    entityData.addValue(a.getApiName(),faker.lorem());
                }
            }else if(a.getDataType().getName().equals(IntegerType.NAME)){
                entityData.addValue(a.getApiName(), faker.number().randomNumber());
            }else if(a.getDataType().getName().equals(DoubleType.NAME)){
                entityData.addValue(a.getApiName(), faker.number().randomDouble(3,100,10000));
            }else if(a.getDataType().getName().equals(DateType.NAME)){
                entityData.addValue(a.getApiName(), DateType.VALUE.convert(faker.date().past(50, TimeUnit.DAYS)));
            }else if(a.getDataType().getName().equals(DatetimeType.NAME)){
                entityData.addValue(a.getApiName(), DatetimeType.VALUE.convert(ZonedDateTime.ofInstant(faker.date().past(100, TimeUnit.DAYS).toInstant(), ZoneOffset.UTC)));
            }else if(a.getDataType().getName().equals(BooleanType.NAME)){
                entityData.addValue(a.getApiName(), Math.random() > 0.5);
            }else{
                entityData.addValue(a.getApiName(), faker.lorem());
            }
        });
        return entityData;
    }
}
