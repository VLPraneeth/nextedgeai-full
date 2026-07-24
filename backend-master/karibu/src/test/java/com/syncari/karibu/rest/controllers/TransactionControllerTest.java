package com.syncari.karibu.rest.controllers;

import com.jayway.jsonpath.JsonPath;
import com.syncari.karibu.rest.config.KaribuConstants;
import com.syncari.karibu.rest.util.OauthUtil;
import com.syncari.karibu.rest.util.TransactionTestUtil;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class TransactionControllerTest extends AbstractSyncariTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    OauthUtil oauthUtil;

    @Autowired
    TransactionTestUtil transactionTestUtil;

    @Override
    public void setUp() {
        super.setUp();
    }

    @Test
    public void listTransactionTest() {
        try {
            int numberOfRecords = 20;

            int i = 0;
            while (i < numberOfRecords) {
                transactionTestUtil.createTxnLog();
                i++;
            }

            Calendar calStart = Calendar.getInstance();
            calStart.add(Calendar.DATE, -1);
            Calendar calEnd = Calendar.getInstance();
            calEnd.add(Calendar.DATE, 1);

            SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            String startTime = s.format(new Date(calStart.getTimeInMillis()));
            String endTime = s.format(new Date(calEnd.getTimeInMillis()));

            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultGetTransactions = mockMvc.perform(get("/api/v1/transactions")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("startTime", startTime)
                            .param("endTime", endTime)
                    )
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(40)))
                    .andExpect(jsonPath("$.result.[0].operation", is("create")))
                    .andExpect(jsonPath("$.result.[0].entityName", is("account")))
                    .andExpect(jsonPath("$.result.[0].sources.[0].synapseId", is("my salesforce connector")))
                    .andExpect(jsonPath("$.result.[0].losingRecords").doesNotExist())
                    .andExpect(jsonPath("$.result.[0].winningRecord").doesNotExist())
                    .andExpect(status().isOk());

            ResultActions resultGetTransactionsByOperation = mockMvc.perform(get("/api/v1/transactions")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("startTime", startTime)
                            .param("endTime", endTime)
                            .param("operation", "Update")
                    )
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(20)))
                    .andExpect(jsonPath("$.result.[0].operation", is("update")))
                    .andExpect(jsonPath("$.result.[0].entityName", is("account")))
                    .andExpect(jsonPath("$.result.[0].sources.[0].synapseId", is("my salesforce connector")))
                    .andExpect(jsonPath("$.result.[0].transactionDetails").exists())
                    .andExpect(jsonPath("$.result.[0].losingRecords").doesNotExist())
                    .andExpect(jsonPath("$.result.[0].winningRecord").doesNotExist())
                    .andExpect(status().isOk());

            ResultActions resultGetTransactionsLimit = mockMvc.perform(get("/api/v1/transactions")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("startTime", startTime)
                            .param("endTime", endTime)
                            .param("operation", "Update")
                            .param("limit", "1")
                    )
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(1)))
                    .andExpect(jsonPath("$.result.[0].operation", is("update")))
                    .andExpect(jsonPath("$.result.[0].entityName", is("account")))
                    .andExpect(jsonPath("$.result.[0].sources.[0].synapseId", is("my salesforce connector")))
                    .andExpect(jsonPath("$.result.[0].transactionDetails").exists())
                    .andExpect(jsonPath("$.result.[0].losingRecords").doesNotExist())
                    .andExpect(jsonPath("$.result.[0].winningRecord").doesNotExist())
                    .andExpect(jsonPath("cursorToken", notNullValue()))
                    .andExpect(status().isOk());

            ResultActions resultGetTransactionsEntityName = mockMvc.perform(get("/api/v1/transactions")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("startTime", startTime)
                            .param("endTime", endTime)
                            .param("syncariEntityName", "account")
                            .param("limit", "1")
                    )
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(1)))
                    .andExpect(jsonPath("$.result.[0].operation", is("create")))
                    .andExpect(jsonPath("$.result.[0].entityName", is("account")))
                    .andExpect(jsonPath("$.result.[0].sources.[0].synapseId", is("my salesforce connector")))
                    .andExpect(jsonPath("$.result.[0].losingRecords").doesNotExist())
                    .andExpect(jsonPath("$.result.[0].winningRecord").doesNotExist())
                    .andExpect(status().isOk());

            ResultActions resultGetTransactionsByMissingEntityName = mockMvc.perform(get("/api/v1/transactions")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("startTime", startTime)
                            .param("endTime", endTime)
                            .param("operation", "Update")
                            .param("syncariEntityName", "Contact__c1")
                            .param("limit", "1")
                    )
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(0)))
                    .andExpect(status().isOk());

            ResultActions resultGetTransactionsGetCursor = mockMvc.perform(get("/api/v1/transactions")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("startTime", startTime)
                            .param("endTime", endTime)
                            .param("limit", "1")
                    )
                    .andDo(print())
                    .andExpect(jsonPath("$.cursorToken").exists())
                    .andExpect(jsonPath("$.result", hasSize(1)))
                    .andExpect(jsonPath("$.result.[0].operation", is("create")))
                    .andExpect(jsonPath("$.result.[0].entityName", is("account")))
                    .andExpect(jsonPath("$.result.[0].sources.[0].synapseId", is("my salesforce connector")))
                    .andExpect(jsonPath("$.result.[0].losingRecords").doesNotExist())
                    .andExpect(jsonPath("$.result.[0].winningRecord").doesNotExist())
                    .andExpect(status().isOk());

            MvcResult getTransactionsGetCursorResult = resultGetTransactionsGetCursor.andReturn();
            String cursorToken = JsonPath.read(getTransactionsGetCursorResult.getResponse().getContentAsString(), "$.cursorToken");

            ResultActions resultGetTransactionsCursor = mockMvc.perform(get("/api/v1/transactions")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("startTime", startTime)
                            .param("endTime", endTime)
                            .param("cursorToken", cursorToken)
                            .param("limit", "1")
                    )
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(1)))
                    .andExpect(jsonPath("$.result.[0].operation", is("update")))
                    .andExpect(jsonPath("$.result.[0].entityName", is("account")))
                    .andExpect(jsonPath("$.result.[0].sources.[0].synapseId", is("my salesforce connector")))
                    .andExpect(jsonPath("$.result.[0].losingRecords").doesNotExist())
                    .andExpect(jsonPath("$.result.[0].winningRecord").doesNotExist())
                    .andExpect(status().isOk());

        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }

    @Test
    public void listTransactionMergeTest() {
        try {

            int i = 0;
            while (i < 2) {
                transactionTestUtil.createMergeTxnLog(Optional.of(true));
                i++;
            }

            Calendar calStart = Calendar.getInstance();
            calStart.add(Calendar.DATE, -1);
            Calendar calEnd = Calendar.getInstance();
            calEnd.add(Calendar.DATE, 1);

            SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            String startTime = s.format(new Date(calStart.getTimeInMillis()));
            String endTime = s.format(new Date(calEnd.getTimeInMillis()));

            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultGetTransactionsMergeReportOnly = mockMvc.perform(get("/api/v1/transactions")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("startTime", startTime)
                            .param("endTime", endTime)
                            .param("operation", "Merge_Report_Only")
                    )
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(2)))
                    .andExpect(jsonPath("$.result.[0].operation", is("merge_report_only")))
                    .andExpect(jsonPath("$.result.[0].entityName", is("account")))
                    .andExpect(jsonPath("$.result.[0].sources.[0].synapseId", is("my salesforce connector")))
                    .andExpect(jsonPath("$.result.[0].losingRecords").exists())
                    .andExpect(jsonPath("$.result.[0].winningRecord").exists())
                    .andExpect(status().isOk());

            ResultActions resultGetTransactionsMerge = mockMvc.perform(get("/api/v1/transactions")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("startTime", startTime)
                            .param("endTime", endTime)
                            .param("operation", "Merge")
                    )
                    .andDo(print())
                    .andExpect(jsonPath("$.result", hasSize(0)))
                    .andExpect(status().isOk());

        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }

    }


    @Test
    public void listTransactionNegativeTest() {
        try {
            Calendar calStart = Calendar.getInstance();
            calStart.add(Calendar.DATE, -1);
            Calendar calEnd = Calendar.getInstance();
            calEnd.add(Calendar.DATE, 1);

            SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            String startTime = s.format(new Date(calStart.getTimeInMillis()));
            String endTime = s.format(new Date(calEnd.getTimeInMillis()));

            String accessToken = oauthUtil.getTestAccessToken();

            ResultActions resultGetTransactionsBadTime = mockMvc.perform(get("/api/v1/transactions")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("startTime", "badTime")
                            .param("endTime", "badTime")
                    )
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Please use date/time format of YYYY-MM-ddTHH:mm:ss for startTime and endTime")))
                    .andExpect(status().isBadRequest());

            ResultActions resultGetTransactionsMissingTime = mockMvc.perform(get("/api/v1/transactions")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                    )
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Request parameters startTime and endTime are required for list transactions")))
                    .andExpect(status().isBadRequest());

            ResultActions resultGetTransactionsBadOperation = mockMvc.perform(get("/api/v1/transactions")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("startTime", startTime)
                            .param("endTime", endTime)
                            .param("operation", "badOperation")
                    )
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Operation of badOperation not in accepted operations [Create, Update, Delete, Disconnect, Merge, Merge_Report_Only]")))
                    .andExpect(status().isBadRequest());

            ResultActions resultGetTransactionsBadLimit = mockMvc.perform(get("/api/v1/transactions")
                            .header("Authorization", accessToken)
                            .contentType(APPLICATION_JSON_UTF8)
                            .param("startTime", startTime)
                            .param("endTime", endTime)
                            .param("limit", "10000")
                    )
                    .andDo(print())
                    .andExpect(jsonPath("$.error.message", is("Limit value of 10000 exceeds max value of "+ KaribuConstants.MAX_LIMIT)))
                    .andExpect(status().isBadRequest());

        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }
}
