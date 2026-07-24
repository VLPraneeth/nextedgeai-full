package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class RemoveOldSeededDatacardDataset {

    @ChangeSet(order = "001", id = "removeOldDatacards", author = "rohit", runAlways = true)
    public void removeOldDatacards(MongoTemplate template){
        removeDatacard("quarterlyClosedPipelineRevenue", template);
        removeDatacard("annualRecurringRevenue", template);
        removeDatacard("quarterlyClosedPipelineRevenueByType", template);
        removeDatacard("avgRevenueForAllAccounts", template);
        removeDatacard("existingCustomerCount", template);
        removeDatacard("revenueChurnByQuarter", template);
        removeDatacard("nextFewQuaterOpenPipelines", template);
        removeDatacard("top10CustomersByRevenue", template);
        removeDatacard("openTicketsAccountforOpenPipeline", template);
        removeDatacard("salesFunnel", template);
        removeDatacard("allOpenPipelineByType", template);
        removeDatacard("openRenewals", template);
        removeDatacard("openRenewalLogoCount", template);
        removeDatacard("openTicketsCountByAccount", template);
        removeDatacard("leadCountBySource", template);
        removeDatacard("mqlCountInQuarter", template);
        removeDatacard("sqlLeadCountByOwner", template);
        removeDatacard("userGrowth", template);
        removeDatacard("openEscalatedTicketCount", template);
        removeDatacard("openTicketsByPriority", template);
        removeDatacard("trendOfIssuesResolvedIn24hours", template);
        removeDatacard("trendOfIssuesResolvedIn7Days", template);
        removeDatacard("upcomingRenewalDates", template);
        removeDatacard("openTicketsCountByAccount", template);
        removeDatacard("allOpenPipelineCount", template);
    }

    @ChangeSet(order = "002", id = "removeOldDatasets", author = "rohit", runAlways = true)
    public void removeOldDatasets(MongoTemplate template){
        removeDataset("allOpenPipeline", template);
        removeDataset("allClosedPipeline", template);
        removeDataset("allClosedLostPipeline", template);
        removeDataset("leadsBySource", template);
        removeDataset("leadWithOwner", template);
        removeDataset("supportTickets", template);
        removeDataset("ticketAccountsForOpenPipeline", template);
        removeDataset("userDataset", template);
    }

    private void removeDatacard(String datacardName, MongoTemplate template){
        MongoCollection<Document> datacardCollection = template.getCollection("datacard");
        Document datacard = datacardCollection.find(new Document("name", datacardName)).first();
        log.info("datacard is {}", datacard);
        if (null != datacard){
            log.info("Removing datacard {}", datacard.getObjectId("_id"));
            datacardCollection.deleteOne(datacard);
        }
    }

    private void removeDataset(String datasetName, MongoTemplate template){
        MongoCollection<Document> datasetCollection = template.getCollection("dataset");
        Document dataset = datasetCollection.find(new Document("name", datasetName)).first();
        log.info("dataset is {}", dataset);
        if (null != dataset){
            log.info("Removing dataset {}", dataset.getObjectId("_id"));
            datasetCollection.deleteOne(dataset);
        }
    }
}
