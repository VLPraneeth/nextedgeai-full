package com.syncari.core.service;

import com.syncari.core.model.Layout;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.UUIDAuditModel;
import com.syncari.core.repositories.customer.LayoutRepo;
import com.syncari.core.utils.ValidationUtils;
import com.syncari.utils.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LayoutService {
    @Autowired
    private LayoutRepo layoutRepo;

    @Autowired
    @Qualifier("customerMongoTemplate")
    private MongoTemplate mongoTemplate;

    public List<Layout> findEdgeLayouts(List<String> edgeIds) {
        Timer timer = new Timer(5000, String.format("LayoutService::findEdgeLayouts::Total Edges::%s", edgeIds.size()), log);
        var layouts = layoutRepo.findByTargetIdInAndTargetType(edgeIds, Layout.EDGE_TYPE);
        timer.close();
        return layouts;
    }

    public List<Layout> findNodeLayouts(List<String> nodeIds) {
        Timer timer = new Timer(5000, String.format("LayoutService::findNodeLayouts::Total Nodes::%s", nodeIds.size()), log);
        var layouts =  layoutRepo.findByTargetIdInAndTargetType(nodeIds, Layout.NODE_TYPE);
        timer.close();
        return layouts;
    }

    public void deleteNodeLayouts(List<String> nodeIds) {
        layoutRepo.deleteAllByTargetId(nodeIds, Layout.NODE_TYPE);
    }
    public void deleteEdgeLayouts(List<String> edgeIds) {
        layoutRepo.deleteAllByTargetId(edgeIds, Layout.EDGE_TYPE);
    }

    public void deleteLayout(Layout layout) {
        layoutRepo.delete(layout);
    }

    public void deleteLayouts(List<Layout> layouts) {
        layoutRepo.deleteAll(layouts);
    }

    public void deleteLayouts(List<String> targetIds,String targetType) {
        ValidationUtils.validateCondition(!Layout.EDGE_TYPE.equals(targetType) && !Layout.NODE_TYPE.equals(targetType)
                , "Target type must be one of Edge or MappingNode");
        layoutRepo.deleteAllByTargetId(targetIds, Layout.EDGE_TYPE);
    }

    public List<Layout> findLayouts(List<String> targetIds, String targetType) {
        ValidationUtils.validateCondition(!Layout.EDGE_TYPE.equals(targetType) && !Layout.NODE_TYPE.equals(targetType)
                , "Target type must be one of Edge or MappingNode");
        return layoutRepo.findByTargetIdInAndTargetType(targetIds, targetType);
    }

    public Optional<Layout> findEdgeLayout(String edgeId) {
        return layoutRepo.findByTargetIdAndTargetType(edgeId, Layout.EDGE_TYPE);
    }

    public Optional<Layout> findNodeLayout(String nodeId) {
        return layoutRepo.findByTargetIdAndTargetType(nodeId, Layout.NODE_TYPE);
    }

    public List<Layout> getAllLayoutsInGraph(MappingGraph graph){
        List<String> nodeIds = graph.getNodes().stream().map(n -> n.getId()).collect(Collectors.toList());
        List<String> edgeIds = graph.getEdges().stream().map(e -> e.getId()).collect(Collectors.toList());
        List<Layout> layouts = findNodeLayouts(nodeIds);
        layouts.addAll(findEdgeLayouts(edgeIds));
        return layouts;
    }

    public List<Layout> upsert(List<Layout> layouts) {
        Map<String, List<Layout>> byType = layouts.stream().collect(Collectors.groupingBy(l -> l.getTargetType()));
        return byType.entrySet().stream().flatMap(entry -> {
            String key = entry.getKey();
            List<Layout> incoming = entry.getValue();
            return getStream(key, incoming).stream();
        }).collect(Collectors.toList());
    }


    private List<? extends Layout> getStream(String targetType, List<Layout> layouts) {
        var incomingTargetIds = layouts.stream().map(Layout::getTargetId).collect(Collectors.toSet());
        List<Layout> existing = layoutRepo.findByTargetIdInAndTargetType(incomingTargetIds.stream().collect(Collectors.toList()), targetType);
        var toDelete = existing.stream().filter(e -> !incomingTargetIds.contains(e.getTargetId())).collect(Collectors.toList());
        if(CollectionUtils.isNotEmpty(toDelete)) {
            log.info("These layouts should be deleted {}", toDelete.stream().map(UUIDAuditModel::getId).collect(Collectors.toList()));
        }


        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Layout.class);

        for (Layout layout : layouts) {
            Query query = new Query(Criteria.where("targetId").is(layout.getTargetId())
                                          .and("targetType").is(targetType));

            Update update = new Update()
                .setOnInsert("_id", ObjectId.get().toHexString())
                .setOnInsert("targetId", layout.getTargetId())
                .setOnInsert("targetType", layout.getTargetType())
                .setOnInsert("createdAt", layout.getCreatedAt())
                .setOnInsert("createdBy", layout.getCreatedBy())
                .set("layoutProperties", layout.getLayoutProperties())
                .set("updatedAt", layout.getUpdatedAt())
                .set("updatedBy", layout.getUpdatedBy());

            bulkOps.upsert(query, update);
        }

        bulkOps.execute();

        // Return the updated layouts by querying back
        var targetIds = layouts.stream().map(Layout::getTargetId).collect(Collectors.toList());
        return layoutRepo.findByTargetIdInAndTargetType(targetIds, targetType);
    }

}
