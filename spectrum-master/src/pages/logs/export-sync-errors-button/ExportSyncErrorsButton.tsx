import { Button, Icon, Tooltip } from 'antd';
import { useCallback } from 'react';

import { useEnhancedSelector } from 'hooks/redux';
import { GetSyncErrorsParams } from 'store/logs/thunks';
import AppConstants from 'utils/AppConstants';
import DataUrlConstants from 'utils/DataUrlConstants';
import { formatDatesInParams } from 'utils/DateUtil';
import { downloadFile } from 'utils/DownloadUtil';
import { t, tc } from 'utils/i18nUtil';
import { makeUrl } from 'utils/UrlUtil';

import './ExportSyncErrorsButton.scss';

export interface ExportSyncErrorsButtonProps {
  dataParams: Omit<GetSyncErrorsParams, 'pageNumber' | 'count'>;
}

export const ExportSyncErrorsButton = ({ dataParams }: ExportSyncErrorsButtonProps) => {
  const { fetchStatus, listData: errorData } = useEnhancedSelector((state) => state.logs.syncErrors);

  const { entityId, operation, syncariRecordId, syncariEntityName, connectorName, startDate, endDate } = dataParams;

  const handleExportAsCSV = useCallback(() => {
    const { startDate: fStartDate, endDate: fEndDate } = formatDatesInParams({ startDate, endDate });

    const url = makeUrl(DataUrlConstants.DOWNLOAD_SYNC_ERRORS, undefined, {
      startDate: fStartDate,
      endDate: fEndDate,
      syncariRecordId,
      syncariEntityName: syncariEntityName === 'all' ? undefined : syncariEntityName,
      connectorName: connectorName === 'all' ? undefined : connectorName,
      entityId: entityId === 'all' ? undefined : entityId,
      operation: operation === 'all' ? undefined : operation,
    });

    downloadFile(url);
  }, [connectorName, endDate, entityId, operation, startDate, syncariEntityName, syncariRecordId]);

  return (
    <Button
      className="export-sync-errors-button"
      disabled={false && (fetchStatus === AppConstants.FETCH_STATUS.LOADING || errorData?.records.length === 0)}
      onClick={handleExportAsCSV}>
      {tc('export_as_csv')}
      <div className="export-sync-errors-button__icon">
        <Tooltip title={t('Reports.SyncErrors.export_as_csv_help')} placement="topRight">
          <Icon theme="filled" type="question-circle" />
        </Tooltip>
      </div>
    </Button>
  );
};
