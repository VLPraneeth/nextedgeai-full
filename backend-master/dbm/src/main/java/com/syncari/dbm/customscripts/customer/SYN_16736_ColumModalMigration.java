package com.syncari.dbm.customscripts.customer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SYN_16736_ColumModalMigration {
	private int counter = 1;
	
	@ChangeSet(order = "001", id = "ColumModalMigration", author = "sibin", runAlways = true)
	public void migrateFromProfileToInstance(MongoTemplate template) {
		boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
		var userPreferncerRepo = MigrationContext.getUserPreferenceRepo();
		userPreferncerRepo.findAll().forEach(pref -> {
		  if(pref.getSchemaStudio() != null) {
		    if(CollectionUtils.isNotEmpty(pref.getSchemaStudio().getEntityColumns())) {
		      LinkedHashSet<Map<String, Object>> allEntityColumns = new LinkedHashSet<>();
		      if(CollectionUtils.isNotEmpty(pref.getSchemaStudio().getAllEntityColumns())) {
		        allEntityColumns.addAll(pref.getSchemaStudio().getAllEntityColumns());
		      }
		      pref.getSchemaStudio().getEntityColumns().forEach(ec -> {
		        allEntityColumns.add(Map.of("columnName", ec, "isSelected", true));
		      });
		      pref.getSchemaStudio().setAllEntityColumns(allEntityColumns);
		      log.info("Updating schema studion etity columns to {} ", allEntityColumns);
		    }
		    
		    if(CollectionUtils.isNotEmpty(pref.getSchemaStudio().getFieldColumns())) {
		      LinkedHashSet<Map<String, Object>> allFieldColumns = new LinkedHashSet<>();
              if(CollectionUtils.isNotEmpty(pref.getSchemaStudio().getAllFieldColumns())) {
                allFieldColumns.addAll(pref.getSchemaStudio().getAllFieldColumns());
              }
              pref.getSchemaStudio().getFieldColumns().forEach(fc -> {
                allFieldColumns.add(Map.of("columnName", fc, "isSelected", true));
              });
              pref.getSchemaStudio().setAllFieldColumns(allFieldColumns);
              log.info("Updating schema studion field columns to {} ", allFieldColumns);
            }
		    
		  }
		  if(pref.getDataStudio() != null) {
		    if(MapUtils.isNotEmpty(pref.getDataStudio().getSelectedColumns())) {
		      Map<String, Set<Map<String, Object>>> allColumns = new LinkedHashMap<>();
              if(MapUtils.isNotEmpty(pref.getDataStudio().getAllColumns())) {
                allColumns.putAll(pref.getDataStudio().getAllColumns());
              }
              pref.getDataStudio().getSelectedColumns().entrySet().forEach(entry ->{
                Set<Map<String, Object>> allFields = new LinkedHashSet<>();
                entry.getValue().forEach(fc -> {
                  allFields.add(Map.of("columnName", fc, "isSelected", true));
                });
                allColumns.put(entry.getKey(), allFields);
              });
              pref.getDataStudio().setAllColumns(allColumns);
              log.info("Updating data studion columns to {} ", allColumns);
            }
		  }
		  if(!dryRunMode) {
		    userPreferncerRepo.save(pref);
		  }
		});
	}
}
