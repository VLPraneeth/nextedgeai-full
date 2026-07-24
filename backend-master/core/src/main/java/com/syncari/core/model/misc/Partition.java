package com.syncari.core.model.misc;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Partition {
    String id;
    String name;
    Instant createdOn;
    long rowCount;
    long columnCount;
}
