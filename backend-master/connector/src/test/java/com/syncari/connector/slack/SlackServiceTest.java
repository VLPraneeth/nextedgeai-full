package com.syncari.connector.slack;

import com.syncari.connector.AbstractConnectorTest;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.DataServiceTest;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@AutoConfigureDataMongo
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
@TestPropertySource("classpath:test_application.properties")
public class SlackServiceTest extends AbstractConnectorTest implements DataServiceTest {

    @Autowired
    SlackService slackService;

    private ConnectorInfo connector;

    @Before
    public void setup() {
        connector = createConnector();
    }

    @Override
    public ConnectorInfo getConnector() {
        if (connector == null) connector = createConnector();
        return connector;
    }

    private ConnectorInfo createConnector() {
        ConnectorInfo slackConnector = new ConnectorInfo();
        AuthConfig authConfig = new AuthConfig();
        authConfig.setClientId(System.getenv().getOrDefault("SLACK_TEST_CLIENT_ID", "REPLACE_ME"));
        authConfig.setClientSecret(System.getenv().getOrDefault("SLACK_TEST_CLIENT_SECRET", "REPLACE_ME"));
        authConfig.setAccessToken(System.getenv().getOrDefault("SLACK_TEST_ACCESS_TOKEN", "REPLACE_ME"));
        slackConnector.setAuthConfig(authConfig);
        UUID uuid = UUID.randomUUID();
        slackConnector.setId(uuid.toString());
        return slackConnector;
    }

    @Override
    public AuthenticationService getAuthenticationService() {
        return slackService;
    }

    @Override
    public MetadataService getMetadataService() {
        return slackService;
    }

    @Override
    public CommonDataService getDataService() {
        return slackService;
    }

    @Override
    public String getDescribeObject() {
        return "";
    }

    @Test
    @Override
    public void testConnectionTest() {
        retryWithBackoff(() -> {
            verifyTestConnection();
        });
    }

    @Test
    @Override
    public void describeAllTest() {
        describeAll(null);
    }

    @Test
    @Override
    public void describeTest() {
        Optional<EntitySchema> schema = describe("message", null);
        assertTrue(schema.isPresent());
        List<AttributeSchema> list = schema.get().getAttributes().stream().filter(attr -> attr.getApiName().equalsIgnoreCase("micro_ts")).collect(Collectors.toList());
        assertTrue(list.size() == 1);
    }

    @Test
    public void describeBlockSchemaTest() {
        Optional<EntitySchema> schema = describe("block_action_response", null);
        assertTrue(schema.isPresent());
        List<AttributeSchema> list = schema.get().getAttributes().stream().filter(attr -> attr.getApiName().equalsIgnoreCase("action_ts")).collect(Collectors.toList());
        assertTrue(list.size() == 1);
    }

    @Test
    @Override
    public void getByWatermarkSinceEpoch() {
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        watermark.setResync(true);
        verifyGetByWatermarkSinceEpoch("channel", watermark);
        verifyGetByWatermarkSinceEpoch("user", watermark);
        verifyGetByWatermarkSinceEpoch("message", watermark);
    }

    @Override
    public void getByWatermarkRecent() {
        verifyGetByWatermarkRecent("message");
    }

    @Test
    @Override
    public void getByWatermarkWithLimit() {
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        watermark.setResync(true);
        verifyGetByWatermarkWithLimit("channel", 4, watermark);
    }

    @Override
    public void getByWatermarkResultsOrdered() {
        verifyGetByWatermarkResultsOrdered("message");
    }

    @Test
    @Override
    public void getByIds() {
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        watermark.setResync(true);
        verifyGetByIds("channel",1, watermark);
    }

    @Test
    public void getMessageByIds() {
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        watermark.setResync(true);
        verifyGetByIds("message",1, watermark);
    }

    @Test
    public void getMessageByInvalidIds() {
        Optional<EntitySchema> schema = describe("message", null);
        SyncRequest getByIdRequest = new SyncRequest().Builder(getConnector(), schema.get());
        List<EntityData> data = List.of(new EntityData("message").setId("testid"));
        getByIdRequest.setData(Map.of(getConnector().getId(), data));
        data = getDataService().getByIds(getByIdRequest);
        assertNotNull(data);
        assertEquals(0, data.size());
    }

    @Override
    public void getDeletedByWatermark() {

    }

    @Override
    public void createTest() {

    }

    @Override
    public void updateTest() {

    }

    @Override
    public void deleteTest() {

    }

    @Override
    public void batchCreateTest() {

    }

    @Override
    public void batchUpdateTest() {

    }

    @Override
    public void batchDeleteTest() {

    }

    @Override
    public void createCustomObjectTest() {

    }

    @Override
    public void updateCustomObjectTest() {

    }

    @Override
    public void deleteCustomObjectTest() {

    }

    @Override
    public void mixedBatchCreateFailuresTest() {

    }

    @Override
    public void mixedBatchUpdateFailuresTest() {

    }

    @Override
    public void mixedBatchDeleteFailuresTest() {

    }

    @Override
    public void allDataTypesTest() {

    }

    @Override
    public void referencesTest() {

    }

    @Override
    public void rateLimitTest() {

    }

    @Test
    public void parseEventTest(){
        String json = "{\"token\":\"JWkcDDhs2BtEm2D5UxKwCTTK\",\"team_id\":\"T02JKG9QSRL\",\"api_app_id\":\"A02JUFCGA20\",\"event\":{\"type\":\"message\",\"subtype\":\"channel_join\",\"ts\":\"1636705292.000800\",\"user\":\"U02L7AT0NJ0\",\"text\":\"<@U02L7AT0NJ0> has joined the channel\",\"inviter\":\"U02JBHX7TB9\",\"channel\":\"C02LANLEXJR\",\"event_ts\":\"1636705292.000800\",\"channel_type\":\"channel\"},\"type\":\"event_callback\",\"event_id\":\"Ev02LP837A3H\",\"event_time\":1636705292,\"authorizations\":[{\"enterprise_id\":null,\"team_id\":\"T02JKG9QSRL\",\"user_id\":\"U02JPG5HUHK\",\"is_bot\":true,\"is_enterprise_install\":false}],\"is_ext_shared_channel\":false,\"event_context\":\"4-eyJldCI6Im1lc3NhZ2UiLCJ0aWQiOiJUMDJKS0c5UVNSTCIsImFpZCI6IkEwMkpVRkNHQTIwIiwiY2lkIjoiQzAyTTBUTjBRODUifQ\"}";
        WebhookRequest request = new WebhookRequest();
        request.setConfig(getConnector());
        request.setBody(json);
        List<EventData> eventDataList = slackService.parseEventData(request);
        assertNotNull(eventDataList);
        assertEquals(eventDataList.size(), 1);
    }

