package com.syncari.core.changelogs.syncari;

import java.io.IOException;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.service.ReferenceDataService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeLog(order = "0040")
public class M0040_UpdateSyncariReferenceDataSet {

	private final ReferenceDataService service = MigrationContext.getReferenceDataService();

	@ChangeSet(order = "001", id = "updateSyncariReferenceDataSet", author = "sibin")
	public void updateSyncariReferenceDataSet() {
		boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
		log.info("Running this tool in dryrun mode: {} ", dryRunMode);

		populateData("countriesWithRegionalCodes.csv", "Countries With Regional Codes", dryRunMode);
	}

	private void populateData(String fileName, String datasetName, boolean dryRunMode) {
		log.info("Loading resource file: {} for database {}", fileName, datasetName);
		Resource resource = new ClassPathResource("dataset/" + fileName);
		if (!dryRunMode) {
			service.findReferenceDataByName(datasetName).ifPresent(dataset -> {
				try {
					service.extract(service
							.updateMeta(dataset, resource.getInputStream(), resource.getInputStream(), false).getId(),
							false);
				} catch (IOException e) {
					log.info("Error occured loading file", e);
					throw new RuntimeException(e);
				}
			});
		}
	}

}
