package com.syncari.connector.data;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Partition {
    String id;
    String name;
    Instant createdOn;
    long rowCount;
    long columnCount;
}