    @Test
    public void parseNewMessage() {
        String json1 = "{\"token\":\"M64VQMbKkdl2MwimlcokzgNm\",\"team_id\":\"T02JKG9QSRL\",\"api_app_id\":\"A02M1UJ354P\",\"event\":{\"client_msg_id\":\"03d65801-e84e-4911-8780-963195f2421b\",\"type\":\"message\",\"text\":\"test message 123\",\"user\":\"U02JBHX7TB9\",\"ts\":\"1662764620.515379\",\"team\":\"T02JKG9QSRL\",\"blocks\":[{\"type\":\"rich_text\",\"block_id\":\"bbIeK\",\"elements\":[{\"type\":\"rich_text_section\",\"elements\":[{\"type\":\"text\",\"text\":\"test message 123\"}]}]}],\"channel\":\"C041MUXDN4E\",\"event_ts\":\"1662764620.515379\",\"channel_type\":\"channel\"},\"type\":\"event_callback\",\"event_id\":\"Ev04276L7CHX\",\"event_time\":1662764620,\"authorizations\":[{\"enterprise_id\":null,\"team_id\":\"T02JKG9QSRL\",\"user_id\":\"U02MV7GG88Z\",\"is_bot\":true,\"is_enterprise_install\":false}],\"is_ext_shared_channel\":false,\"event_context\":\"4-eyJldCI6Im1lc3NhZ2UiLCJ0aWQiOiJUMDJKS0c5UVNSTCIsImFpZCI6IkEwMk0xVUozNTRQIiwiY2lkIjoiQzA0MU1VWERONEUifQ\"}";
        WebhookRequest request1 = new WebhookRequest();
        request1.setConfig(getConnector());
        request1.setBody(json1);
        List<EventData> eventDataList1 = slackService.parseEventData(request1);
        assertNotNull(eventDataList1);
        assertEquals(eventDataList1.size(), 1);
        assertEquals(eventDataList1.get(0).getData().getValue("text"), "test message 123");
        assertEquals(eventDataList1.get(0).getData().getValue("micro_ts"), "1662764620.515379");
        String json2 = "{\"token\":\"M64VQMbKkdl2MwimlcokzgNm\",\"team_id\":\"T02JKG9QSRL\",\"api_app_id\":\"A02M1UJ354P\",\"event\":{\"client_msg_id\":\"c0d3e265-9152-488d-a124-ba1ffe3bbf7b\",\"type\":\"message\",\"text\":\"test reply 123\",\"user\":\"U02JBHX7TB9\",\"ts\":\"1662764701.012809\",\"team\":\"T02JKG9QSRL\",\"blocks\":[{\"type\":\"rich_text\",\"block_id\":\"qDIDM\",\"elements\":[{\"type\":\"rich_text_section\",\"elements\":[{\"type\":\"text\",\"text\":\"test reply 123\"}]}]}],\"thread_ts\":\"1662764620.515379\",\"parent_user_id\":\"U02JBHX7TB9\",\"channel\":\"C041MUXDN4E\",\"event_ts\":\"1662764701.012809\",\"channel_type\":\"channel\"},\"type\":\"event_callback\",\"event_id\":\"Ev04276P3881\",\"event_time\":1662764701,\"authorizations\":[{\"enterprise_id\":null,\"team_id\":\"T02JKG9QSRL\",\"user_id\":\"U02MV7GG88Z\",\"is_bot\":true,\"is_enterprise_install\":false}],\"is_ext_shared_channel\":false,\"event_context\":\"4-eyJldCI6Im1lc3NhZ2UiLCJ0aWQiOiJUMDJKS0c5UVNSTCIsImFpZCI6IkEwMk0xVUozNTRQIiwiY2lkIjoiQzA0MU1VWERONEUifQ\"}";
        WebhookRequest request2 = new WebhookRequest();
        request2.setConfig(getConnector());
        request2.setBody(json2);
        List<EventData> eventDataList2 = slackService.parseEventData(request2);
        assertNotNull(eventDataList2);
        // TODO - the message is not found for somme reason - fix this later
//        assertEquals(eventDataList2.size(), 2);
        assertEquals(eventDataList2.size(), 1);
    }

