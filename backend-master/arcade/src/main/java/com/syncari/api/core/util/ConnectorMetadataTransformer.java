package com.syncari.api.core.util;

import com.syncari.api.rest.controllers.data.ConnectorMetadataDTO;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.model.SharedItem;
import com.syncari.core.service.ConnectorMetadataService;
import com.syncari.utils.Timer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
public class ConnectorMetadataTransformer {

    private static final int SESSION_DURATION = 5; //MINUTES

    @Autowired
    private ConnectorMetadataService connectorMetadataService;

    public ConnectorMetadataDTO toConnectorMetadata(ConnectorMetadata connectorMetadata){
    	Timer timer = new Timer(200, "ConnectorMetadataTransformer::toConnectorMetadata", log);
        ConnectorMetadataDTO connectorMetadataDTO = new ConnectorMetadataDTO(connectorMetadata);
        Optional<SharedItem> sharedItemOptional = connectorMetadataService.findSharedItemByConnectorMetaData(connectorMetadata);
        if(sharedItemOptional.isPresent()){
            connectorMetadataDTO.setGlobal(sharedItemOptional.get().isPublishedToMarketplace());
        }
        if ("syncari".equalsIgnoreCase(connectorMetadata.getName())){
            connectorMetadataDTO.setHideFromSynapseList(true);
        }
        timer.close();
        connectorMetadataDTO.setCreatable(!"Syncari Datastore".equals(connectorMetadata.getDisplayName()));
        return connectorMetadataDTO;
    }
}
