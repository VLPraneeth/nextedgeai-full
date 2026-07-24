package com.syncari.core.repositories.syncari;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.syncari.core.actions.CustomActionDefinition;
import com.syncari.core.model.ActionDefinition;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.model.SharedItem;
import com.syncari.core.model.insights.sharing.InsightsDashboardSharedItem;
import com.syncari.core.model.insights.sharing.SharedItemInvitationStatus;
import com.syncari.core.model.misc.Sharable;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageInfo;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.quickstart.v2.QuickStart;
import com.syncari.core.share.SharedItemObject;
import com.syncari.core.utils.DataCriteriaVisitor;
import com.syncari.core.utils.SyncariMongoUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Service
@Slf4j
public class SharedItemRepoImpl implements SharedItemCustom {

    private final String COLLECTION_NAME = "sharedItem";

    @Autowired
    protected MongoTemplate syncariMongoTemplate;

    @Autowired
    SyncariMongoUtils syncariMongoUtils;

    public List<SharedItem> getSharedItems(Sharable itemType, boolean publishedToMarketplace,
                                           String displayName, String sharedItemId, int limit) {

        Criteria criteria = where("itemType").is(itemType).and("publishedToMarketplace").is(publishedToMarketplace);

        if(sharedItemId!=null){
            criteria = criteria.and("sourceId").gt(sharedItemId);
        }

        if(displayName!=null){
            criteria = criteria.and("itemObject.displayName").is(displayName);
        }

        Query query = new Query().addCriteria(
                criteria
        ).with(Sort.by("sourceId").ascending()).limit(limit);

        return syncariMongoTemplate.find(query, SharedItem.class);
    }

    @Override
    public List<SharedItem> findAllSharedItemsByItemTypeAAndSharingInstance(Sharable itemType, String sharingInstance,List<String> currentOrgInstances){
        if (StringUtils.isEmpty(sharingInstance) || null == itemType) return List.of();
        String sharingIntanceKey = "sharingInstances."+sharingInstance;
        Criteria criteria = where("itemType").is(itemType).orOperator(where(sharingIntanceKey).is(sharingInstance),
                where("sharedWithOrg").is(true).and("sourceInstance").in(currentOrgInstances));
        Query query = new Query().addCriteria(
                criteria
        ).with(Sort.by("_id").descending());
        log.info("Query to execute for findAllSharedItemsByItemTypeAAndSharingInstance is {}", query);
        return syncariMongoTemplate.find(query, SharedItem.class);


    }

    public Page<SharedItem> getSharedItems(String sourceId,PageCursor pageInfo,Optional<Expression> filter){
        // Add source Id
        Optional<Expression> intialExpression = Optional.ofNullable(Expression.eq(Expression.var("sourceId"),Expression.lit(sourceId)));
        Optional<Expression> cursorExp = StringUtils.isBlank(pageInfo.getCursor()) ? intialExpression : intialExpression.map(iE -> Expression.and(iE,getPageFilter(pageInfo)));
        Optional<Expression> finalExpression = filter.map(i -> cursorExp.map(c -> Expression.and(i, c)).orElse(i))
                .or(() -> cursorExp);
        Optional<DataCriteriaVisitor> criteriaVisitor = finalExpression
                .map(i -> new DataCriteriaVisitor(i, Map.of(), Optional.empty()));
        Optional<Bson> searchCriteria = criteriaVisitor.map(v -> v.createCriteria());
        log.debug("Search criteria - {}", searchCriteria);

        // Sort desc when viewing previous page to get the correct result set
        Bson sort = new BasicDBObject("_id", pageInfo.isForward() ? 1 : -1);
        // coverter to convert doc to SharedItem, keeping a variable for more readability;
        Function<Document, SharedItem> converter = doc -> createSharedItem(doc);
        List<SharedItem> results = syncariMongoUtils.searchPaged(COLLECTION_NAME,searchCriteria,sort,converter,pageInfo.getPageSize() + 1);

        boolean hasMore = pageInfo.isForward() ? results.size() == pageInfo.getPageSize() + 1 : true;
        boolean hasPrevious = StringUtils.isEmpty(pageInfo.getCursor()) ? false
                : (results.size() == pageInfo.getPageSize() + 1) ? true : !hasMore;

        if (results.size() > pageInfo.getPageSize()) {
            results = results.subList(0, results.size() - 1);
        }

        // Restore the results order to be asc by ID
        if (!pageInfo.isForward()) {
            Collections.reverse(results);
        }

        String pageStart = results.size() > 0 ? results.get(0).getId() : null;
        String pageEnd = results.size() > 0 ? results.get(results.size() - 1).getId() : null;
        Page<SharedItem> page = new Page<SharedItem>();
        page.setPageInfo(new PageInfo(pageStart, pageEnd, hasMore).addSort("Id", true).setHasPrevious(hasPrevious));
        page.setRecords(results);
        Optional<Expression> countExp = filter.map(i -> intialExpression.map(f -> Expression.and(i, f)).orElse(i))
                .or(() -> intialExpression);


        Optional<DataCriteriaVisitor> countCriteria = countExp
                .map(i -> new DataCriteriaVisitor(i, Map.of(), Optional.empty()));
        long totalCount = syncariMongoUtils.count(COLLECTION_NAME, countCriteria.map(v -> v.createCriteria()));
        page.getPageInfo().setTotalCount(totalCount);
        return page;
    }





