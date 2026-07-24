package com.syncari.core.functions;

import com.syncari.connector.EntityData;
import com.syncari.connector.service.def.FileService;
import com.syncari.core.DataTransformer;
import com.syncari.core.actions.ActionConstants;
import com.syncari.core.actions.DefaultAction;
import com.syncari.core.model.ActionResult;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.GenericActionConfig;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.service.DataServiceFactory;
import com.syncari.core.utils.MongoCriteria;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

@Component(ActionConstants.CREATE_FILE_ACTION)
@Slf4j
public class CreateFileAction extends DefaultAction {
    public static final String FILE_NAME = "fileName";
    public static final String FILE_CONTENT = "fileContent";
    public static final String FOLDER = "folder";
    public static final String STORAGE_SYNAPSE_ID = "storageSynapseId";
    public static final String TOTAL_BYTES_WRITTEN = "totalBytesWritten";
    public static final String SYNCARI_ENTITY_DEF_ID = "syncariEntityDefId";
    ;

    @Autowired
    DataServiceFactory dataServiceFactory;
    @Autowired
    DataTransformer dataTransformer;
    @Autowired
    private EntityRepo entityRepo;

    public ActionResult execute(GenericActionConfig actionConfig, GraphContext context) {
        String syncariEntityDefId = getConfig(SYNCARI_ENTITY_DEF_ID, actionConfig);
        EntityDefinition entity = context.cache(syncariEntityDefId, () -> schemaService.getEntity(syncariEntityDefId));
        String fileName = tokenHelper.resolveTokens(context, getConfig(FILE_NAME, actionConfig));
        String folder = tokenHelper.resolveTokens(context, getConfig(FOLDER, actionConfig));

        //Supports S3,SFTP, Google Drive right now
        String storageSynapseId = getConfig(STORAGE_SYNAPSE_ID, actionConfig);
        final Optional<Connector> maybeConnector = connectorService.find(storageSynapseId);
        Map<String, Object> predicates = getConfig(PREDICATE, actionConfig);

        return maybeConnector.map(connector -> {
            final FileService fileService = dataServiceFactory.getFileService(connector.getMetadata());
            Optional<MongoCriteria> mongoCriteria = getCriteria(context, entity, predicates, entityRepo);
            final Iterator<EntityData> searchResults = entityRepo.search(entity, mongoCriteria);
            context.put("searchResults", searchResults);
            String content = tokenHelper.resolveJTwigToken(context, getConfig(FILE_CONTENT, actionConfig)).x;
            final byte[] bytes = content.getBytes();
            fileService.writeFile(dataTransformer.toConnectorInfo(connector), new ByteArrayInputStream(bytes), fileName, folder);
            return new ActionResult(true, Map.of(TOTAL_BYTES_WRITTEN, bytes.length));
        }).orElse(new ActionResult(true, Map.of(TOTAL_BYTES_WRITTEN, 0)));
    }
}