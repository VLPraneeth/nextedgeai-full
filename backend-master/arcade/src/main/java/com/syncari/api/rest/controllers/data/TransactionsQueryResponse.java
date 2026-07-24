package com.syncari.api.rest.controllers.data;

import java.util.List;

import com.syncari.core.model.TransactionLog;
import com.syncari.core.model.pagination.PageInfo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
// TODO: This can be replaced with the Page Object
public class TransactionsQueryResponse {
    List<TransactionLog> records;
    PageInfo pageInfo;
}
