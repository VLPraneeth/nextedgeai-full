package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.BSON;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class SYN_10372_IdMappingFix {

    @ChangeSet(order = "001", id = "removeIdMapping", author = "venkat", runAlways = true)
    public void removeIdMapping(MongoTemplate template) throws Exception {

        var dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        String externalEntityId = System.getProperty("externalEntityId");
        String idMappingFile = System.getProperty("idMappingFile");

        MongoCollection<Document> idMapping = template.getCollection("idMapping");

        var mappings = getMappings(idMappingFile);
        mappings.entrySet().stream().forEach(e -> {
            Bson query = new Document().append("syncariId", e.getKey());
            Bson fields = new Document().append("mappings", new Document().append( "entityId", e.getValue()).append("entityDefinitionId", externalEntityId));
            Bson update = new Document("$pull", fields);

            Document doc = idMapping.find(query).first();
            log.info("Found doc {}", doc);
            log.info("About to remove mapping for syncari id {} external record id {} external def id {}", e.getKey(), e.getValue(), externalEntityId);
            if (!dryRun) {
                var updateResult = idMapping.updateOne(query, update);
                log.info("Modified idMapping record {}", updateResult.getModifiedCount());
            }
        });
    }

    public Map<String, String> getMappings(String fileName) throws Exception {
        var url = this.getClass().getClassLoader().getResource(fileName);
        try (Stream<String> stream = Files.lines(Paths.get(url.toURI()), Charset.forName("utf-8"))) {
            return stream.map(s -> s.split(",")).collect(Collectors.toMap(a -> a[0], a -> a[1]));
        }
    }

}