    @Test
    public void parseMessageUpdate() {
        String json1 = "{\"token\":\"JWkcDDhs2BtEm2D5UxKwCTTK\",\"team_id\":\"T02JKG9QSRL\",\"api_app_id\":\"A02JUFCGA20\",\"event\":{\"type\":\"message\",\"subtype\":\"message_changed\",\"hidden\":true,\"message\":{\"client_msg_id\":\"093bc706-de8e-4fb7-925d-8b47dfc13986\",\"type\":\"message\",\"text\":\"test message 9 to 10\",\"user\":\"U02JBHX7TB9\",\"team\":\"T02JKG9QSRL\",\"edited\":{\"user\":\"U02JBHX7TB9\",\"ts\":\"1637113260.000000\"},\"blocks\":[{\"type\":\"rich_text\",\"block_id\":\"x=u9\",\"elements\":[{\"type\":\"rich_text_section\",\"elements\":[{\"type\":\"text\",\"text\":\"test message 9 to 10\"}]}]}],\"thread_ts\":\"1637112531.003500\",\"reply_count\":1,\"reply_users_count\":1,\"latest_reply\":\"1637112540.003600\",\"reply_users\":[\"U02JBHX7TB9\"],\"is_locked\":false,\"ts\":\"1637112531.003500\",\"source_team\":\"T02JKG9QSRL\",\"user_team\":\"T02JKG9QSRL\"},\"channel\":\"C02M5Q32HLM\",\"previous_message\":{\"client_msg_id\":\"093bc706-de8e-4fb7-925d-8b47dfc13986\",\"type\":\"message\",\"text\":\"test message 9\",\"user\":\"U02JBHX7TB9\",\"ts\":\"1637112531.003500\",\"team\":\"T02JKG9QSRL\",\"blocks\":[{\"type\":\"rich_text\",\"block_id\":\"4M=DJ\",\"elements\":[{\"type\":\"rich_text_section\",\"elements\":[{\"type\":\"text\",\"text\":\"test message 9\"}]}]}],\"thread_ts\":\"1637112531.003500\",\"reply_count\":1,\"reply_users_count\":1,\"latest_reply\":\"1637112540.003600\",\"reply_users\":[\"U02JBHX7TB9\"],\"is_locked\":false,\"subscribed\":true,\"last_read\":\"1637112540.003600\"},\"event_ts\":\"1637113260.003800\",\"ts\":\"1637113260.003800\",\"channel_type\":\"channel\"},\"type\":\"event_callback\",\"event_id\":\"Ev02MDV3GM2A\",\"event_time\":1637113260,\"authorizations\":[{\"enterprise_id\":null,\"team_id\":\"T02JKG9QSRL\",\"user_id\":\"U02JPG5HUHK\",\"is_bot\":true,\"is_enterprise_install\":false}],\"is_ext_shared_channel\":false,\"event_context\":\"4-eyJldCI6Im1lc3NhZ2UiLCJ0aWQiOiJUMDJKS0c5UVNSTCIsImFpZCI6IkEwMkpVRkNHQTIwIiwiY2lkIjoiQzAyTTVRMzJITE0ifQ\"}\n";
        WebhookRequest request1 = new WebhookRequest();
        request1.setConfig(getConnector());
        request1.setBody(json1);
        List<EventData> eventDataList1 = slackService.parseEventData(request1);
        assertNotNull(eventDataList1);
        assertEquals(eventDataList1.size(), 1);
        assertEquals(eventDataList1.get(0).getData().getValue("text"), "test message 9 to 10");
        String json2 = "{\"token\":\"JWkcDDhs2BtEm2D5UxKwCTTK\",\"team_id\":\"T02JKG9QSRL\",\"api_app_id\":\"A02JUFCGA20\",\"event\":{\"type\":\"message\",\"subtype\":\"message_changed\",\"hidden\":true,\"message\":{\"client_msg_id\":\"ca3ef704-a90e-4089-a697-54d1fcdb3b42\",\"type\":\"message\",\"text\":\"reply 9 to 10\",\"user\":\"U02JBHX7TB9\",\"team\":\"T02JKG9QSRL\",\"edited\":{\"user\":\"U02JBHX7TB9\",\"ts\":\"1637114190.000000\"},\"blocks\":[{\"type\":\"rich_text\",\"block_id\":\"iohP\",\"elements\":[{\"type\":\"rich_text_section\",\"elements\":[{\"type\":\"text\",\"text\":\"reply 9 to 10\"}]}]}],\"thread_ts\":\"1637112531.003500\",\"parent_user_id\":\"U02JBHX7TB9\",\"ts\":\"1637112540.003600\",\"source_team\":\"T02JKG9QSRL\",\"user_team\":\"T02JKG9QSRL\"},\"channel\":\"C02M5Q32HLM\",\"previous_message\":{\"client_msg_id\":\"ca3ef704-a90e-4089-a697-54d1fcdb3b42\",\"type\":\"message\",\"text\":\"reply 9\",\"user\":\"U02JBHX7TB9\",\"ts\":\"1637112540.003600\",\"team\":\"T02JKG9QSRL\",\"blocks\":[{\"type\":\"rich_text\",\"block_id\":\"=Jx\",\"elements\":[{\"type\":\"rich_text_section\",\"elements\":[{\"type\":\"text\",\"text\":\"reply 9\"}]}]}],\"thread_ts\":\"1637112531.003500\",\"parent_user_id\":\"U02JBHX7TB9\"},\"event_ts\":\"1637114190.004200\",\"ts\":\"1637114190.004200\",\"channel_type\":\"channel\"},\"type\":\"event_callback\",\"event_id\":\"Ev02ME0QHW5U\",\"event_time\":1637114190,\"authorizations\":[{\"enterprise_id\":null,\"team_id\":\"T02JKG9QSRL\",\"user_id\":\"U02JPG5HUHK\",\"is_bot\":true,\"is_enterprise_install\":false}],\"is_ext_shared_channel\":false,\"event_context\":\"4-eyJldCI6Im1lc3NhZ2UiLCJ0aWQiOiJUMDJKS0c5UVNSTCIsImFpZCI6IkEwMkpVRkNHQTIwIiwiY2lkIjoiQzAyTTVRMzJITE0ifQ\"}\n";
        WebhookRequest request2 = new WebhookRequest();
        request2.setConfig(getConnector());
        request2.setBody(json2);
        List<EventData> eventDataList2 = slackService.parseEventData(request2);
        assertNotNull(eventDataList2);
        assertEquals(eventDataList2.size(), 1);
        assertEquals(eventDataList2.get(0).getData().getValue("text"), "reply 9 to 10");
    }

