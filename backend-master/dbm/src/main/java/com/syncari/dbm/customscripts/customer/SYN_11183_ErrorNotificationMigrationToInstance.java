package com.syncari.dbm.customscripts.customer;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.BooleanUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SYN_11183_ErrorNotificationMigrationToInstance {
	private int counter = 1;
	
	@ChangeSet(order = "002", id = "MigrateFromProfileToInstance", author = "sibin")
	public void migrateFromProfileToInstance(MongoTemplate template) {
		boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
		MongoCollection<Document> preferenceTemplate = template.getCollection("userPreference");
		preferenceTemplate.find().forEach(new Consumer<Document>() {
            @Override
            public void accept(Document pref) {
                var notifConf = (Document) pref.get("errorNotification");
                if(Objects.nonNull(notifConf)) {
                	log.info("errorNotification config found");
                	List<Map<String, String>> subscriptions = getSubscription(notifConf.get("subscriptions", List.class));
                	Set<String> emails = getEmails(notifConf.get("channelConfigurations", List.class));
                	log.info("Final subscription to be processed {}", subscriptions);
                	log.info("Final emails to be processed {}", emails);
                	if(CollectionUtils.isNotEmpty(subscriptions) && CollectionUtils.isNotEmpty(emails)) {
                		Map<String, Set<String>> finalConfig = new LinkedHashMap<>();
                		subscriptions.forEach(sub -> {
                			var frequency = sub.get("frequency");
                			var catalogId = sub.get("catalogId");
                			var data = finalConfig.get(frequency);
                			if(data == null) {
                				data = new LinkedHashSet<String>();
                				finalConfig.put(frequency, data);
                			}
                			data.add(catalogId);
                		});
                		log.info("Final migration to be processed {}", finalConfig);
                		if(!dryRunMode) {
                			createNewErrorNotificationConfig(template, finalConfig, emails);
                		}
                	}
                }
            }


        });
	}
	
	private void createNewErrorNotificationConfig(MongoTemplate template, Map<String, Set<String>> finalConfig, Set<String> emails) {
		MongoCollection<Document> configTemplate = template.getCollection("errorNotificationConfig");
		var emailSet = emails.stream().map(e ->  Map.of("email", e, "status", "Active")).collect(Collectors.toSet());
		finalConfig.keySet().forEach(frequency -> {
			configTemplate.insertOne(
					new Document("name", "Migrated_Email_Group_" + counter)
					.append("description", "Migrated_Email_Group_" + counter)
					.append("status", "Active")
					.append("notificationTypes", finalConfig.get(frequency))
					.append("emails", emailSet)
					.append("cadence", frequency)
					.append("lastNotificationTimestamp", new Date())
					.append("_class", "com.syncari.core.model.ErrorNotificationEmailConfig")
					.append("seeded", true)
					);
			counter++;
		});
	}
	
	private Set<String> getEmails(List<Document> emailConfigs) {
		Set<String> emailList = new LinkedHashSet<String>();
		if(CollectionUtils.isNotEmpty(emailConfigs)) {
			emailConfigs.forEach(emailConf -> {
    			if(BooleanUtils.isTrue(emailConf.getBoolean("active") && "email".equals(emailConf.getString("type")))) {
    				log.info("errorNotification email entry found");
    				Document config = (Document) emailConf.get("configuration");
    				if(Objects.nonNull(config)) {
    					List<String> emailsFromConfig = config.get("emails", List.class);
    					if(CollectionUtils.isNotEmpty(emailsFromConfig)) {
    						emailList.addAll(emailsFromConfig);
    					}
    				}
    			} else {
    				log.info("errorNotification email entry skipped");
    			}
    		});
    	}
		return emailList;
	}
	private List<Map<String, String>> getSubscription(List<Document> subscriptions) {
		List<Map<String, String>> subList = new ArrayList<Map<String,String>>();
		if(CollectionUtils.isNotEmpty(subscriptions)) {
    		subscriptions.forEach(sub -> {
    			if(BooleanUtils.isTrue(sub.getBoolean("active"))) {
    				log.info("errorNotification subscription entry found");
    				subList.add(Map.of("frequency", sub.getString("frequency"), "catalogId", sub.getString("catalogId")));
    			} else {
    				log.info("errorNotification subscription entry skipped");
    			}
    		});
    	}
		return subList;
	}
}
