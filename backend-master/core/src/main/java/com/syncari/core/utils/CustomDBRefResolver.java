package com.syncari.core.utils;

import com.mongodb.DBRef;
import lombok.SneakyThrows;
import org.bson.Document;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;

import java.util.List;
import java.util.stream.Collectors;

public class CustomDBRefResolver extends DefaultDbRefResolver {

    public CustomDBRefResolver(MongoDbFactory mongoDbFactory) {
        super(mongoDbFactory);
    }

    @SneakyThrows
    @Override
    public Document fetch(DBRef dbRef) {
        return new Document("_id", dbRef.getId());
    }

    @SneakyThrows
    @Override
    public List<Document> bulkFetch(List<DBRef> dbRefs) {
        return dbRefs.stream().map(dbRef -> new Document("_id", dbRef.getId())).collect(Collectors.toList());
    }

}