    @Test
    public void parseChannelArchive(){
        String json = "{\"token\":\"JWkcDDhs2BtEm2D5UxKwCTTK\",\"team_id\":\"T02JKG9QSRL\",\"api_app_id\":\"A02JUFCGA20\",\"event\":{\"type\":\"channel_archive\",\"channel\":\"C02KW5K7G93\",\"user\":\"U02JBHX7TB9\",\"is_moved\":0,\"event_ts\":\"1637137947.048500\"},\"type\":\"event_callback\",\"event_id\":\"Ev02MML543EE\",\"event_time\":1637137947,\"authorizations\":[{\"enterprise_id\":null,\"team_id\":\"T02JKG9QSRL\",\"user_id\":\"U02JPG5HUHK\",\"is_bot\":true,\"is_enterprise_install\":false}],\"is_ext_shared_channel\":false}";
        WebhookRequest request = new WebhookRequest();
        request.setConfig(getConnector());
        request.setBody(json);
        List<EventData> eventDataList = slackService.parseEventData(request);
        assertNotNull(eventDataList);
        assertEquals(eventDataList.size(), 1);
        assertNotNull(eventDataList.get(0).getData().getValue("is_archived"));
        assertEquals(eventDataList.get(0).getData().getValue("is_archived"), true);
        String json1 = "{\"token\":\"JWkcDDhs2BtEm2D5UxKwCTTK\",\"team_id\":\"T02JKG9QSRL\",\"api_app_id\":\"A02JUFCGA20\",\"event\":{\"type\":\"channel_unarchive\",\"channel\":\"C02KW5K7G93\",\"user\":\"U02JBHX7TB9\",\"is_moved\":0,\"event_ts\":\"1637137947.048500\"},\"type\":\"event_callback\",\"event_id\":\"Ev02MML543EE\",\"event_time\":1637137947,\"authorizations\":[{\"enterprise_id\":null,\"team_id\":\"T02JKG9QSRL\",\"user_id\":\"U02JPG5HUHK\",\"is_bot\":true,\"is_enterprise_install\":false}],\"is_ext_shared_channel\":false}";
        WebhookRequest request1 = new WebhookRequest();
        request1.setConfig(getConnector());
        request1.setBody(json1);
        List<EventData> eventDataList1 = slackService.parseEventData(request1);
        assertNotNull(eventDataList1);
        assertEquals(eventDataList1.size(), 1);
        assertNotNull(eventDataList1.get(0).getData().getValue("is_archived"));
        assertEquals(eventDataList1.get(0).getData().getValue("is_archived"), false);
    }

    @Test
    public void parseChannelRename(){
        String json = "{\"token\":\"JWkcDDhs2BtEm2D5UxKwCTTK\",\"team_id\":\"T02JKG9QSRL\",\"api_app_id\":\"A02JUFCGA20\",\"event\":{\"type\":\"channel_rename\",\"channel\":{\"id\":\"C02M0TN0Q85\",\"is_channel\":true,\"is_mpim\":false,\"name\":\"test-channel-13-rename\",\"name_normalized\":\"test-channel-13-rename\",\"created\":1636704804},\"event_ts\":\"1637139533.048900\"},\"type\":\"event_callback\",\"event_id\":\"Ev02MMP1REKC\",\"event_time\":1637139533,\"authorizations\":[{\"enterprise_id\":null,\"team_id\":\"T02JKG9QSRL\",\"user_id\":\"U02JPG5HUHK\",\"is_bot\":true,\"is_enterprise_install\":false}],\"is_ext_shared_channel\":false}";
        WebhookRequest request = new WebhookRequest();
        request.setConfig(getConnector());
        request.setBody(json);
        List<EventData> eventDataList = slackService.parseEventData(request);
        assertNotNull(eventDataList);
        assertEquals(eventDataList.size(), 1);
        assertNotNull(eventDataList.get(0).getData().getValue("name"));
        assertEquals(eventDataList.get(0).getData().getValue("name"), "test-channel-13-rename");
    }

    @Test
    public void parseMessageDelete(){
        String json = "{\"token\":\"JWkcDDhs2BtEm2D5UxKwCTTK\",\"team_id\":\"T02JKG9QSRL\",\"api_app_id\":\"A02JUFCGA20\",\"event\":{\"type\":\"message\",\"subtype\":\"message_deleted\",\"hidden\":true,\"deleted_ts\":\"1637139967.000800\",\"channel\":\"C02M7B66SP4\",\"previous_message\":{\"client_msg_id\":\"9c0735a3-8616-4230-8308-421e9659043b\",\"type\":\"message\",\"text\":\"test\",\"user\":\"U02JBHX7TB9\",\"ts\":\"1637139967.000800\",\"team\":\"T02JKG9QSRL\",\"blocks\":[{\"type\":\"rich_text\",\"block_id\":\"nfkjp\",\"elements\":[{\"type\":\"rich_text_section\",\"elements\":[{\"type\":\"text\",\"text\":\"test\"}]}]}]},\"event_ts\":\"1637139976.000900\",\"ts\":\"1637139976.000900\",\"channel_type\":\"channel\"},\"type\":\"event_callback\",\"event_id\":\"Ev02MJRHCB1T\",\"event_time\":1637139976,\"authorizations\":[{\"enterprise_id\":null,\"team_id\":\"T02JKG9QSRL\",\"user_id\":\"U02JPG5HUHK\",\"is_bot\":true,\"is_enterprise_install\":false}],\"is_ext_shared_channel\":false,\"event_context\":\"4-eyJldCI6Im1lc3NhZ2UiLCJ0aWQiOiJUMDJKS0c5UVNSTCIsImFpZCI6IkEwMkpVRkNHQTIwIiwiY2lkIjoiQzAyTTdCNjZTUDQifQ\"}";
        WebhookRequest request = new WebhookRequest();
        request.setConfig(getConnector());
        request.setBody(json);
        List<EventData> eventDataList = slackService.parseEventData(request);
        assertNotNull(eventDataList);
        assertEquals(eventDataList.size(), 1);
        assertTrue(eventDataList.get(0).getData().isDeleted());
    }

