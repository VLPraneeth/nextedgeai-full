package com.syncari.connector.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import com.sforce.soap.partner.PartnerConnection;
import com.sforce.soap.partner.fault.ExceptionCode;
import com.sforce.soap.partner.fault.LoginFault;
import com.sforce.soap.partner.fault.MalformedQueryFault;
import com.sforce.ws.ConnectionException;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.exception.AuthenticationException;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.exception.UnknownException;

@RunWith(SpringRunner.class)
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
public class SalesforceServiceErrorTest {

	@Test
	public void malformedQueryFaultIsNonRetriable() throws ConnectionException {
		SalesforceService service = Mockito.spy(SalesforceService.class);
		PartnerConnection connection = Mockito.mock(PartnerConnection.class);
		String errorMsg = "[MalformedQueryFault [ApiQueryFault [ApiFault  exceptionCode='MALFORMED_QUERY'"
				+ " exceptionMessage='unexpected token: CreatedDate'" + " extendedErrorDetails='{[0]}'" + "]"
				+ " row='1'" + " column='0'" + "]" + "]";
		MalformedQueryFault malformedQueryFault = new MalformedQueryFault();
		malformedQueryFault.setExceptionCode(ExceptionCode.MALFORMED_QUERY);
		malformedQueryFault.setExceptionMessage(errorMsg);
		when(connection.query(ArgumentMatchers.any())).thenThrow(malformedQueryFault);
		doReturn(connection).when(service).getClient(ArgumentMatchers.any());

		try {
			SyncRequest request = new SyncRequest().Builder(null, new EntitySchema("Account"));
			service.getFirstCreatedTime(request);
			fail();
		} catch (NonRetriableException e) {
			assertEquals("MALFORMED_QUERY", e.getErrorCode());
			assertTrue(e.getMessage().contains("exceptionCode='MALFORMED_QUERY'"));
			assertTrue(e.getMessage().contains("exceptionMessage='unexpected token: CreatedDate"));
		}
	}
	
	@Test
	public void loginFaultIsNonRetriable() throws ConnectionException {
		SalesforceService service = Mockito.spy(SalesforceService.class);
		PartnerConnection connection = Mockito.mock(PartnerConnection.class);
		String errorMsg = "Login error";
		LoginFault malformedQueryFault = new LoginFault();
		malformedQueryFault.setExceptionCode(ExceptionCode.INVALID_LOGIN);
		malformedQueryFault.setExceptionMessage(errorMsg);
		when(connection.query(ArgumentMatchers.any())).thenThrow(malformedQueryFault);
		doReturn(connection).when(service).getClient(ArgumentMatchers.any());
		
		try {
			SyncRequest request = new SyncRequest().Builder(null, new EntitySchema("Account"));
			request.setConnector(new ConnectorInfo());
			service.getFirstCreatedTime(request);
			fail();
		} catch (AuthenticationException e) {
			assertEquals(ErrorCodes.LOGIN_ERROR.name(), e.getErrorCode());
			assertTrue(e.getStatusCode().contains(ExceptionCode.INVALID_LOGIN.name()));
		}
	}
	
	@Test
	public void connectionTimeoutIsRetriable() throws ConnectionException {
		SalesforceService service = Mockito.spy(SalesforceService.class);
		PartnerConnection connection = Mockito.mock(PartnerConnection.class);
		ConnectionException timeOut = new ConnectionException("Request timed out");
		when(connection.query(ArgumentMatchers.any())).thenThrow(timeOut);
		doReturn(connection).when(service).getClient(ArgumentMatchers.any());
		
		try {
			SyncRequest request = new SyncRequest().Builder(null, new EntitySchema("Account"));
			service.getFirstCreatedTime(request);
			fail();
		} catch (RetriableException e) {
			assertEquals("CONNECTION_ERROR", e.getErrorCode());
			assertTrue(e.getMessage().contains("Request timed out"));
		}
	}
	
	@Test
	public void nullPointerIsUnknown() throws ConnectionException {
		SalesforceService service = Mockito.spy(SalesforceService.class);
		PartnerConnection connection = Mockito.mock(PartnerConnection.class);
		NullPointerException nullP = new NullPointerException("Null pointer");
		when(connection.query(ArgumentMatchers.any())).thenThrow(nullP);
		doReturn(connection).when(service).getClient(ArgumentMatchers.any());
		
		try {
			SyncRequest request = new SyncRequest().Builder(null, new EntitySchema("Account"));
			service.getFirstCreatedTime(request);
			fail();
		} catch (UnknownException e) {
			assertEquals("UNKNOWN_EXCEPTION", e.getErrorCode());
			assertTrue(e.getMessage().contains("Null pointer"));
		}
	}

}
