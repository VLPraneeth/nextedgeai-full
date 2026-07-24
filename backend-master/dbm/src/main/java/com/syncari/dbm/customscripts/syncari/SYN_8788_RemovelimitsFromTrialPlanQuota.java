package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;

@Slf4j
public class SYN_8788_RemovelimitsFromTrialPlanQuota {

    @ChangeSet(order = "001", id = "removeLimitsTrialPlanQuota", author = "rohit")
    public void removeLimitsTrialPlanQuota(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        MongoCollection<Document> planCollection = template.getCollection("plan");
        FindIterable<Document> plans =   planCollection.find(eq("name","trial"));
        if (null != plans){
            log.info("Updating plan with name trial");
            List<Document> listOfPlans = plans.into(new ArrayList<>());
            for (Document document : listOfPlans) {
                if (null != (List<Document>)document.get("quota")){
                    List<Document> quotas = (List<Document>)document.get("quota");
                    List<Document> documentList = new ArrayList<>();
                    for (Document q : quotas){
                        String type = (String)q.get("type");
                        if (type.equals("RECORDS_LIMIT") || type.equals("PIPELINE_PUBLISH_LIMIT") || type.equals("REF_DATA_UPLOAD_LIMIT")){{
                            documentList.add(q);
                        }}
                    }
                    documentList.forEach(d -> {
                        if (!dryRunMode){
                            UpdateResult updatedResult = planCollection.updateOne(eq("_id", document.get("_id")), Updates.pull("quota",d));
                            log.info("Updated result is {}", updatedResult);
                        }else{
                            log.info("Doc to be removed is {}", d);
                        }
                    });
                }else{
                    log.error("Plan with name trial does not have quota");
                }
            }
        }else{
            log.error("Plan with name trial not found");
        }
    }
}
