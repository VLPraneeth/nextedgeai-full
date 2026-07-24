import { Icon, Tooltip } from 'antd';
import { useEffect } from 'react';

import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import { HStack, Stack } from 'components/layout';
import TabPanelSpin from 'components/TabPanelSpin';
import { Text } from 'components/typography';
import { useDatasetPreview } from 'pages/insights-studio/utils/useDatasetPreview';
import { useUnifiedDataCardAuthoring } from 'pages/insights-studio/utils/useUnifiedDataCardAuthoring';
import { numberFormat, t } from 'utils/i18nUtil';

import './DatasetReviewTable.scss';

export interface DatasetReviewTableProps {
  errorMessage?: string;
}
export const DatasetReviewTable = ({ errorMessage }: DatasetReviewTableProps) => {
  const { displayName, getDatasetAndDataCard } = useUnifiedDataCardAuthoring();
  const { getDatasetPreview, datasetPreviewResult, getTotalCount, totalCountResult } = useDatasetPreview({
    getDatasetAndDataCard,
  });

  const {
    data: previewData,
    columns: previewColumn,
    isLoading: datasetIsLoading,
    errorMessage: previewErrorMessage,
  } = datasetPreviewResult;
  useEffect(() => {
    getDatasetPreview();
    getTotalCount();
    // Fetch the preview result on mount only
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const localErrorMessage = errorMessage || previewErrorMessage;

  return (
    <Stack spacing="xxxs" className="dataset-review-table">
      <div className="dataset-review-table__error">
        <InlineMessage allowMultiline type={InlineMessageTypes.ERROR} title={localErrorMessage}>
          {localErrorMessage}
        </InlineMessage>
      </div>
      {datasetIsLoading ? (
        <TabPanelSpin className="dataset-review-table__loading" spinning tip="Loading sample data set result…" />
      ) : (
        <Stack spacing="xxxs">
          <HStack justify="space-between">
            <Text color="gray-900" lineHeight="loose" size="lg" weight="bold">
              {displayName || ''}
            </Text>
            <Tooltip title={totalCountResult?.errorMessage}>
              <Text color="gray-900" lineHeight="loose">
                {t('InsightsStudio.total_records')}
                {totalCountResult?.errorMessage ? <Icon type="warning" /> : numberFormat(totalCountResult?.count)}
              </Text>
            </Tooltip>
          </HStack>
          <AgTable
            className="dataset-review-table__table"
            columnDefs={previewColumn}
            rowData={previewData}
            sizeColumnsToFit={ResizeColumnsCondition.WHEN_NARROWER}
            suppressCellSelection
            enableCellTextSelection
            colResizeDefault="shift"
          />
        </Stack>
      )}
    </Stack>
  );
};