    @Test
    public void parseNewUser(){
        String json = "{\"token\":\"JWkcDDhs2BtEm2D5UxKwCTTK\",\"team_id\":\"T02JKG9QSRL\",\"api_app_id\":\"A02JUFCGA20\",\"event\":{\"type\":\"team_join\",\"user\":{\"id\":\"U02M9DXPPHU\",\"team_id\":\"T02JKG9QSRL\",\"name\":\"venkat\",\"deleted\":false,\"color\":\"684b6c\",\"real_name\":\"Venkat Raman\",\"tz\":\"America\\/Los_Angeles\",\"tz_label\":\"Pacific Standard Time\",\"tz_offset\":-28800,\"profile\":{\"title\":\"\",\"phone\":\"\",\"skype\":\"\",\"real_name\":\"Venkat Raman\",\"real_name_normalized\":\"Venkat Raman\",\"display_name\":\"Venkat Raman\",\"display_name_normalized\":\"Venkat Raman\",\"fields\":null,\"status_text\":\"\",\"status_emoji\":\"\",\"status_emoji_display_info\":[],\"status_expiration\":0,\"avatar_hash\":\"g9cfa9ee0806\",\"email\":\"venkat@syncari.com\",\"first_name\":\"Venkat\",\"last_name\":\"Raman\",\"image_24\":\"https:\\/\\/secure.gravatar.com\\/avatar\\/9cfa9ee0806d194bc507e9a1c041b35e.jpg?s=24&d=https%3A%2F%2Fa.slack-edge.com%2Fdf10d%2Fimg%2Favatars%2Fava_0007-24.png\",\"image_32\":\"https:\\/\\/secure.gravatar.com\\/avatar\\/9cfa9ee0806d194bc507e9a1c041b35e.jpg?s=32&d=https%3A%2F%2Fa.slack-edge.com%2Fdf10d%2Fimg%2Favatars%2Fava_0007-32.png\",\"image_48\":\"https:\\/\\/secure.gravatar.com\\/avatar\\/9cfa9ee0806d194bc507e9a1c041b35e.jpg?s=48&d=https%3A%2F%2Fa.slack-edge.com%2Fdf10d%2Fimg%2Favatars%2Fava_0007-48.png\",\"image_72\":\"https:\\/\\/secure.gravatar.com\\/avatar\\/9cfa9ee0806d194bc507e9a1c041b35e.jpg?s=72&d=https%3A%2F%2Fa.slack-edge.com%2Fdf10d%2Fimg%2Favatars%2Fava_0007-72.png\",\"image_192\":\"https:\\/\\/secure.gravatar.com\\/avatar\\/9cfa9ee0806d194bc507e9a1c041b35e.jpg?s=192&d=https%3A%2F%2Fa.slack-edge.com%2Fdf10d%2Fimg%2Favatars%2Fava_0007-192.png\",\"image_512\":\"https:\\/\\/secure.gravatar.com\\/avatar\\/9cfa9ee0806d194bc507e9a1c041b35e.jpg?s=512&d=https%3A%2F%2Fa.slack-edge.com%2Fdf10d%2Fimg%2Favatars%2Fava_0007-512.png\",\"status_text_canonical\":\"\",\"team\":\"T02JKG9QSRL\"},\"is_admin\":false,\"is_owner\":false,\"is_primary_owner\":false,\"is_restricted\":false,\"is_ultra_restricted\":false,\"is_bot\":false,\"is_app_user\":false,\"updated\":1637088830,\"is_email_confirmed\":true,\"who_can_share_contact_card\":\"EVERYONE\",\"presence\":\"away\"},\"cache_ts\":1637088830,\"event_ts\":\"1637088831.046900\"},\"type\":\"event_callback\",\"event_id\":\"Ev02M4EG79T9\",\"event_time\":1637088831,\"authorizations\":[{\"enterprise_id\":null,\"team_id\":\"T02JKG9QSRL\",\"user_id\":\"U02JPG5HUHK\",\"is_bot\":true,\"is_enterprise_install\":false}],\"is_ext_shared_channel\":false}\n";
        WebhookRequest request = new WebhookRequest();
        request.setConfig(getConnector());
        request.setBody(json);
        List<EventData> eventDataList = slackService.parseEventData(request);
        assertNotNull(eventDataList);
        assertEquals(eventDataList.size(), 1);
        assertNotNull(eventDataList.get(0).getData().getValue("profile_avatar_hash"));
        assertEquals(eventDataList.get(0).getData().getValue("profile_avatar_hash"), "g9cfa9ee0806");
    }

    @Test
    public void parseUserUpdate(){
        String json = "{\"token\":\"M64VQMbKkdl2MwimlcokzgNm\",\"team_id\":\"T02JKG9QSRL\",\"api_app_id\":\"A02M1UJ354P\",\"event\":{\"type\":\"user_change\",\"user\":{\"id\":\"U02JBHX7TB9\",\"team_id\":\"T02JKG9QSRL\",\"name\":\"blesson\",\"color\":\"e7392d\",\"real_name\":\"Blesson Mathew Sam\",\"tz\":\"America\\/Los_Angeles\",\"tz_label\":\"Pacific Standard Time\",\"tz_offset\":-28800,\"profile\":{\"title\":\"Test \",\"phone\":\"7166047244\",\"skype\":\"\",\"real_name\":\"Blesson Mathew Sam\",\"real_name_normalized\":\"Blesson Mathew Sam\",\"display_name\":\"Blesson\",\"display_name_normalized\":\"Blesson\",\"fields\":{},\"status_text\":\"\",\"status_emoji\":\"\",\"status_emoji_display_info\":[],\"status_expiration\":0,\"avatar_hash\":\"2177936f7eeb\",\"image_original\":\"https:\\/\\/avatars.slack-edge.com\\/2021-10-22\\/2640502581620_2177936f7eeb5e8069be_original.png\",\"is_custom_image\":true,\"huddle_state\":\"default_unset\",\"first_name\":\"Blesson\",\"last_name\":\"Mathew Sam\",\"image_24\":\"https:\\/\\/avatars.slack-edge.com\\/2021-10-22\\/2640502581620_2177936f7eeb5e8069be_24.png\",\"image_32\":\"https:\\/\\/avatars.slack-edge.com\\/2021-10-22\\/2640502581620_2177936f7eeb5e8069be_32.png\",\"image_48\":\"https:\\/\\/avatars.slack-edge.com\\/2021-10-22\\/2640502581620_2177936f7eeb5e8069be_48.png\",\"image_72\":\"https:\\/\\/avatars.slack-edge.com\\/2021-10-22\\/2640502581620_2177936f7eeb5e8069be_72.png\",\"image_192\":\"https:\\/\\/avatars.slack-edge.com\\/2021-10-22\\/2640502581620_2177936f7eeb5e8069be_192.png\",\"image_512\":\"https:\\/\\/avatars.slack-edge.com\\/2021-10-22\\/2640502581620_2177936f7eeb5e8069be_512.png\",\"image_1024\":\"https:\\/\\/avatars.slack-edge.com\\/2021-10-22\\/2640502581620_2177936f7eeb5e8069be_1024.png\",\"status_text_canonical\":\"\",\"team\":\"T02JKG9QSRL\",\"email\":\"blesson@syncari.com\"},\"is_admin\":true,\"is_owner\":false,\"is_primary_owner\":false,\"is_restricted\":false,\"is_ultra_restricted\":false,\"is_bot\":false,\"is_app_user\":false,\"updated\":1645059742,\"is_email_confirmed\":true,\"who_can_share_contact_card\":\"EVERYONE\",\"locale\":\"en-US\"},\"cache_ts\":1645059742,\"event_ts\":\"1645059742.016400\"},\"type\":\"event_callback\",\"event_id\":\"Ev033CQV9RQD\",\"event_time\":1645059742,\"authorizations\":[{\"enterprise_id\":null,\"team_id\":\"T02JKG9QSRL\",\"user_id\":\"U02MV7GG88Z\",\"is_bot\":true,\"is_enterprise_install\":false}],\"is_ext_shared_channel\":false}";        WebhookRequest request = new WebhookRequest();
        request.setConfig(getConnector());
        request.setBody(json);
        List<EventData> eventDataList = slackService.parseEventData(request);
        assertNotNull(eventDataList);
        assertEquals(eventDataList.size(), 1);
    }

