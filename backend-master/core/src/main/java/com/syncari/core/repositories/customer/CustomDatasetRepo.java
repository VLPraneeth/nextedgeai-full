package com.syncari.core.repositories.customer;

import com.syncari.core.model.insights.dataset.Dataset;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface CustomDatasetRepo {

    public List<Dataset> findAllApprovedAndGreaterThanId(String datasetId, int limit);

    public List<Dataset> findAllApprovedWithLimit( int limit);
}
