package com.syncari.connector.data;

import lombok.Data;

import java.util.List;

@Data
public class DescribeAllResponse {
    private List<EntitySchema> data;
}
