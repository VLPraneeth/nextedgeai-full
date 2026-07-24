package com.syncari.core.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.syncari.connector.Status;
import com.syncari.core.repositories.customer.NotificationRepo;
import com.syncari.core.utils.CustomerMongoUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.DeleteFieldRequest;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.SyncResponse;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.service.def.DataService;
import com.syncari.core.DataTransformer;
import com.syncari.core.SyncariContext;
import com.syncari.core.event.store.EventStore;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.repositories.customer.EdgeRepo;
import com.syncari.core.repositories.customer.IdMappingRepo;
import com.syncari.core.repositories.customer.LayoutRepo;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import com.syncari.core.repositories.customer.SinkLogRepo;
import com.syncari.core.repositories.customer.StagedBatchRecordRepo;
import com.syncari.core.repositories.customer.StagedBatchRepo;
import com.syncari.core.repositories.customer.StreamRepo;
import com.syncari.core.repositories.customer.SyncDetailRepo;
import com.syncari.core.schema.EntityDef;
import com.syncari.core.schema.PipelineStatus;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SpecterService {

    @Autowired
    private ConnectorService connectorService;

    @Autowired
    private DataTransformer transformer;

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private DataServiceFactory dataServiceFactory;

    @Autowired
    private CustomerMongoUtils customerMongoUtils;

    @Autowired
    private IdMappingRepo idMappingRepo;

    @Autowired
    private StagedBatchRecordRepo stagedBatchRecordRepo;

    @Autowired
    private SyncDetailRepo syncDetailRepo;

    @Autowired
    private SinkLogRepo sinkLogRepo;

    @Autowired
    private StagedBatchRepo stagedBatchRepo;

    @Autowired
    private StreamRepo streamRepo;

    @Autowired
    private MappingGraphRepo mappingGraphRepo;

    @Autowired
    private EdgeRepo edgeRepo;

    @Autowired
    private LayoutRepo layoutRepo;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private DataTransformer dataTransformer;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private NotificationRepo notificationRepo;

    /**
     * Resets all external connectors, syncari collections, BigQuery store and drops customer entity collections
     * @param syncariId String
     */
    public void resetSubscription(String syncariId, long numOfDays){
        try{
            // Change context
            Organization org = subscriptionService.getOrgBySyncariId(syncariId);
            Instance instance = org.getInstance(syncariId)
                    .orElseThrow(() -> new InstanceNotFound("Instance with syncari id " + syncariId + " not found"));

            SyncariContext.runWithContext(org, instance, SyncariContext.getUser(), () -> {
                connectorService.getAll().forEach(connector -> {
                    resetExternalConnector(connector, numOfDays);
                    // Change the connector status back to authenticated
                    connectorService.deactivate(connector.getId());
                    connectorService.authenticated(connector.getId());
                });
                resetSyncariCollections();
                dropCustomerEntityCollections();
                resetEventStore(syncariId);
                populateSynapses();
            });
            log.info(String.format("Subscription with Syncari Id %s reset successfully!", syncariId));

        }catch(Exception e){
            log.error(e.getMessage(), e);
            throw new RuntimeException(String.format("Unable to reset subscription %s", syncariId));
        }

    }

    protected void populateSynapses() {
        // Salesforce
        Optional<Connector> connector = connectorService.getAll().stream()
                .filter(c -> Constants.SALESFORCE.equalsIgnoreCase(c.getMetadata().getName()))
                .findFirst();

        if(connector.isPresent()){
            Connector sfConnector = connector.get();
            ConnectorInfo connectorInfo = transformer.toConnectorInfo(sfConnector);
            SyncRequest syncRequest = new SyncRequest();
            syncRequest.setConnector(connectorInfo);
            SalesforceDemoData rawSfData = new SalesforceDemoData();

            List<List<String>> contactData =rawSfData.getData();
            List<String> fields = rawSfData.getHeader();
            for(int i = 0; i < contactData.size(); i++){
                List<String> contact = contactData.get(i);
                EntityData e = new EntityData(Constants.CONTACT);
                for(int j = 0; j < contact.size(); j++){
                    SyncResponse accResp;
                    if(fields.get(j).equals("AccountName")){
                        // create new account for every account name encountered
                        SyncRequest syncRequestAcc = new SyncRequest();
                        syncRequestAcc.setConnector(connectorInfo);
                        EntityData acc = new EntityData(Constants.ACCOUNT);
                        acc.addValue("Name", contact.get(j));
                        syncRequestAcc.addData(sfConnector.getId(), acc);
                        accResp = dataServiceFactory.getDataService(sfConnector.getMetadata()).create(syncRequestAcc);
                        e.addValue("AccountId", accResp.getResults().get(0).getId());
                    }else {
                        e.addValue(fields.get(j), contact.get(j));
                    }
                }
                syncRequest.addData(sfConnector.getId(), e);
            }
            DataService dataService = dataServiceFactory.getDataService(sfConnector.getMetadata());
            dataService.create(syncRequest);
        }
    }

    /**
     * Resets an external connector
     * @param connector Connector
     */
    protected void resetExternalConnector(Connector connector, Long numOfDays) {
        List<EntityDef> publishedEntities = filterSyncedEntities(schemaService.getSyncariSchema().getEntities());

        List<EntitySchema> entitySchemaList = new ArrayList<>();

        publishedEntities.stream().forEach(entityDef -> {
            List<EntityDefinition> entities = schemaService.getEntities(connector.getId());
            entitySchemaList.addAll(dataTransformer.toEntitySchema(entities, connector));
        });

        // Empty synapse entities
        entitySchemaList.forEach(entity -> {
            long start = Instant.now().minus(Duration.ofDays(numOfDays)).toEpochMilli();
            long end = Instant.now().toEpochMilli();
            ConnectorInfo connectorInfo = transformer.toConnectorInfo(connector);
            WatermarkInfo watermark = new WatermarkInfo(start, end, false, 0);
            SyncRequest syncRequest = new SyncRequest()
                    .Builder(connectorInfo, entity)
                    .setWatermark(watermark);
            DataService dataService = dataServiceFactory.getDataService(connector.getMetadata());
            List<EntityData> entityData = new ArrayList<>();
            dataService.getByWatermark(syncRequest).getIterator().forEachRemaining(entityData::addAll);
            syncRequest.setData(Map.of(connector.getId(), entityData));
            log.info(String.format("Deleting entity %s from connector %s", entity.getDisplayName(), connector.getName()));
            dataService.delete(syncRequest);

            // Delete custom fields if any
            entity.getAttributes().stream().filter(attr -> attr.isCustom() && Status.ACTIVE.equals(attr.getStatus())).forEach(attr -> {
                DeleteFieldRequest delReq = new DeleteFieldRequest(connectorInfo, entity.getApiName(), attr.getApiName());
                delReq.setExternalFieldId(attr.getExternalId());
                log.info(String.format("Deleting custom field %s from entity %s of connector %s",
                        attr.getApiName(), entity.getDisplayName(), connector.getName()));
                try {
                    dataServiceFactory.getSchemaService(connector.getMetadata()).deleteField(delReq);
                } catch(Exception e){
                    log.info("Unable to delete custom field {} from entity {} of connector {}", attr.getApiName(), entity.getDisplayName(), connector.getName());
                }
            });
        });
    }

    /**
     * Remove records from syncari collections to reset the database
     */
    protected void resetSyncariCollections(){
        idMappingRepo.reset();
        stagedBatchRecordRepo.reset();
        syncDetailRepo.reset();
        sinkLogRepo.reset();
        stagedBatchRepo.reset();
        streamRepo.reset();
        mappingGraphRepo.reset();
        edgeRepo.reset();
        layoutRepo.reset();
        notificationRepo.reset();
    }

    /**
     * Drop all customer entity collections prefixed with "syncari_*"
     */
    protected void dropCustomerEntityCollections(){
        customerMongoUtils.getCollectionNamesStartWith("syncari_").forEach(customerMongoUtils::dropCollection);
    }

    /**
     * Reset the BigQuery event store
     * @param syncariId
     */
    protected void resetEventStore(String syncariId){
        eventStore.deprovision(syncariId);
        eventStore.provision(syncariId);

    }

    // Filter only the entities which have data synced from external connector
    private List<EntityDef> filterSyncedEntities(List<EntityDef> entities){
        return entities.stream()
                .filter(e -> !(e.getPipelineStatus().equals(PipelineStatus.UNMAPPED)
                            || e.getPipelineStatus().equals(PipelineStatus.DRAFT))
                ).collect(Collectors.toList());
    }



    public void setEventStore(EventStore eventStore){
        this.eventStore = eventStore;
    }
}

