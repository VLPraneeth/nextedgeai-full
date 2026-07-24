package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import com.syncari.core.MigrationContext;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;

import javax.print.Doc;
import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;

@Slf4j
public class SYN_8576_UpdateTrialPlanQuota {

    @ChangeSet(order = "001", id = "updateTrialPlanQuota", author = "rohit")
    public void updateTrialPlanQuota(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        MongoCollection<Document> planCollection = template.getCollection("plan");
        FindIterable<Document> plans =   planCollection.find(eq("name","trial"));
        if (null != plans){
            log.info("Updating plan with name trial");
            List<Document> listOfPlans = plans.into(new ArrayList<>());
            for (Document document : listOfPlans) {
                if (null != (List<Document>)document.get("quota")){
                    List<Document> quotas = (List<Document>)document.get("quota");
                    quotas.forEach(qu -> {
                        // Update right quota with right value
                        String type = (String)qu.get("type");
                        if (type.equals("RECORDS_LIMIT")){
                            qu.remove("value");
                            qu.append("value",1000000);
                        }
                        if (type.equals("TRIAL_DAYS_LIMIT")){
                            qu.remove("value");
                            qu.append("value",31);
                        }
                    });
                    if (!dryRunMode){
                        Bson updatedVal = Updates.set("quota", quotas);
                        UpdateResult updatedResult = planCollection.updateOne(eq("_id", document.get("_id")), updatedVal);
                        log.info("Updated result is {}", updatedResult);
                    }else{
                        log.info("Quotas to be updated to {}", quotas);
                    }
                }else{
                    log.error("Plan with name trial does not have quota");
                }
            }
        }else{
            log.error("Plan with name trial not found");
        }

    }
}
