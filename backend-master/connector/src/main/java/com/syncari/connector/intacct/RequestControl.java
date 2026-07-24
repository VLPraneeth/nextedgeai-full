package com.syncari.connector.intacct;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.UUID;

@Data
@Accessors(chain = true)
public class RequestControl {
    String senderid;
    String password;
    String controlid = UUID.randomUUID().toString();
    boolean uniqueid = false;
    String dtdversion ="3.0";
    boolean includewhitespace = false;

    public static RequestControl getRequestControl(String senderId, String password){
        return new RequestControl().setSenderid(senderId).setPassword(password);
    }
}
