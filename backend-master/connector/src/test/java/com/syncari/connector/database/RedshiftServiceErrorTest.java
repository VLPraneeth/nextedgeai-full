package com.syncari.connector.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;

import java.sql.SQLTimeoutException;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.database.RedshiftService;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.exception.UnknownException;

@RunWith(SpringRunner.class)
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class RedshiftServiceErrorTest {

	@Test
	public void nullPointerIsUnknown() throws Exception {
		RedshiftService service = Mockito.spy(RedshiftService.class);
		service.compositeKeyHelper = new CompositeKeyHelper();
		doThrow(new RuntimeException("Null pointer")).when(service).getConnection(ArgumentMatchers.any());

		try {
			service.getByIds(getRequest());
			fail();
		} catch (NonRetriableException e) {
			assertEquals("UNKNOWN_ERROR", e.getErrorCode());
			assertTrue(e.getMessage().contains("Null pointer"));
		}
	}

	@Test
	public void sqlTimeoutIsRetriable() throws Exception {
		RedshiftService service = Mockito.spy(RedshiftService.class);
		service.compositeKeyHelper = new CompositeKeyHelper();
		doThrow(new SQLTimeoutException("Time out")).when(service).getConnection(ArgumentMatchers.any());

		try {
			service.getByIds(getRequest());
			fail();
		} catch (RetriableException e) {
			assertEquals("TIME_OUT", e.getErrorCode());
			assertTrue(e.getMessage().contains("Time out"));
		}
	}

	@Test
	public void classNotFoundIsNonRetriable() throws Exception {
		RedshiftService service = Mockito.spy(RedshiftService.class);
		service.compositeKeyHelper = new CompositeKeyHelper();
		doThrow(new ClassNotFoundException("Class not found")).when(service).getConnection(ArgumentMatchers.any());

		try {
			service.getByIds(getRequest());
			fail();
		} catch (NonRetriableException e) {
			assertEquals("INTERNAL_SERVER_ERROR", e.getErrorCode());
			assertTrue(e.getMessage().contains("Class not found"));
		}
	}

	private SyncRequest getRequest() {
		EntitySchema entitySchema = new EntitySchema("ticket");
		entitySchema.addField(new AttributeSchema("id", "string").setIdField(true));
        SyncRequest request = new SyncRequest()
				.Builder(new ConnectorInfo("123", "redshift", "https://redshift.com","instance1"), entitySchema)
				.setWatermark(new WatermarkInfo());
		request.setData(Map.of("123", List.of(new EntityData())));
		return request;
	}
}
