package com.syncari.karibu.rest.controllers;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.syncari.karibu.rest.util.ViperUtils;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController

public class HealthCheckController {
    @Autowired
    MongoTemplate syncariMongoTemplate;
    @Autowired
    ViperUtils viperUtils;

    @RequestMapping(value = "/health", method = RequestMethod.GET)
    public ResponseEntity<Map<String, String>> apiHealth() {
        try {
            MongoCollection<Document> organization = syncariMongoTemplate.getCollection("organization");
            FindIterable<Document> limit = organization.find().limit(1);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(Map.of("status", "ok"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @RequestMapping(value = "/health/sync")
    public ResponseEntity<Map<String, Object>> syncHealth() {
        final ResponseEntity<Map<String, Object>> health = viperUtils.health();
        return ResponseEntity.status(HttpStatus.OK)
                .body(health.getBody());
    }

    @ExceptionHandler
    public ResponseEntity<Map<String, String>> handle(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", ex.getMessage()));

    }
}
