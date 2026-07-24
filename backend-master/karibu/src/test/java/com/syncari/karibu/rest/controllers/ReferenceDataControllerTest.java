package com.syncari.karibu.rest.controllers;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jayway.jsonpath.JsonPath;
import com.syncari.core.file.FileManagerFactory;
import com.syncari.core.file.GCSFileManager;
import com.syncari.core.model.ReferenceDataMeta;
import com.syncari.core.model.misc.ReferenceDataSource;
import com.syncari.core.model.misc.ReferenceDataSourceType;
import com.syncari.core.service.ReferenceDataService;
import com.syncari.karibu.rest.util.OauthUtil;
import com.syncari.restutils.utils.ApiUtils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@Slf4j
@TestPropertySource(locations = "classpath:test_application.properties")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ReferenceDataControllerTest extends AbstractSyncariTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    OauthUtil oauthUtil;

    @Autowired
    ReferenceDataService service;
    
	@Mock
	FileManagerFactory fileFactory;
	@Autowired
	FileManagerFactory fileManagerFactory;
	@Mock
	GCSFileManager gcsFileManager;

    ObjectMapper mapper = new ObjectMapper();
    
    @Autowired
    ApiUtils apiUtils;

    @Override
    public void setUp() {
        super.setUp();
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
        doReturn(gcsFileManager).when(fileFactory).getFileManager(any());
		doReturn("somepath").when(gcsFileManager).uploadFile(any(), any());
		doNothing().when(publisher).publishToGenericQueue(anyString());
		service.setFileManagerFactory(fileFactory);
    }

    @Test
    public void addUpdateDeleteItemsTest() throws Exception {
        try {
        	ReferenceDataMeta meta = create("City names dataset");
        	List<Map<String, Object>> rows = new ArrayList<>();
        	rows.add(Map.of("City Name", "Foster City", "Code", 10));
            ObjectWriter ow = mapper.writer().withDefaultPrettyPrinter();
            String accessToken = oauthUtil.getTestAccessToken();

            // add items
            ResultActions resultRef = mockMvc.perform(post("/api/v1/referencedata/"+meta.getId()+"/items")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .content(ow.writeValueAsString(rows)))
                    .andDo(print())
                    .andExpect(status().isOk());
            MvcResult result = resultRef.andReturn();
            Object resultVal = JsonPath.read(result.getResponse().getContentAsString(), "$.result");
			assertNotNull(resultVal);
			
			// update items
			String id = JsonPath.read(result.getResponse().getContentAsString(), "$.result[0]");
			Map<String, Map<String, Object>> req = new HashMap<>();
			req.put(id, Map.of("Code", 5));
			resultRef = mockMvc.perform(patch("/api/v1/referencedata/"+meta.getId()+"/items")
                    .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                    .contentType(APPLICATION_JSON_UTF8)
                    .content(ow.writeValueAsString(req)))
            .andDo(print())
            .andExpect(status().isOk());
            result = resultRef.andReturn();
            assertEquals("1", JsonPath.read(result.getResponse().getContentAsString(), "$.result.updatedCount").toString());

            // delete items
            resultRef = mockMvc.perform(delete("/api/v1/referencedata/"+meta.getId()+"/items")
                    .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                    .contentType(APPLICATION_JSON_UTF8)
                    .content(ow.writeValueAsString(resultVal)))
            .andDo(print())
            .andExpect(status().isOk());
            result = resultRef.andReturn();
            assertEquals("1", JsonPath.read(result.getResponse().getContentAsString(), "$.result.deletedCount").toString());

            // replace items
            List<Map<String, Object>> replacedRows = List.of(
                    Map.of("City Name", "SFO", "Code", 200),
                    Map.of("City Name", "SJ", "Code", 300),
                    Map.of("City Name", "FRM", "Code", 400)
            );

            resultRef  = mockMvc.perform(post("/api/v1/referencedata/"+meta.getId()+"/items")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .content(ow.writeValueAsString(replacedRows)))
                    .andDo(print())
                    .andExpect(status().isOk());
            result = resultRef.andReturn();
            assertEquals("3", JsonPath.read(result.getResponse().getContentAsString(), "$.result.length()").toString());

        } catch (Exception e) {
        	log.error(ExceptionUtils.getStackTrace(e));
            assertTrue(false);
        }
    }
    
	@Test
	public void queryTest() throws Exception {
		ReferenceDataMeta meta = create("City names dataset query");
		List<Map<String, Object>> rows = new ArrayList<>();
		rows.add(Map.of("City Name", "Foster City", "Code", 10));
		rows.add(Map.of("City Name", "San Mateo", "Code", 20));
		rows.add(Map.of("City Name", "San Francisco", "Code", 30));
		rows.add(Map.of("City Name", "Dublin", "Code", 40));
		ObjectWriter ow = mapper.writer().withDefaultPrettyPrinter();
		String accessToken = oauthUtil.getTestAccessToken();

		// add items
		ResultActions resultRef = mockMvc
				.perform(post("/api/v1/referencedata/" + meta.getId() + "/items")
						.header("clientRequestId", "placeholder").header("Authorization", accessToken)
						.contentType(APPLICATION_JSON_UTF8).content(ow.writeValueAsString(rows)))
				.andDo(print()).andExpect(status().isOk());
		MvcResult result = resultRef.andReturn();
		Object resultVal = JsonPath.read(result.getResponse().getContentAsString(), "$.result");
		assertNotNull(resultVal);

		// query items first page limit 1
		resultRef = mockMvc.perform(
				get("/api/v1/referencedata/" + meta.getId() + "/items?limit=1").header("clientRequestId", "placeholder")
						.header("Authorization", accessToken).contentType(APPLICATION_JSON_UTF8))
				.andDo(print()).andExpect(status().isOk());
		result = resultRef.andReturn();
		assertEquals(1, ((List) JsonPath.read(result.getResponse().getContentAsString(), "$.result")).size());

		// query items first page limit 10
		resultRef = mockMvc.perform(get("/api/v1/referencedata/" + meta.getId() + "/items?limit=10")
				.header("clientRequestId", "placeholder").header("Authorization", accessToken)
				.contentType(APPLICATION_JSON_UTF8)).andDo(print()).andExpect(status().isOk());
		result = resultRef.andReturn();
		List list = (List) JsonPath.read(result.getResponse().getContentAsString(), "$.result");
		assertEquals(5, list.size());

		// query second page limit 10
		String cursorToken = apiUtils.encodeCursor(((Map) list.get(1)).get("id").toString());
		resultRef = mockMvc.perform(
				get("/api/v1/referencedata/" + meta.getId() + "/items?cursorToken=" + cursorToken + "&limit=10")
						.header("clientRequestId", "placeholder").header("Authorization", accessToken)
						.contentType(APPLICATION_JSON_UTF8))
				.andDo(print()).andExpect(status().isOk());
		result = resultRef.andReturn();
		assertEquals(3, ((List) JsonPath.read(result.getResponse().getContentAsString(), "$.result")).size());

		// query second page limit 1
		resultRef = mockMvc
				.perform(get("/api/v1/referencedata/" + meta.getId() + "/items?cursorToken=" + cursorToken + "&limit=1")
						.header("clientRequestId", "placeholder").header("Authorization", accessToken)
						.contentType(APPLICATION_JSON_UTF8))
				.andDo(print()).andExpect(status().isOk());
		result = resultRef.andReturn();
		assertEquals(1, ((List) JsonPath.read(result.getResponse().getContentAsString(), "$.result")).size());
		assertEquals(((Map) list.get(2)).get("id").toString(),
				apiUtils.decodeCursor(JsonPath.read(result.getResponse().getContentAsString(), "$.cursorToken").toString()));
	}

    @Test
    public void ListReferenceDataTest() throws Exception {
        try {
            String accessToken = oauthUtil.getTestAccessToken();

            // --------------------------- get reference data ----------------------------------------------------------
            ResultActions resultListReferenceDatasets = mockMvc.perform(get("/api/v1/referencedata")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(5)))
                    .andExpect(jsonPath("$.result.[0].name", is("Free Email Domains")))
                    .andExpect(status().isOk());

            MvcResult referenceDatasetsResult = resultListReferenceDatasets.andReturn();
            String referenceDataId = JsonPath.read(referenceDatasetsResult.getResponse().getContentAsString(), "$.result.[0].id");

            ResultActions resultBadPipeline = mockMvc.perform(get("/api/v1/referencedata/{referenceDataId}", "badReferenceDataId")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Reference Data with Id badReferenceDataId is not found")))
                    .andExpect(status().isNotFound());

            ResultActions resultPipeline = mockMvc.perform(get("/api/v1/referencedata/{referenceDataId}", referenceDataId)
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.name", is("Free Email Domains")))
                    .andExpect(status().isOk());

        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void uploadReferenceDataTest() throws Exception {
        try {
            String accessToken = oauthUtil.getTestAccessToken();

            File f = new File("src/test/resources/csv/valid.csv");
            FileInputStream fi1 = new FileInputStream(f);
            MockMultipartFile fstmp = new MockMultipartFile("file", f.getName(), "text/csv",fi1);

            ResultActions resultCreateReferenceDataset = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/referencedata/upload")
                            .file(fstmp)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType("text/csv")
                            .param("name", "valid")
                            .param("fileName", "valid.csv"))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.name", is("valid")))
                    .andExpect(status().isOk());

            ResultActions resultCreateReferenceDatasetDuplicate = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/referencedata/upload")
                            .file(fstmp)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType("text/csv")
                            .param("name", "valid")
                            .param("fileName", "valid.csv"))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Dataset with name 'valid' already exists.")))
                    .andExpect(status().isBadRequest());

        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void uploadReferenceDataNegativeTest() throws Exception {
        try {
            String accessToken = oauthUtil.getTestAccessToken();

            File f = new File("src/test/resources/csv/invalid.csv");
            FileInputStream fi1 = new FileInputStream(f);
            MockMultipartFile fstmp = new MockMultipartFile("file", f.getName(), "text/csv",fi1);

            ResultActions resultCreateReferenceDatasetBadName = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/referencedata/upload")
                            .file(fstmp)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType("text/csv")
                            .param("fileName", "invalid.csv"))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Required String parameter 'name' is not present")))
                    .andExpect(status().isBadRequest());

            ResultActions resultCreateReferenceDatasetBadFilename = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/referencedata/upload")
                            .file(fstmp)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType("text/csv")
                            .param("name", "invalid"))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Required String parameter 'fileName' is not present")))
                    .andExpect(status().isBadRequest());

            File f2 = new File("src/test/resources/csv/non-csv.png");
            FileInputStream fi2 = new FileInputStream(f2);
            MockMultipartFile fstmp2 = new MockMultipartFile("file", f2.getName(), "image/png",fi2);

            ResultActions resultCreateReferenceDatasetBadFile = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/referencedata/upload")
                            .file(fstmp2)
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType("image/png")
                            .param("name", "non-csv")
                            .param("fileName", "non-csv.png"))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Unsupported file extension")))
                    .andExpect(status().isBadRequest());
        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void createReferenceDataTest() throws Exception {
        try {
            String accessToken = oauthUtil.getTestAccessToken();

            String referenceDataRequest = "{\"name\": \"testReferenceDataset1\", \"headerColumns\": [\"column1\", \"column2\"] }";

            ResultActions resultCreateReferenceDataset = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/referencedata")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(referenceDataRequest))
                    .andDo(print())
                    .andExpect(jsonPath("$.result.name", is("testReferenceDataset1")))
                    .andExpect(jsonPath("$.result.headerColumns", hasSize(2)))
                    .andExpect(jsonPath("$.result.headerColumns.[0]", is("column1")))
                    .andExpect(jsonPath("$.result.headerColumns.[1]", is("column2")))
                    .andExpect(status().isOk());

            MvcResult createReferenceDataResult = resultCreateReferenceDataset.andReturn();
            String referenceDataId = JsonPath.read(createReferenceDataResult.getResponse().getContentAsString(), "$.result.id");

            String updateItemRequest = "[{\"column1\" : \"column1value\", \"column2\" : \"column2value\" }]";

            // add items
            ResultActions resultUpdateReferenceDataset = mockMvc.perform(post("/api/v1/referencedata/"+referenceDataId+"/items")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(updateItemRequest))
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(1)))
                    .andExpect(status().isOk());

            ResultActions resultCreateReferenceDatasetDuplicate = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/referencedata")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(referenceDataRequest))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Dataset with name 'testReferenceDataset1' already exists.")))
                    .andExpect(status().isBadRequest());
        } catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void createDeleteReferenceDataTest() throws Exception {
        // create reference data
        String referenceDataId = create();
        String accessToken = oauthUtil.getTestAccessToken();
        try {
            // try creating again fails
            create();
            fail();
        } catch (Exception e) {
        }

        // delete it
        ResultActions resultCreateReferenceDataset = mockMvc.perform(delete("/api/v1/referencedata/"+referenceDataId)
                        .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                        .contentType(APPLICATION_JSON_UTF8))
                .andDo(print())
                .andExpect(status().isOk());
        resultCreateReferenceDataset.andReturn();

        // now create succeeds
        create();
    }
    private String create() throws Exception {
        String accessToken = oauthUtil.getTestAccessToken();

        String referenceDataRequest = "{\"name\": \"testReferenceDataset10\", \"headerColumns\": [\"column1\", \"column2\"] }";

        ResultActions resultCreateReferenceDataset = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/referencedata")
                        .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                        .contentType(APPLICATION_JSON_UTF8).content(referenceDataRequest));

        MvcResult createReferenceDataResult = resultCreateReferenceDataset.andReturn();
        return JsonPath.read(createReferenceDataResult.getResponse().getContentAsString(), "$.result.id");
    }

    @Test
    public void createReferenceDataNegativeTest() throws Exception {
        try {
            String accessToken = oauthUtil.getTestAccessToken();

            String referenceDataRequestMissingName = "{\"headerColumns\": [\"column1\", \"column2\"] }";

            ResultActions resultCreateReferenceDatasetMissingName = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/referencedata")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(referenceDataRequestMissingName))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Reference Data name is empty. Please verify these request parameters")))
                    .andExpect(status().isBadRequest());

            String referenceDataRequestMissingHederColums = "{\"name\": \"testReferenceDataset1\"}";

            ResultActions resultCreateReferenceDatasetMissingHeafColumns = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/referencedata")
                            .header("clientRequestId", "placeholder").header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8).content(referenceDataRequestMissingHederColums))
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Reference Data headerColumns is empty. Please verify these request parameters")))
                    .andExpect(status().isBadRequest());

        } catch (Exception e) {
            assertTrue(false);
        }
    }

    private ReferenceDataMeta create(String name) throws FileNotFoundException, IOException {
        ReferenceDataMeta refData = new ReferenceDataMeta(name,
                new ReferenceDataSource(ReferenceDataSourceType.upload, "city.csv", null, null));
        InputStream fileStream1 = new FileInputStream("src/test/resources/csv/city.csv");
        doReturn(fileStream1).when(gcsFileManager).readFile(any());
        try (InputStream fileStream = new FileInputStream("src/test/resources/csv/city.csv")) {
            ReferenceDataMeta meta = service.createMeta(refData, fileStream, null, true);
            refData = service.extract(refData.getId(), true);
            return meta;
        }
    }

}
