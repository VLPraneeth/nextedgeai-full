package com.syncari.core.changelogs.syncari;

import java.util.List;

import org.bson.Document;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.ReferenceDataMeta;
import com.syncari.core.model.misc.ReferenceDataSource;
import com.syncari.core.model.misc.ReferenceDataSourceType;
import com.syncari.core.service.ReferenceDataService;

@ChangeLog(order = "0033")
public class M0033_addSyncariReferenceDataSet {

    private final ReferenceDataService service = MigrationContext.getReferenceDataService();
    private final static String gcpLocation = "syncaridb";

    @ChangeSet(order = "001", id = "addSynariReferenceDataSet2", author = "Avinash")
    public void addSyncariReferenceDataSet() {
        populateData("airportCodes.csv","Airport Codes" );
        populateData("countriesWithRegionalCodes.csv","Countries With Regional Codes" );
        populateData("currencyCodes.csv","Currency Codes" );
    }
    
    @ChangeSet(order = "002", id = "addUsStatesReferenceDataSet", author = "varsha")
    public void addUsStatesReferenceDataSet() {
        populateData("usStates.csv","US States/Regions with State/Region Codes" );
    }

    @ChangeSet(order = "003", id = "addFreeEmailReferenceDataSet", author = "rohit")
    public void addFreeEmailReferenceDataSet() {
        populateData("free_email_domains.csv","Free Email Domains" );
    }
    
    @ChangeSet(order = "004", id = "changeStatusReferenceDataSet", author = "varsha")
    public void changeStatusReferenceDataSet(MongoTemplate template) {
    	MongoCollection<Document> collection = template.getCollection("referenceDataMeta");
    	List<String> stdRef = List.of("Airport Codes", "Countries With Regional Codes", "Currency Codes", "US States/Regions with State/Region Codes", "Free Email Domains");
    	stdRef.forEach(r -> {
    		collection.updateMany(new Document("name", r), new Document("$set",new Document("isStandard", true)), new UpdateOptions().upsert(false));
    	});
    }

    private void populateData(String fileName, String datasetName){
        Resource resource = new ClassPathResource("dataset/"+fileName);
        try{
            service.extract(
                    service.createMeta(createDataSet(fileName, datasetName), resource.getInputStream(), resource.getInputStream(), false).getId(), false);
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    private ReferenceDataMeta createDataSet(String fileName, String datasetName){
        com.syncari.core.model.ReferenceDataMeta dataset = new com.syncari.core.model.ReferenceDataMeta();
        dataset.setName(datasetName);
        dataset.setStandard(true);
        ReferenceDataSourceType dataSourceType = ReferenceDataSourceType.valueOf(ReferenceDataSourceType.syncari.toString());
        dataset.setSource(new ReferenceDataSource(dataSourceType, gcpLocation + "/" + fileName));
        return dataset;
    }
}

