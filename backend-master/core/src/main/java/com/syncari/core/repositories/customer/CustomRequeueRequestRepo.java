package com.syncari.core.repositories.customer;

import com.syncari.core.model.RequeueRequest;

import java.util.List;

public interface CustomRequeueRequestRepo {
    void upsert(List<RequeueRequest> requeueRequests);
}