    private Expression getPageFilter(PageCursor pageInfo) {
        Expression lhs = Expression.var("_id");
        Expression rhs = Expression.lit(new ObjectId(pageInfo.getCursor()));
        return pageInfo.isForward() ? Expression.gt(lhs, rhs) : Expression.lt(lhs, rhs);
    }

    private SharedItem createSharedItem(Document document){
        SharedItem sharedItem = new SharedItem();
        sharedItem.setId(document.getObjectId("_id").toHexString());
        sharedItem.setSourceId(document.getString("sourceId"));
        sharedItem.setItemType(Sharable.valueOf(document.getString("itemType")));
        sharedItem.setSourceInstance(document.getString("sourceInstance"));
        sharedItem.setPublishedToMarketplace(document.getBoolean("publishedToMarketplace"));
        sharedItem.setRecipientsUserId(document.getString("recipientsUserId"));
        sharedItem.setRecipientsEmailId(document.getString("recipientsEmailId"));
        sharedItem.setItemObject(getSharedItem(document.get("itemObject")));
        sharedItem.setCreatedBy(document.getString("createdBy"));
        sharedItem.setUpdatedBy(document.getString("updatedBy"));
        sharedItem.setUpdatedAt(document.getDate("updatedAt"));
        sharedItem.setCreatedAt(document.getDate("createdAt"));
        return sharedItem;
    }

    private SharedItemObject getSharedItem(Object object) {
        if(object == null) return null;
        if(object instanceof ActionDefinition) return (ActionDefinition) object;
        if(object instanceof ConnectorMetadata) return (ConnectorMetadata) object;
        if(object instanceof CustomActionDefinition) return (CustomActionDefinition) object;
        if(object instanceof QuickStart) return (QuickStart) object;
        if(object instanceof InsightsDashboardSharedItem) return (InsightsDashboardSharedItem) object;
        if(object instanceof Document) {
            Document document = (Document) object;
            try{
                String classN = document.getString("_class");
                String json = document.toJson();
                ObjectMapper mapper = new ObjectMapper();
                switch (classN){
                    case "com.syncari.core.quickstart.v2.QuickStart": return mapper.readValue(json, QuickStart.class);
                    case "com.syncari.core.model.ActionDefinition": return mapper.readValue(json, ActionDefinition.class);
                    case "com.syncari.core.model.ConnectorMetadata": return mapper.readValue(json, ConnectorMetadata.class);
                    case "com.syncari.core.actions.CustomActionDefinition": return mapper.readValue(json, CustomActionDefinition.class);
                    case "com.syncari.core.model.insights.sharing.InsightsDashboardSharedItem": return getInsightSharedItem(document);
                    default: throw new RuntimeException("Not supported sharing object");
                }
            }catch (JsonProcessingException e) {
                log.error("Failed to deserialize SharedItemObject due to {}", e.getMessage(), e);
                throw new RuntimeException("Failed to deserialize SharedItemObject.", e);
            }
        }
        return null;
    }

    private InsightsDashboardSharedItem getInsightSharedItem(Document doc){
        InsightsDashboardSharedItem sharedItem = new InsightsDashboardSharedItem();
        sharedItem.setEmailMessage(doc.getString("emailMessage"));
        sharedItem.setDashboardSourceInstanceId(doc.getString("dashboardSourceInstanceId"));
        sharedItem.setDashboardId(doc.getString("dashboardId"));
        sharedItem.setDashboardDescription(doc.getString("dashboardDescription"));
        sharedItem.setDashboardDisplayName(doc.getString("dashboardDisplayName"));
        sharedItem.setSenderUserId(doc.getString("senderUserId"));
        sharedItem.setExpiredTime(doc.getLong("expiredTime"));
        sharedItem.setLastVisitedDate(doc.getLong("lastVisitedDate"));
        if ((doc.getLong("expiredTime") > Instant.now().toEpochMilli()) || (doc.getLong("expiredTime") == -1)){
            sharedItem.setInvitationStatus(SharedItemInvitationStatus.valueOf(doc.getString("invitationStatus")));
        }else{
            sharedItem.setInvitationStatus(SharedItemInvitationStatus.EXPIRED);
        }
        return sharedItem;

    }


}
