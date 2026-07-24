package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;

@Slf4j
public class SYN_9023_RemoveInstanceLimits {

    @ChangeSet(order = "001", id = "removeInstanceLimits", author = "rohit")
    public void removeInstanceLimits(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        String instanceId = System.getProperty("instanceId");
        String limitProperty = System.getProperty("limitProperty");
        assert (null != instanceId);
        assert (null != limitProperty);
        log.info("syncariId {}, property to be removed {}", instanceId, limitProperty);
        MongoCollection<Document> orgCollection = template.getCollection("organization");
        FindIterable<Document> orgsToIterate =   orgCollection.find(eq("instances.0.syncariId",instanceId));
        if (null != orgsToIterate){
            log.info("Updating org with instance of syncariId {}", instanceId);
            List<Document> listOfOrgs = orgsToIterate.into(new ArrayList<>());
            assert CollectionUtils.isNotEmpty(listOfOrgs);
            assert listOfOrgs.size() == 1;
            for (Document document : listOfOrgs) {
                if (null != (List<Document>)document.get("instances")){
                    List<Document> instances = (List<Document>)document.get("instances");
                    assert CollectionUtils.isNotEmpty(instances);
                    assert instances.size() == 1;
                    List<Document> documentList = new ArrayList<>();
                    for (Document q : instances){
                        List<Document> quotas = (List<Document>)q.get("quota");
                        for (Document quo : quotas) {
                            String type = (String) quo.get("type");
                            if (type.equals(limitProperty)){
                                documentList.add(quo);
                            }
                        }
                    }
                    documentList.forEach(d -> {
                        if (!dryRunMode){
                            UpdateResult updatedResult = orgCollection.updateOne(eq("_id", document.get("_id")), Updates.pull("instances.0.quota",d));
                            log.info("Updated result is {}", updatedResult);
                        }else{
                            log.info("Doc to be removed is {}", d);
                        }
                    });
                }else{
                    log.error("Org with syncariId {} instance does not exists", instanceId);
                }
            }
        }else{
            log.error("Org with syncariId {} instance does not exists", instanceId);
        }
    }
}
