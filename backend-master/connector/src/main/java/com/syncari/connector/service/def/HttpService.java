package com.syncari.connector.service.def;

import com.syncari.connector.ConnectorInfo;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public interface HttpService {


    public default ResponseEntity<String> doPost(ConnectorInfo connectorInfo, String url, String body, String method){
        return new ResponseEntity<String>("", HttpStatus.ACCEPTED);
    }

    public default ResponseEntity<String> doPut(ConnectorInfo connectorInfo, String url, String body, String method){
        return new ResponseEntity<String>("", HttpStatus.ACCEPTED);
    }

    public default ResponseEntity<String> doDelete(ConnectorInfo connectorInfo, String url, String body, String method){
        return new ResponseEntity<String>("", HttpStatus.ACCEPTED);
    }
}
