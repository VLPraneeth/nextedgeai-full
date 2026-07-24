import { useCallback, useState } from 'react';

import Button from 'components/Button';
import { Stack } from 'components/layout';
import { ScrollableArea } from 'components/scrollable-area/ScrollableArea';
import { useInsightsViewContext } from 'pages/insights-studio/context/InsightsViewContext';
import { useUnifiedDataCardAuthoring } from 'pages/insights-studio/utils/useUnifiedDataCardAuthoring';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tc } from 'utils/i18nUtil';

import { DatasetReviewTable } from './DatasetReviewTable';
import DataSetUsedBy from './UsedByList';

export interface DatasetReviewStepProps {
  onCancel: () => void;
  onSuccess: () => void;
  onPrevious: () => void;
}

export const DatasetReviewStep = ({ onCancel, onPrevious, onSuccess }: DatasetReviewStepProps) => {
  const { saveAndClose, datasetId, isDatasetUpdating, isDatasetCreating } = useUnifiedDataCardAuthoring();
  const [errorMessage, setErrorMessage] = useState('');
  const { isThoughtSpotView } = useInsightsViewContext();

  const save = useCallback(() => {
    setErrorMessage('');
    saveAndClose()
      ?.unwrap()
      .then(() => {
        onSuccess();
      })
      .catch((error) => {
        setErrorMessage(getRtkQueryErrorMessage(error));
      });
  }, [onSuccess, saveAndClose]);

  return (
    <div>
      <ScrollableArea bottomOffset={52}>
        <Stack spacing="xxxs" className="data-set-used-by">
          <DatasetReviewTable errorMessage={errorMessage} />
        </Stack>
        {datasetId && !isThoughtSpotView && <DataSetUsedBy usedById={datasetId} type="DATASET" />}
      </ScrollableArea>
      <div className="synri-drawer-panel__footer">
        <Button onClick={onCancel}>{tc('cancel')}</Button>
        <Button onClick={onPrevious}>{tc('previous')}</Button>
        <Button
          type="primary"
          htmlType="submit"
          form="data-card-form"
          onClick={save}
          loading={isDatasetCreating || isDatasetUpdating}>
          {tc('save')} & {tc('finish')}
        </Button>
      </div>
    </div>
  );
};
