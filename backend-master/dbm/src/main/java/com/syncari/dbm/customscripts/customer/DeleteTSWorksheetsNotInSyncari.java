package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.User;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.provider.ts.*;
import com.syncari.core.service.DatasetService;
import com.syncari.core.service.TSService;
import com.syncari.core.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class DeleteTSWorksheetsNotInSyncari {

    @ChangeSet(order = "001", id = "deleteTSWorksheetNotExistsInSyncari", author = "rohit", runAlways = true)
    public void deleteTSWorksheetNotExistsInSyncari(MongoTemplate template) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        TSService tsService =  MigrationContext.getTSService();
        DatasetService datasetService = MigrationContext.getDatasetService();
        UserService userService = MigrationContext.getUserService();

        Optional<User> userToSetContext = userService.findActiveUserByEmail("systemuser@syncari.com");
        userToSetContext.ifPresentOrElse(usr -> SyncariContext.setUser(usr), () -> {
            SyncariContext.setUser(userService.findActiveUserByEmail("system_syncari_admin@syncari.com").get());
        });

        TSMetadataSearchReq req = new TSMetadataSearchReq();
        TSSearchMetadataSort sort = new TSSearchMetadataSort().setField_name("LAST_ACCESSED");
        req.setMetadata(List.of(new TSMetadataListItemInput().setType(TSMetadataType.LOGICAL_TABLE.name())));
        req.setInclude_headers(true);
        req.setSort_options(sort);

        String grpName = SyncariContext.getSyncariId() + "_" + TSPrivileges.DATAMANAGEMENT.name();
        TSPermission permission = new TSPermission()
                .setPrincipal(new TSPrincipalInput().setIdentifier(grpName).setType("USER_GROUP")).setShare_mode("MODIFY");
        req.setPermissions(List.of(permission));

        HttpHeaders headers = tsService.getHeaders(Optional.of("tsadmin"),300L);
        List<TSMetadataSearchResponse> searchResponses = tsService.searchMetadata(req, Optional.of("tsadmin"),headers)
                .stream().filter(m -> (!((String)m.getMetadata_header().get("authorName")).equalsIgnoreCase("system")))
                .collect(Collectors.toList());
        List<TSMetadataSearchResponse> workSheetResponses = searchResponses.stream().filter(m -> ((String)m.getMetadata_header().get("type")).equalsIgnoreCase("WORKSHEET")).collect(Collectors.toList());
        Map<String, List<String>> allWorksheets = new HashMap<>();
        workSheetResponses.forEach(ws -> {
            if (allWorksheets.containsKey(ws.getMetadata_name())){
                List<String> existingIds = allWorksheets.get(ws.getMetadata_name());
                existingIds.add(ws.getMetadata_id());
                allWorksheets.put(ws.getMetadata_name(),existingIds);
            }else{
                List<String> ids = new ArrayList<>();
                ids.add(ws.getMetadata_id());
                allWorksheets.put(ws.getMetadata_name(),ids);
            }
        });
        Map<String, List<String>> worksheetsTobeDeleted = new HashMap<>();
        List<Dataset> allActiveDatasets = datasetService.getAllApprovedDatasetsWithVersion();
        Map<String, String> allActiveDatasetsMap = allActiveDatasets.stream().collect(Collectors.toMap(Dataset :: getName, Dataset :: getId));

        allWorksheets.forEach((k,v) -> {
            if (!allActiveDatasetsMap.containsKey(k)){
                worksheetsTobeDeleted.put(k,v);
            }else{
                log.info("Worksheet with name {} and id {} exists with dataset id {}", k, v, allActiveDatasetsMap.get(k));
            }
        });
        List<String> sqlViewsNamesTobeDeleted = new ArrayList<>();
        worksheetsTobeDeleted.forEach((k,v) -> {
            try{
                sqlViewsNamesTobeDeleted.add(k);
                if (!dryRun){
                    log.info("Worksheet getting deleted is {} with name {}", v, k);
                    ((List<String>)v).forEach(idToDelete -> {
                        tsService.deleteMetadata(idToDelete, headers);
                    });
                }else{
                    log.info("Running in dry run mode, worksheet to be deleted is {} with name {}", v, k);
                }
            }catch (Exception e){
                log.error("Exception occurred while deleting worksheet with name {} and id {}", k, v);
            }
        });
        sqlViewsNamesTobeDeleted.forEach(k -> {
            try{
                if (!dryRun){
                    log.info("SQL Views getting with name {}",  k);
                    tsService.deleteMetadata(k, headers);
                }else{
                    log.info("Running in dry run mode, SQL Views to be deleted name {}", k);
                }
            }catch (Exception e){
                log.error("Exception occurred while deleting worksheet with name {}", k);
            }
        });
    }
}

