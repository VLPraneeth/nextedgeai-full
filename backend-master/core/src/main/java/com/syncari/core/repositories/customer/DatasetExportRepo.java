package com.syncari.core.repositories.customer;

import com.syncari.core.model.insights.dataset.DatasetExport;
import com.syncari.core.repositories.SyncariRepo;

import java.util.List;
import java.util.Optional;

public interface DatasetExportRepo extends SyncariRepo<DatasetExport> {

    List<DatasetExport> findAllByDatasetId(String datasetId);

    List<DatasetExport> findAllByDatasetIdAndStatusIn(String datasetId, List<String> status);
}
