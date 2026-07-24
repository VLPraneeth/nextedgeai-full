package com.syncari.connector;

import com.sforce.ws.ConnectionException;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.RetriableException;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class ConnectorHelperTest {

    @Test
    public void getColumnAlphabet() throws ConnectionException {
        assertEquals("Y", ConnectorHelper.getColumnAlphabet(25));
        assertEquals("Z", ConnectorHelper.getColumnAlphabet(26));
        assertEquals("AA", ConnectorHelper.getColumnAlphabet(27));
        assertEquals("Z", ConnectorHelper.getColumnAlphabet(26));
        assertEquals("A", ConnectorHelper.getColumnAlphabet(1));
        assertEquals("B", ConnectorHelper.getColumnAlphabet(2));
        assertEquals("C", ConnectorHelper.getColumnAlphabet(3));
        assertEquals("D", ConnectorHelper.getColumnAlphabet(4));
        assertEquals("E", ConnectorHelper.getColumnAlphabet(5));
        assertEquals("F", ConnectorHelper.getColumnAlphabet(6));
        assertEquals("G", ConnectorHelper.getColumnAlphabet(7));
        assertEquals("H", ConnectorHelper.getColumnAlphabet(8));
        assertEquals("I", ConnectorHelper.getColumnAlphabet(9));
        assertEquals("J", ConnectorHelper.getColumnAlphabet(10));
        assertEquals("K", ConnectorHelper.getColumnAlphabet(11));
        assertEquals("L", ConnectorHelper.getColumnAlphabet(12));
        assertEquals("M", ConnectorHelper.getColumnAlphabet(13));
        assertEquals("N", ConnectorHelper.getColumnAlphabet(14));
        assertEquals("O", ConnectorHelper.getColumnAlphabet(15));
        assertEquals("P", ConnectorHelper.getColumnAlphabet(16));
        assertEquals("Q", ConnectorHelper.getColumnAlphabet(17));
        assertEquals("R", ConnectorHelper.getColumnAlphabet(18));
        assertEquals("S", ConnectorHelper.getColumnAlphabet(19));
        assertEquals("T", ConnectorHelper.getColumnAlphabet(20));
        assertEquals("U", ConnectorHelper.getColumnAlphabet(21));
        assertEquals("V", ConnectorHelper.getColumnAlphabet(22));
        assertEquals("W", ConnectorHelper.getColumnAlphabet(23));
        assertEquals("X", ConnectorHelper.getColumnAlphabet(24));
        assertEquals("AZ", ConnectorHelper.getColumnAlphabet(52));
    }

    @Test
    public void withBackoffAndErrorHandlingTest() throws ConnectionException {
        // public RetriableException(String errorCode, String message, String statusCode) {
        int[] counter = {0};
        ConnectorHelper.withBackoff(() -> {
            counter[0]++;
            if (counter[0] < 2) {
                throw new RetriableException(ErrorCodes.ENDPOINT_DOWN, "Test", "500");
            }
            return null;
        }, 5000, 10000, 3);

        try {
            ConnectorHelper.withBackoffAndErrorHandling(() -> {
                if (true) {
                    throw new RetriableException(ErrorCodes.ENDPOINT_DOWN, "Test", "500");
                }
            });
        } catch (Exception e) {
            assertEquals("Test", e.getCause().getMessage());
        }

        try {
            ConnectorHelper.withBackoffAndErrorHandling(() -> {
                if (true) {
                    throw new RetriableException(ErrorCodes.ENDPOINT_DOWN, "Test", "500");
                }
            });
        } catch (Exception e) {
            assertEquals("Test", e.getCause().getMessage());
        }


        try {
            ConnectorHelper.withBackoffAndErrorHandling(() -> {
                if (true) {
                    throw new RetriableException(ErrorCodes.ENDPOINT_DOWN, "Test", "500");
                }
            });
        } catch (Exception e) {
            assertEquals("Test", e.getCause().getMessage());
        }

        try {
            ConnectorHelper.withBackoffAndErrorHandling(() -> {
                if (true) {
                    HttpHeaders header = new HttpHeaders();
                    header.put("Retry-After", List.of("5"));
                    throw HttpClientErrorException.TooManyRequests.create(HttpStatus.TOO_MANY_REQUESTS, "Too many requests", header, null, null);
                }
                return null;
            });
        } catch (Exception e) {
            assertEquals("429 Too many requests", e.getCause().getMessage());
            assertEquals("5", ((HttpClientErrorException.TooManyRequests)e.getCause()).getResponseHeaders().get("Retry-After").get(0));
        }


    }

}