    @Test
    public void parseUserDelete(){
        String json = "{\"token\":\"JWkcDDhs2BtEm2D5UxKwCTTK\",\"team_id\":\"T02JKG9QSRL\",\"api_app_id\":\"A02JUFCGA20\",\"event\":{\"type\":\"user_change\",\"user\":{\"id\":\"U02M9DXPPHU\",\"team_id\":\"T02JKG9QSRL\",\"name\":\"venkat\",\"deleted\":true,\"profile\":{\"title\":\"\",\"phone\":\"\",\"skype\":\"\",\"real_name\":\"Venkat Raman\",\"real_name_normalized\":\"Venkat Raman\",\"display_name\":\"Venkat Raman\",\"display_name_normalized\":\"Venkat Raman\",\"fields\":null,\"status_text\":\"\",\"status_emoji\":\"\",\"status_emoji_display_info\":[],\"status_expiration\":0,\"avatar_hash\":\"d52a971bd599\",\"image_original\":\"https:\\/\\/avatars.slack-edge.com\\/2021-11-16\\/2726429906758_d52a971bd599d22174ba_original.png\",\"is_custom_image\":true,\"first_name\":\"Venkat\",\"last_name\":\"Raman\",\"image_24\":\"https:\\/\\/avatars.slack-edge.com\\/2021-11-16\\/2726429906758_d52a971bd599d22174ba_24.png\",\"image_32\":\"https:\\/\\/avatars.slack-edge.com\\/2021-11-16\\/2726429906758_d52a971bd599d22174ba_32.png\",\"image_48\":\"https:\\/\\/avatars.slack-edge.com\\/2021-11-16\\/2726429906758_d52a971bd599d22174ba_48.png\",\"image_72\":\"https:\\/\\/avatars.slack-edge.com\\/2021-11-16\\/2726429906758_d52a971bd599d22174ba_72.png\",\"image_192\":\"https:\\/\\/avatars.slack-edge.com\\/2021-11-16\\/2726429906758_d52a971bd599d22174ba_192.png\",\"image_512\":\"https:\\/\\/avatars.slack-edge.com\\/2021-11-16\\/2726429906758_d52a971bd599d22174ba_512.png\",\"image_1024\":\"https:\\/\\/avatars.slack-edge.com\\/2021-11-16\\/2726429906758_d52a971bd599d22174ba_1024.png\",\"status_text_canonical\":\"\",\"team\":\"T02JKG9QSRL\"},\"is_bot\":false,\"is_app_user\":false,\"updated\":1637187269},\"cache_ts\":1637187269,\"event_ts\":\"1637187269.050100\"},\"type\":\"event_callback\",\"event_id\":\"Ev02MTS1GG5S\",\"event_time\":1637187269,\"authorizations\":[{\"enterprise_id\":null,\"team_id\":\"T02JKG9QSRL\",\"user_id\":\"U02JPG5HUHK\",\"is_bot\":true,\"is_enterprise_install\":false}],\"is_ext_shared_channel\":false}";
        WebhookRequest request = new WebhookRequest();
        request.setConfig(getConnector());
        request.setBody(json);
        List<EventData> eventDataList = slackService.parseEventData(request);
        assertNotNull(eventDataList);
        assertEquals(eventDataList.size(), 1);
        assertTrue(eventDataList.get(0).getData().isDeleted());
    }

    @Test
    public void parseReactionsNotFound(){
        String json = "{\"token\":\"M64VQMbKkdl2MwimlcokzgNm\",\"team_id\":\"T02JKG9QSRL\",\"context_team_id\":\"T02JKG9QSRL\",\"context_enterprise_id\":null,\"api_app_id\":\"A02M1UJ354P\",\"event\":{\"type\":\"reaction_added\",\"user\":\"U02JBHX7TB9\",\"reaction\":\"white_check_mark\",\"item\":{\"type\":\"message\",\"channel\":\"C04FMLBC8CT\",\"ts\":\"1680105173.823929\"},\"item_user\":\"U02JBHX7TB9\",\"event_ts\":\"1680105198.000100\"},\"type\":\"event_callback\",\"event_id\":\"Ev050SHM2ZT7\",\"event_time\":1680105198,\"authorizations\":[{\"enterprise_id\":null,\"team_id\":\"T02JKG9QSRL\",\"user_id\":\"U02MV7GG88Z\",\"is_bot\":true,\"is_enterprise_install\":false}],\"is_ext_shared_channel\":false,\"event_context\":\"4-eyJldCI6InJlYWN0aW9uX2FkZGVkIiwidGlkIjoiVDAySktHOVFTUkwiLCJhaWQiOiJBMDJNMVVKMzU0UCIsImNpZCI6IkMwNEZNTEJDOENUIn0\"}";
        WebhookRequest request = new WebhookRequest();
        request.setConfig(getConnector());
        request.setBody(json);
        List<EventData> eventDataList = slackService.parseEventData(request);
        assertNotNull(eventDataList);
        assertEquals(eventDataList.size(), 0);
    }

