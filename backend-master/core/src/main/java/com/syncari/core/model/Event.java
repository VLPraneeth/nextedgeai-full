package com.syncari.core.model;

import java.net.NetworkInterface;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.validation.constraints.NotNull;

import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Document
public class Event extends UUIDAuditModel {
    public static String MACHINE_ID = createMachineIdentifier();

    @NotNull(message = "Event type is required")
    String type;
    String subType;
    String client;
    String component;
    Date occuredTime = new Date();
    Date loggedTime = new Date();
    Map<String, Object> details = new HashMap<>();

    public Event() {
    }

    private static String createMachineIdentifier() {
        // build a 2-byte machine piece based on NICs info
        try {
            StringBuilder sb = new StringBuilder();
            Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
            while (e.hasMoreElements()) {
                NetworkInterface ni = e.nextElement();
                sb.append(ni.toString());
                byte[] mac = ni.getHardwareAddress();
                if (mac != null) {
                    ByteBuffer bb = ByteBuffer.wrap(mac);
                    try {
                        sb.append(bb.getChar());
                        sb.append(bb.getChar());
                        sb.append(bb.getChar());
                    } catch (BufferUnderflowException shortHardwareAddressException) { //NOPMD
                        // mac with less than 6 bytes. continue
                    }
                }
            }
            return sb.toString();
        } catch (Throwable t) {
            // exception sometimes happens with IBM JVM, use SecureRandom instead
            return "Unknown Machine Id";
        }
    }


    public static Event from(String type, String subType, String component, String... details) {
        Map<String, Object> detailsMap = new HashMap<>();
        assert details.length % 2 == 0 : "Details must be key value pairs, send in an even number of arguments";
        for (int i = 0; i < details.length; i += 2) {
            detailsMap.put(details[i], details[i + 1]);
        }
        return new Event().setType(type).setSubType(subType).setClient(MACHINE_ID).setComponent(component)
                .setOccuredTime(new Date()).setLoggedTime(new Date()).setDetails(detailsMap);
    }
}
