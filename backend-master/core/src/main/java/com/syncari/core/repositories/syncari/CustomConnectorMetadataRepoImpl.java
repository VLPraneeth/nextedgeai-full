package com.syncari.core.repositories.syncari;

import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.utils.Pair;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

public class CustomConnectorMetadataRepoImpl implements CustomConnectorMetadataRepo {

    @Autowired
    protected MongoTemplate syncariMongoTemplate;

    @Override
    public Pair<List<ConnectorMetadata>, Boolean> retrieveConnectorsPaginated(String connectorId, int limit) {
        Query query = new Query()
                .addCriteria(where("draftStatus").in(DraftStatus.APPROVED, DraftStatus.APPROVAL_IN_PROGRESS, null))
                .with(Sort.by("_id").ascending()).limit(limit + 1);
        if (connectorId != null) {
            Criteria criteria = where("_id").gt(new ObjectId(connectorId));
            query.addCriteria(criteria);
        }
        List<ConnectorMetadata> connectors = syncariMongoTemplate.find(query, ConnectorMetadata.class);
        boolean hasMore = connectors.size() == limit + 1;
        return Pair.of(connectors.subList(0, connectors.size() - 1), hasMore);
    }
}