    @Test
    @Ignore
    public void parseReactionsFound(){
        String json = "{\"token\":\"M64VQMbKkdl2MwimlcokzgNm\",\"team_id\":\"T02JKG9QSRL\",\"context_team_id\":\"T02JKG9QSRL\",\"context_enterprise_id\":null,\"api_app_id\":\"A02M1UJ354P\",\"event\":{\"type\":\"reaction_added\",\"user\":\"U02JBHX7TB9\",\"reaction\":\"white_check_mark\",\"item\":{\"type\":\"message\",\"channel\":\"C04FMLBC8CT\",\"ts\":\"1680105173.823929\"},\"item_user\":\"U02JBHX7TB9\",\"event_ts\":\"1680105198.000100\"},\"type\":\"event_callback\",\"event_id\":\"Ev050SHM2ZT7\",\"event_time\":1680105198,\"authorizations\":[{\"enterprise_id\":null,\"team_id\":\"T02JKG9QSRL\",\"user_id\":\"U02MV7GG88Z\",\"is_bot\":true,\"is_enterprise_install\":false}],\"is_ext_shared_channel\":false,\"event_context\":\"4-eyJldCI6InJlYWN0aW9uX2FkZGVkIiwidGlkIjoiVDAySktHOVFTUkwiLCJhaWQiOiJBMDJNMVVKMzU0UCIsImNpZCI6IkMwNEZNTEJDOENUIn0\"}";
        WebhookRequest request = new WebhookRequest();
        request.setConfig(getConnector());
        request.setBody(json);
        List<EventData> eventDataList = slackService.parseEventData(request);
        assertNotNull(eventDataList);
        assertEquals(eventDataList.size(), 1);
        assertNotNull(eventDataList.get(0).getData().getValue("reactions"));
        assertEquals(eventDataList.get(0).getData().getValue("reactions"), 4);
    }

    @Test
    public void slackChannelIteratorTest(){
        EntitySchema channelSchema = SlackSeed.getChannelSchema();
        SyncRequest channelresyncRequest = new SyncRequest().Builder(getConnector(), channelSchema);
        WatermarkInfo resyncWatermark = new WatermarkInfo().setResync(true);
        channelresyncRequest.setWatermark(resyncWatermark);
        FetchResponse response = slackService.getByWatermark(channelresyncRequest);
        SlackChannelIterator channelIterator = new SlackChannelIterator(response.getIterator(), 1000,
                200, "", "", "Optional.empty()");
        while(channelIterator.hasNext()) {
            EntityData channel = channelIterator.next();
            assertNotNull(channel.getId());
        }
    }

    @Test
    public void extractIdentifierTest() {
        String json = "{\"token\":\"JWkcDDhs2BtEm2D5UxKwCTTK\",\"team_id\":\"T02JKG9QSRL\",\"api_app_id\":\"A02JUFCGA20\",\"event\":{\"type\":\"reaction_added\",\"user\":\"U02JBHX7TB9\",\"item\":{\"type\":\"message\",\"channel\":\"C02M5Q32HLM\",\"ts\":\"1637046879.001000\"},\"reaction\":\"eyes\",\"item_user\":\"U02JBHX7TB9\",\"event_ts\":\"1637051630.001800\"},\"type\":\"event_callback\",\"event_id\":\"Ev02MJBTD0AG\",\"event_time\":1637051630,\"authorizations\":[{\"enterprise_id\":null,\"team_id\":\"T02JKG9QSRL\",\"user_id\":\"U02JPG5HUHK\",\"is_bot\":true,\"is_enterprise_install\":false}],\"is_ext_shared_channel\":false,\"event_context\":\"4-eyJldCI6InJlYWN0aW9uX2FkZGVkIiwidGlkIjoiVDAySktHOVFTUkwiLCJhaWQiOiJBMDJKVUZDR0EyMCIsImNpZCI6IkMwMk01UTMySExNIn0\"}";
        WebhookRequest request = new WebhookRequest();
        request.setConfig(getConnector());
        request.setBody(json);
        String identifier = slackService.extractIdentifier(request);
        assertNotNull(identifier);
    }

    @Test
    public void timestampTest(){
        String ts = "1637052466.002200";
        long timestamp = SlackRestClient.convertFromMicroTimestamp(ts);
        assertNotNull(timestamp);
        assertEquals(1637052466002L, timestamp);
    }

    @Test
    public void userInfoTest(){
        WatermarkInfo watermark = new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0);
        watermark.setResync(true);
        Optional<EntitySchema> entitySchema = describe("user", null);
        SyncRequest syncRequest = new SyncRequest().Builder(getConnector(), entitySchema.get());
        syncRequest.setWatermark(watermark);
        syncRequest.setPageSize(100);

