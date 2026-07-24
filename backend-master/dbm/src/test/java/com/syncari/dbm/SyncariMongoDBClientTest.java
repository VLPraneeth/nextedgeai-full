package com.syncari.dbm;

import static org.junit.Assert.assertTrue;

import java.util.List;

import com.syncari.dbm.dbclient.SyncariMongoDBClient;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;


@RunWith(SpringRunner.class)
@SpringBootTest
public class SyncariMongoDBClientTest {

    @Autowired
    SyncariMongoDBClient client;
    
    @Test
    public void getConnectionString() {
        System.out.println(client.getConnectionString());
    }

    @Test
    public void testConnection() {
        client.testConnection("syncaridb");
        client.testConnection("syncari_admin");
    }

    @Test
    public void getCustomerDatabaseNames() {
        List<String> customerDBs = client.getCustomerDatabaseNames();
        System.out.println(customerDBs);
        assertTrue(customerDBs.size() > 0);
    }

    @Test
    public void getCustomerDb() {
        List<String> customerDBs = client.getCustomerDatabaseNames();
        for(String customerDB: customerDBs) {
            client.getCustomerDb(customerDB);
        }
    }

    @Test
    public void executeCommand() {
        String response = client.executeCommand(client.getCustomerDb("syncari_admin"), 
            "{find: 'entityDefinition', filter: { 'apiName': 'account'} }");
        System.out.println(response);
    }

    @Test
    public void executeCommandForEachCustomer() {
        List<String> response = client.executeCommandForEachCustomer("{find: 'entityDefinition', filter: { 'apiName': 'account'} }");
        System.out.println(response);
    }
}