class SalesforceDemoData {

    private String header = "FirstName|LastName|Email|Title|AccountName|Phone";
    private List<String> data = Arrays.asList(
            "scarlett|cameron|Cameron@safe-mail.net|Sales Engineer|Grand Hotels & Resorts Ltd|(403) 521-1691",
            "CARSON|EMMA|Emma@safe-mail.net|CEO|Zendesk|(862) 165-6432",
            "scarlett|cameron|Cameron@safe-mail.net|Sales Engineer|Grand Hotels & Resorts Ltd|(403) 521-1691",
            "scarlett||Cameron@safe-mail.net|Sales Engineer|Grand Hotels & Resorts Ltd|(403) 521-1691",
            "ella|Arianna||Director|Oracle|(603) 540-5484",
            "jack|Gabriel||SVP Technology|Dickenson plc|(662) 361-5973",
            "Lucy|Audrey|Audrey@rocketmail.com|SVP Administration and Finance|Zendesk|(578) 066-2862",
            "madelyn|Liam|Liam@sky.com|SVP Technology|Facebook|(583) 365-5881",
            "madelyn|Liam|Liam@sky.com|SVP Technology|Facebook|(583) 365-5881",
            "Jose|noah|Noah@inbox.com|SVP Technology|Grand Hotels & Resorts Ltd|(691) 030-1341",
            "genesis|caleb|Caleb@gmx.com|SVP Operations|United Oil & Gas Corp.|(320) 956-1473",
            "lUCAS|Amelia||SVP Operations|Express Logistics and Transport|(590) 560-5126",
            "cHLOE|jacob|Jacob@outlook.com|SVP Operations|Oracle|(996) 814-2790",
            "Serenity|Everly|Everly@icloud.com|Accountant|Ibm|(280) 979-0259",
            "Jaxson|Wyatt|Wyatt@fastmail.fm|SVP Administration and Finance|Zendesk|(664) 312-7049",
            "LILY|Anthony|Anthony@sympatico.ca|SVP Technology|Grand Hotels & Resorts Ltd|(674) 941-6361",
            "Adrian|Madelyn||Sales Engineer|Ibm|(797) 780-9121",
            "CORA|Lydia|Lydia@zoho.com|Data Engineer|Edge Communications|(464) 098-6957",
            "Aubrey|Daniel|23345|CEO|Ibm|(766) 881-4406",
            "jose|Jaxson||VP Facilities|Gusto|(447) 074-6268",
            "Jordan|Josiah|Josiah@talktalk.co.uk|CEO|Dickenson plc|(559) 895-2222",
            "Jackson|Christopher|Christopher@blueyonder.co.uk|SVP Technology|Zendesk|(815) 583-5422",
            "Sophie|Savannah|Savannah@pobox.com|CEO|Oracle|(377) 336-1449",
            "Sophie|Savannah|Savannah@pobox.com|CEO|Oracle|(377) 336-1449",
            "Neelesh|Shastry|neelesh@syncari.com||Syncari Inc|(418) 368-7704",
            "Nick|Bonfiglio|nick@syncari.com||Syncari Inc|(418) 368-0704",
            "Ben|Bayat|ben@nextgenvp.com||Nextgen Venture Partners|(849) 407-3086",
            "Eleanor|Eli|Eli@bt.com|CEO|Express Logistics and Transport|(649) 041-6720",
            "Mason|Jackson|Jackson@aol.com|SVP Operations|Burlington Textiles Corp of America|(331) 889-3037",
            "Nicholas|Grace|Grace@bt.com|Director|Express Logistics and Transport|(405) 607-0244",
            "Andrew|Julian|Julian@icloud.com|SVP Administration and Finance|Ibm|(849) 407-3086",
            "Easton|Jackson|Jackson@gmail.com|VP Finance|Pyramid Construction Inc.|(243) 207-6292",
            "Vivian|Noah|Noah@tutanota.com|Sales Engineer|Express Logistics and Transport|(456) 435-6212",
            "Alexa|Everett|Everett@pobox.com|VP Facilities|Marketo|(042) 300-8704",
            "Samuel|Thomas|Thomas@tuta.io|VP Facilities|Express Logistics and Transport|(669) 432-9819",
            "Alice|Oliver|Oliver@hotmail.co.uk|SVP Technology|Zendesk|(614) 385-2971",
            "Layla|Jackson|Jackson@tutamail.com|VP Facilities|Oracle|(310) 975-2902",
            "Mateo|Bryson|Bryson@tutanota.com|SVP Administration and Finance|Pyramid Construction Inc.|(725) 574-9149",
            "Hazel|Sofia|Sofia@shaw.ca|Sales Engineer|Pyramid Construction Inc.|(847) 858-5484",
            "Lucas|Eli|Eli@ntlworld.com|SVP Operations|Zendesk|(591) 453-2022",
            "Valentina|Zoe|Zoe@pobox.com|Director|Marketo|(427) 405-7982",
            "Alice|Nora|Nora@email.com|CEO|GenePoint|(524) 712-1538",
            "Alice|Nora|Nora@email.com|CEO|GenePoint|(524) 712-1538",
            "Alice|Nora|Nora@email.com|CEO|GenePoint|(524) 712-1538",
            "Alice|Nora|Nora@email.com|CEO|GenePoint|(524) 712-1538",
            "Gianna|Sarah|Sarah@tuta.io|Manager|Edge Communications|(966) 627-0305",
            "Abigail|Aubrey|Aubrey@aol.com|SVP Administration and Finance|sForce|(660) 166-8291",
            "Julia|Everly|Everly@tutanota.de|SVP Operations|sForce|(179) 276-2774",
            "Jaxon|Angel|Angel@tuta.io|Manager|Gusto|(421) 977-2707",
            "Chloe|Leah|Leah@freeserve.co.uk|SVP Operations|Zendesk|(801) 686-0807",
            "Jonathan|William|William@facebook.net|QA|Facebook|(257) 675-0715",
            "robert|Claire|Claire@facebook.com|VP Finance|Marketo|(250) 360-6566",
            "rob|Claire|Claire@facebook.com|VP Finance|Marketo|(250) 360-6566",
            "rob||Claire@facebook.com|VP Finance|Marketo|(250) 360-6566",
            "Henry|Parker|Parker@inbox.com|SVP Technology|GenePoint|(647) 028-7661",
            "Hudson|Lily|Lily@yahoo.ca|VP Facilities|Ibm|(095) 322-1465",
            "Gabriel|Kayden|Kayden@icloud.com|CEO|GenePoint|",
            "Axel|Anna|Anna@tutanota.de|SVP Operations|Edge Communications|(031) 781-5964"
    );

    public List<List<String>> getData(){
        return data.stream()
                .map(d -> Arrays.asList(StringUtils.splitPreserveAllTokens(d, '|')))
                .collect(Collectors.toList());
    }

    public List<String> getHeader(){
        return Arrays.asList(StringUtils.splitPreserveAllTokens(header, '|'));
    }
}
