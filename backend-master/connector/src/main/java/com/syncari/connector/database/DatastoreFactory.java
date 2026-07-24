package com.syncari.connector.database;

import com.syncari.connector.datastore.PostgresqlDatastoreService;
import com.syncari.connector.datastore.SyncariDatastoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.datastore.Datastore;
import com.syncari.connector.datastore.RedshiftDatastoreService;
import com.syncari.connector.datastore.SnowflakeDatastoreService;

@Component
public class DatastoreFactory {
    private final ApplicationContext context;

    @Autowired
    public DatastoreFactory(ApplicationContext context) {
        this.context = context;
    }

    public Datastore getService(ConnectorInfo info) {
        // Handle syncari datastore separately irrespective of datastore type
        if("Syncari Datastore".equals(info.getName())){
            return context.getBean(SyncariDatastoreService.class);
        }
        Object clazz = context.getBean(RedshiftDatastoreService.class);
        if(info.getDatastoreType() != null) {
            switch (info.getDatastoreType()) {
            case postgresql:
                clazz = context.getBean(PostgresqlDatastoreService.class);
                break;
            case snowflake:
                clazz = context.getBean(SnowflakeDatastoreService.class);
                break;
                
            default:
                // TODO remove this when we fully migrate all customers to postgres
                clazz = context.getBean(RedshiftDatastoreService.class);
            }
        }
        if (!Datastore.class.isAssignableFrom(clazz.getClass())) {
            throw new RuntimeException(String.format("%s does not implement Datastore interface",
                    info.getDatastoreType().name()));
        }
        return (Datastore) clazz;
    }

}
