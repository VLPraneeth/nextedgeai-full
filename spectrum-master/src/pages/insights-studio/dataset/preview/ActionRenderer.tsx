import { Button, Icon, message, Tooltip } from 'antd';
import { useState } from 'react';

import { ReactComponent as CloseIcon } from 'assets/icons/close-icon.svg';
import { ReactComponent as ExportIcon } from 'assets/icons/export-icon.svg';
import { ReactComponent as TrashIcon } from 'assets/icons/Trash.svg';
import Can from 'components/Can';
import { Dataset, DatasetExportJob } from 'store/insights-studio/types';
import DataUrlConstants from 'utils/DataUrlConstants';
import { downloadCsvDataAsFile } from 'utils/DownloadUtil';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tc } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import { makeUrl } from 'utils/UrlUtil';

import { ExportJobActions } from './PreviewModal';

export interface ActionRendererProps {
  data: DatasetExportJob;
  dataset: Dataset | null | undefined;
  handleConfirmationModalOpen: (action: ExportJobActions, exportJobId: string) => void;
}

export function ActionRenderer({ data, dataset, handleConfirmationModalOpen }: ActionRendererProps) {
  const [downloading, setDownloading] = useState(false);

  if (downloading) {
    return <Icon type="loading" className="dataset-preview-modal__loading-icon" />;
  }
  if (data.status === 'PENDING') {
    return (
      <>
        <Tooltip title={tc('cancel')}>
          <Can permission={AllPermissions.CANCEL_EXPORT}>
            <Button
              type="link"
              className="dataset-preview-modal__action-btn"
              onClick={() => {
                if (data.exportJobId) {
                  handleConfirmationModalOpen('CANCEL', data.exportJobId);
                }
              }}>
              <Icon component={(props) => <CloseIcon {...props} />} aria-label={tc('cancel')} role="button" />
            </Button>
          </Can>
        </Tooltip>
        <DeleteAction exportJobId={data.exportJobId} handleConfirmationModalOpen={handleConfirmationModalOpen} />
      </>
    );
  }
  if (data.status === 'COMPLETED') {
    return (
      <>
        <Tooltip title={tc('download')}>
          <Can permission={AllPermissions.DOWNLOAD_EXPORTED_DATASET}>
            <Button
              className="dataset-preview-modal__action-btn"
              type="link"
              onClick={() => {
                if (dataset) {
                  setDownloading(true);
                  downloadCsvDataAsFile(
                    dataset.displayName + '.csv',
                    makeUrl(DataUrlConstants.INSIGHTS_DATASET_DOWNLOAD, { exportJobId: data.exportJobId })
                  )
                    .catch((error) => message.error(getRtkQueryErrorMessage(error)))
                    .finally(() => setDownloading(false));
                }
              }}>
              <Icon component={(props) => <ExportIcon {...props} />} aria-label={tc('download')} role="button" />
            </Button>
          </Can>
        </Tooltip>
        <DeleteAction exportJobId={data.exportJobId} handleConfirmationModalOpen={handleConfirmationModalOpen} />
      </>
    );
  }

  return <DeleteAction exportJobId={data.exportJobId} handleConfirmationModalOpen={handleConfirmationModalOpen} />;
}

interface DeleteActionProps {
  exportJobId: string;
  handleConfirmationModalOpen: (action: ExportJobActions, exportJobId: string) => void;
}

function DeleteAction({ exportJobId, handleConfirmationModalOpen }: DeleteActionProps) {
  return (
    <Tooltip title={tc('delete')}>
      <Can permission={AllPermissions.DELETE_EXPORT}>
        <Button
          type="link"
          className="dataset-preview-modal__action-btn"
          onClick={() => {
            if (exportJobId) {
              handleConfirmationModalOpen('DELETE', exportJobId);
            }
          }}>
          <Icon component={(props) => <TrashIcon {...props} />} aria-label={tc('delete')} role="button" />
        </Button>
      </Can>
    </Tooltip>
  );
}
