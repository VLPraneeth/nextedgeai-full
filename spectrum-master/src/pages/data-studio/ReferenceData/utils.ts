import { ReferenceDataRecord } from 'store/reference-data';
import DataUrlConstants from 'utils/DataUrlConstants';
import { downloadCsvDataAsFile } from 'utils/DownloadUtil';
import { makeUrl } from 'utils/UrlUtil';

export const downloadReferenceData = (refData: ReferenceDataRecord) => {
  const fileName = `${refData.name}.csv`;
  const url = makeUrl(DataUrlConstants.DOWNLOAD_REF_DATA, { refMetaId: refData.id });

  return downloadCsvDataAsFile(fileName, url);
};
