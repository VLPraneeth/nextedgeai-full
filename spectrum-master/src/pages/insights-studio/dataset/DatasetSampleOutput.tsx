//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Tooltip, Icon } from 'antd';
import cx from 'classnames';
import { useMemo } from 'react';

import { ReactComponent as RefreshIcon } from 'assets/images/refresh-icon.svg';
import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import { withI18n } from 'components/I18nProvider';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import { Divider, HStack, Stack } from 'components/layout';
import TabPanelSpin from 'components/TabPanelSpin';
import { Text } from 'components/typography';
import { DatasetRecordTable } from 'store/insights-studio/types';
import { format as formatDate, SHORT_DATE_TIME_FORMAT } from 'utils/DateUtil';
import { tc } from 'utils/i18nUtil';
import './DatasetSampleOutput.less';

export interface DatasetSampleOutputProps {
  getDatasetPreview: () => Promise<void>;
  datasetPreviewResult: DatasetRecordTable;
  datasetConfigPreviewChanged: boolean;
}

const DatasetSampleOutput = ({
  datasetConfigPreviewChanged,
  datasetPreviewResult,
  getDatasetPreview,
}: DatasetSampleOutputProps) => {
  const { data: rowData, columns, isLoading, errorMessage, lastRefreshDate } = datasetPreviewResult;

  const lastRefreshed = useMemo(
    () => `Last refresh: ${!lastRefreshDate ? 'Never' : formatDate(lastRefreshDate, SHORT_DATE_TIME_FORMAT)}`,
    [lastRefreshDate]
  );

  return (
    <div className="dataset-sample-output">
      <Divider y="sm" />
      <div className="dataset-sample-output__container">
        <HStack justify="start">
          <Text weight="semibold" color="gray-900" size="md">
            {tc('preview')}
          </Text>
          <Tooltip title="Preview up to 20 records in the Data Set based on this configuration.">
            <Icon type="question-circle" theme="filled" />
          </Tooltip>
          <Tooltip title={lastRefreshed}>
            <RefreshIcon
              onClick={getDatasetPreview}
              className={cx('dataset-sample-output__refresh-icon', {
                'with-changes': datasetConfigPreviewChanged,
                rotate: isLoading,
              })}
            />
          </Tooltip>
        </HStack>
        <Divider y="sm" />

        <Stack fill>
          <div className="dataset-sample-output__error">
            <InlineMessage allowMultiline type={InlineMessageTypes.ERROR} title={errorMessage}>
              {errorMessage}
            </InlineMessage>
          </div>
          {isLoading ? (
            <TabPanelSpin className="dataset-sample-output__loading" spinning tip="Loading sample data set result…" />
          ) : (
            <AgTable
              className="dataset-sample-output__table"
              columnDefs={columns}
              rowData={rowData}
              sizeColumnsToFit={ResizeColumnsCondition.WHEN_NARROWER}
              suppressCellSelection
              enableCellTextSelection
              colResizeDefault="shift"
            />
          )}
        </Stack>
      </div>
    </div>
  );
};

export default withI18n(DatasetSampleOutput, 'Dataset');
