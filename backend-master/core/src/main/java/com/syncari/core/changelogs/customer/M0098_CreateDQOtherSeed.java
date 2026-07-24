
package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.model.DataQualityCategory;

import java.util.Date;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0098")
public class M0098_CreateDQOtherSeed {

  @ChangeSet(order = "001", id = "categoryOther", author = "francis")
  public void categoryOther(MongoTemplate template) {
    MongoCollection<Document> dqCategory = template.getCollection("dataQualityCategory");

    dqCategory.insertOne(new Document("name", DataQualityCategory.OTHER)
        .append("custom", false)
        .append("createdAt", new Date())
        .append("updatedAt", new Date())
        .append("seeded", true));
  }

  @ChangeSet(order = "002", id = "categoryDefault", author = "sathish")
  public void categoryNonCustomDefaults(MongoTemplate template) {
    MongoCollection<Document> dqCategory = template.getCollection("dataQualityCategory");
    Document completeness = new Document("name", DataQualityCategory.COMPLETENESS)
            .append("custom", true)
            .append("createdAt", new Date())
            .append("updatedAt", new Date())
            .append("seeded", true);
    Document validity = new Document("name", DataQualityCategory.VALIDITY)
            .append("custom", true)
            .append("createdAt", new Date())
            .append("updatedAt", new Date())
            .append("seeded", true);
    Document uniqueness = new Document("name", DataQualityCategory.UNIQUENESS)
            .append("custom", true)
            .append("createdAt", new Date())
            .append("updatedAt", new Date())
            .append("seeded", true);
    Document conformity = new Document("name", DataQualityCategory.CONFORMITY)
            .append("custom", true)
            .append("createdAt", new Date())
            .append("updatedAt", new Date())
            .append("seeded", true);

    dqCategory.insertMany(List.of(completeness, validity, uniqueness, conformity));

  }


}
