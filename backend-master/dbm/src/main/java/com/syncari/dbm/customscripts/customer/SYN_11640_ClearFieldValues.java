package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.UpdateResult;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.ActionDefinition;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

@Slf4j
public class SYN_11640_ClearFieldValues {

    @ChangeSet(order = "001", id = "clearFieldValues", author = "varsha", runAlways = true)
    public void clearFieldValues(MongoTemplate template) {
        String entityApiName = System.getProperty("entityApiName");
        String fieldApiNames = System.getProperty("fieldApiNames");
        if(StringUtils.isBlank(entityApiName) || StringUtils.isBlank(fieldApiNames)) {
            log.warn("entityApiName/fieldApiNames is empty");
            return;
        }
        String[] fieldApiNameList = fieldApiNames.split(";");
        if(fieldApiNameList.length == 0) {
            log.warn("entityApiName/fieldApiNames is empty");
            return;
        }
        MongoCollection<Document> collection = template.getCollection("syncari_"+entityApiName.toLowerCase());

        for (String field : fieldApiNameList) {
            if(StringUtils.isBlank(field)) {
                log.warn("blank field name");
                continue;
            }
            UpdateResult updateResult = collection.updateMany(new Document(), new Document("$set", new Document(field, null)));
            log.info("updated {} for {}", updateResult.getModifiedCount(), field);
        }
    }
}