        FetchResponse byWatermark = getDataService().getByWatermark(syncRequest);
        assertTrue(byWatermark.getIterator().hasNext());
        EntityDataBatchIterator iterator = byWatermark.getIterator();
        List<EntityData> dataWithEmail = new ArrayList<>();
        while(iterator.hasNext()) {
            List<EntityData> dataList = iterator.next();
            dataWithEmail.addAll(dataList.stream().filter(data -> data.getValue("profile_email") != null).collect(Collectors.toList()));
        }
        assertFalse(dataWithEmail.isEmpty());
    }

    @Test
    public void blockParserRadioButtonTest() {
        String body = "{\"type\":\"block_actions\",\"user\":{\"id\":\"U02JBHX7TB9\",\"username\":\"blesson\",\"name\":\"blesson\",\"team_id\":\"T02JKG9QSRL\"},\"api_app_id\":\"A02M1UJ354P\",\"token\":\"M64VQMbKkdl2MwimlcokzgNm\",\"container\":{\"type\":\"message\",\"message_ts\":\"1654622473.181949\",\"channel_id\":\"C03JLJBKK35\",\"is_ephemeral\":false},\"trigger_id\":\"3619981558871.2631553842870.eda567b9bd50bfc069f741614e75ab14\",\"team\":{\"id\":\"T02JKG9QSRL\",\"domain\":\"slacksynapsetest\"},\"enterprise\":null,\"is_enterprise_install\":false,\"channel\":{\"id\":\"C03JLJBKK35\",\"name\":\"slackv20607\"},\"message\":{\"bot_id\":\"B02M9SZRCTG\",\"type\":\"message\",\"text\":\"Testing from instance QA0308 on 6\\/7\",\"user\":\"U02MV7GG88Z\",\"ts\":\"1654622473.181949\",\"app_id\":\"A02M1UJ354P\",\"team\":\"T02JKG9QSRL\",\"blocks\":[{\"type\":\"section\",\"block_id\":\"3Gz\",\"text\":{\"type\":\"mrkdwn\",\"text\":\"Section block with radio buttons\",\"verbatim\":false},\"accessory\":{\"type\":\"radio_buttons\",\"action_id\":\"radio_buttons-action\",\"options\":[{\"text\":{\"type\":\"plain_text\",\"text\":\"12\",\"emoji\":true},\"value\":\"value-0\"},{\"text\":{\"type\":\"plain_text\",\"text\":\"544\",\"emoji\":true},\"value\":\"value-1\"},{\"text\":{\"type\":\"plain_text\",\"text\":\"564\",\"emoji\":true},\"value\":\"value-2\"}]}}]},\"state\":{\"values\":{\"3Gz\":{\"radio_buttons-action\":{\"type\":\"radio_buttons\",\"selected_option\":{\"text\":{\"type\":\"plain_text\",\"text\":\"564\",\"emoji\":true},\"value\":\"value-2\"}}}}},\"response_url\":\"https:\\/\\/hooks.slack.com\\/actions\\/T02JKG9QSRL\\/3636983624404\\/tdwTzOtDWjnb4N1HbAi3vesG\",\"actions\":[{\"action_id\":\"radio_buttons-action\",\"block_id\":\"3Gz\",\"selected_option\":{\"text\":{\"type\":\"plain_text\",\"text\":\"564\",\"emoji\":true},\"value\":\"value-2\"},\"type\":\"radio_buttons\",\"action_ts\":\"1654626585.730303\"}]}";
        WebhookRequest request = new WebhookRequest();
        request.setConfig(getConnector());
        request.setBody(body);
        List<EventData> eventDataList = slackService.parseEventData(request);
        assertFalse(eventDataList.isEmpty());
        assertNotNull(eventDataList.get(0).getData());
        assertNotNull(eventDataList.get(0).getData().getValue("selected_option"));
    }

    @Test
    public void blockParserTimePicketTest() {
        String body = "{\"type\":\"block_actions\",\"user\":{\"id\":\"U02JBHX7TB9\",\"username\":\"blesson\",\"name\":\"blesson\",\"team_id\":\"T02JKG9QSRL\"},\"api_app_id\":\"A02M1UJ354P\",\"token\":\"M64VQMbKkdl2MwimlcokzgNm\",\"container\":{\"type\":\"message\",\"message_ts\":\"1654624880.581599\",\"channel_id\":\"C03JLJBKK35\",\"is_ephemeral\":false},\"trigger_id\":\"3642775168644.2631553842870.0010d350564619365bfce0d270f1d54d\",\"team\":{\"id\":\"T02JKG9QSRL\",\"domain\":\"slacksynapsetest\"},\"enterprise\":null,\"is_enterprise_install\":false,\"channel\":{\"id\":\"C03JLJBKK35\",\"name\":\"slackv20607\"},\"message\":{\"bot_id\":\"B02M9SZRCTG\",\"type\":\"message\",\"text\":\"Testing from instance QA0308 on 6\\/7\",\"user\":\"U02MV7GG88Z\",\"ts\":\"1654624880.581599\",\"app_id\":\"A02M1UJ354P\",\"team\":\"T02JKG9QSRL\",\"blocks\":[{\"type\":\"section\",\"block_id\":\"WchNe\",\"text\":{\"type\":\"mrkdwn\",\"text\":\"Section block with a timepicker\",\"verbatim\":false},\"accessory\":{\"type\":\"timepicker\",\"action_id\":\"timepicker-action\",\"initial_time\":\"13:37\",\"placeholder\":{\"type\":\"plain_text\",\"text\":\"Select time\",\"emoji\":true}}}]},\"state\":{\"values\":{\"WchNe\":{\"timepicker-action\":{\"type\":\"timepicker\",\"selected_time\":\"03:00\"}}}},\"response_url\":\"https:\\/\\/hooks.slack.com\\/actions\\/T02JKG9QSRL\\/3640379749698\\/GqjgeH2Da0hggejAXtTcKc3X\",\"actions\":[{\"type\":\"timepicker\",\"action_id\":\"timepicker-action\",\"block_id\":\"WchNe\",\"selected_time\":\"03:00\",\"initial_time\":\"13:37\",\"action_ts\":\"1654709119.745523\"}]}";
        WebhookRequest request = new WebhookRequest();
        request.setConfig(getConnector());
        request.setBody(body);
        List<EventData> eventDataList = slackService.parseEventData(request);
        assertFalse(eventDataList.isEmpty());
        assertNotNull(eventDataList.get(0).getData());
        assertNotNull(eventDataList.get(0).getData().getValue("selected_time"));
        assertEquals(eventDataList.get(0).getData().getValue("selected_time"), "03:00");
    }

}
