package com.syncari.viper.streams.stages;

import com.syncari.AbstractSyncariTest;
import com.syncari.connector.EntityData;
import com.syncari.core.model.Connector;
import com.syncari.core.model.IdMapping;
import com.syncari.core.model.StagedBatchRecord;
import com.syncari.core.pipeline.PipelineEvaluator;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.StagedBatchRecordRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.FunctionService;
import com.syncari.core.service.SchemaService;
import com.syncari.viper.ViperContext;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class ExecutePipelineAndSaveTest extends AbstractSyncariTest {
    @Autowired
    private EntityDefinitionRepo entityProxyRepo;
    @Autowired
    private AttributeRepo attributeProxyRepo;

    @Autowired
    private FunctionService functionService;

    @Autowired
    private SchemaService schemaService;
    @Autowired
    private PipelineEvaluator evaluator;

    @Autowired
    private ConnectorService connectorService;

    @Autowired
    private EntityRepo entityRepo;
    @Autowired
    private StagedBatchRecordRepo recordRepo;

    private Connector syncariConnector;
    private Connector sfdcConnector;
    @Value("${salesforce.url}")
    String salesforceUrl;

    @Value("${salesforce.user}")
    private String sfdcUser;

    @Value("${salesforce.password}")
    private String password;

    @Value("${salesforce.token}")
    private String token;

    @Autowired
    ExecuteFieldPipeline executePipeline;

    ViperContext context;

    @Before
    public void setUp(){
        super.setUp();
        resetRepos(executePipeline.idMappingRepo);
//        sfdcConnector = new Connector("sfdc1", connectorService.describe("salesforce").getId(), salesforceUrl, sfdcUser,
//                password);
//        sfdcConnector.getAuthConfig().setToken(token);
//        sfdcConnector = connectorService.save(sfdcConnector);
//        connectorService.authenticated(sfdcConnector.getId());
//        connectorService.activate(sfdcConnector.getId());
//
//        syncariConnector =connectorService.getSyncariConnector();
//        context = new ViperContext(org, instance, user);

    }
    @Test
    public void deletingAllMappingsDeletesIdMapping(){
        var records = List.of(createStagedBatchRecord("1", "id1"),
                createStagedBatchRecord("2", "id2"),
                createStagedBatchRecord("3", "id3"),
                createStagedBatchRecord("4", "id4"),
                createStagedBatchRecord("5", "id5"),
                createStagedBatchRecord("6", "id6"));
        executePipeline.idMappingRepo.upsert(List.of(
                new IdMapping().setEntityName("account").setSyncariId("1").addMapping("c1","id1","e1"),
                new IdMapping().setEntityName("account").setSyncariId("2").addMapping("c1","id2","e1"),
                new IdMapping().setEntityName("account").setSyncariId("3").addMapping("c1","id3","e1")
        ));
        assertEquals(3,executePipeline.idMappingRepo.count());
        executePipeline.deleteIdMapping(records,"account");
        assertEquals(0,executePipeline.idMappingRepo.count());
    }

    private StagedBatchRecord createStagedBatchRecord(String syncariId, String externalRecordId) {
        return new StagedBatchRecord().setSyncariId(syncariId).
                setEntityData(new EntityData().setConnectorId("c1"))
                .setExternalEntityDefinitionId("e1")
        .setExternalRecordId(externalRecordId);
    }



}
