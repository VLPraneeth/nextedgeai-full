package com.syncari.connector;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Response {
    String offset;
    List<EntityData> records = new ArrayList<>();
}