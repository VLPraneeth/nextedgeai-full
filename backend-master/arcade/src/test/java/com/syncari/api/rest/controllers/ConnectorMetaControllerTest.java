package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.READ_STUDIO;
import static com.syncari.core.security.Permissions.WRITE_STUDIO;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import com.syncari.api.core.util.ObjectTransformer;
import com.syncari.api.rest.controllers.data.HttpSourceEntityTestRequest;
import com.syncari.api.rest.controllers.data.HttpSourceEntityTestResponse;
import com.syncari.connector.data.HTTPSourceResult;
import com.syncari.core.http.source.HttpSourceConfig;
import com.syncari.core.service.ConnectorMetadataService;

public class ConnectorMetaControllerTest extends AbstractSyncariTest {

  @Mock
  ConnectorMetadataService service;
  @Mock
  ObjectTransformer transformer;
  @InjectMocks
  ConnectorMetaController controller;


  @Override
  public void setUp() {
    super.setUp();

  }

  @Override
  public void tearDown() {
    super.tearDown();
  }

  @Test
  @WithMockUser(username = "admin", authorities = {WRITE_STUDIO, READ_STUDIO})
  public void testHttpSourceEntity() throws Exception {
    HttpSourceEntityTestRequest testReq = new HttpSourceEntityTestRequest();
    testReq.setBody("{\\\"test\\\":\\\"test123\\\"}");

    HttpSourceConfig httpSourceConfig = new HttpSourceConfig();
    HTTPSourceResult httpResponse = new HTTPSourceResult();
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    httpResponse.setRequestHeaders(httpHeaders);

    when(transformer.toHttpSourceConfig(any())).thenReturn(httpSourceConfig);
    when(service.testHttpSource(any(), any(), any(), any(), any())).thenReturn(httpResponse);
    when(transformer.toHttpSourceEntityTestResponse(any(), any())).thenReturn(new HttpSourceEntityTestResponse());

    HttpSourceEntityTestResponse response = controller.test(testReq);

    assertNotNull(response); // Ensure the response is not null
    verify(service, times(1)).testHttpSource(any(), any(), any(), any(), any()); // Verify the service was called once

    // Validate that the body is unescaped and trimmed correctly
    assertEquals("{\"test\":\"test123\"}", testReq.getBody());

  }


}
