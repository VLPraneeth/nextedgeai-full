package com.syncari.api.rest.controllers;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController

public class HealthCheckController {
    @Autowired
    MongoTemplate syncariMongoTemplate;

    @RequestMapping(value = "/health")
    public Map<String, String> home() {
        try {
            MongoCollection<Document> organization = syncariMongoTemplate.getCollection("organization");
            FindIterable<Document> limit = organization.find().limit(1);
            return Map.of("status", "ok");
        } catch (Exception e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    @ExceptionHandler
    public ResponseEntity<Map<String, String>> handle(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", ex.getMessage()));

    }
}
