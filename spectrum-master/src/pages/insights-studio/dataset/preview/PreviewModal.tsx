//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { navigate } from '@reach/router';
import { ColDef, ColGroupDef } from 'ag-grid-community';
import { Button, Icon, message, Tooltip } from 'antd';
import ObjectID from 'bson-objectid';
import cx from 'classnames';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { ReactComponent as ExportIcon } from 'assets/icons/export-icon.svg';
import { CursorBasedPagination } from 'components/AgTable';
import AgTable, { ResizeColumnsCondition } from 'components/AgTable/AgTable';
import { CursorPageInfo } from 'components/AgTable/Pagination';
import Can from 'components/Can';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { HStack } from 'components/layout';
import Modal from 'components/Modal';
import { Text } from 'components/typography';
import { useCursorPagination } from 'hooks/pagination';
import { useUserHasPermission } from 'hooks/useUserHasPermission';
import { useInsightsViewContext } from 'pages/insights-studio/context/InsightsViewContext';
import { useUnifiedDataCardAuthoringContext } from 'pages/insights-studio/context/UnifiedDataCardAuthoringContext';
import { makeDatasetResult } from 'pages/insights-studio/utils/UnifiedDataCard.util';
import { useUnifiedDataCardNavigate } from 'pages/insights-studio/utils/useUnifiedDataCardNavigate';
import { TEN_SECONDS } from 'store/api/constants';
import {
  useCreateExportMutation,
  useGetDatasetsQuery,
  useLazyGetCountQuery,
  useLazyGetExportJobsQuery,
  useReadDataMutation,
} from 'store/insights-studio';
import { DatasetExportJob } from 'store/insights-studio/types';
import { format as formatDate, SHORT_DATE_TIME_FORMAT } from 'utils/DateUtil';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { numberFormat, tc, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { ActionRenderer } from './ActionRenderer';
import { ConfirmationModal } from './ConfirmationModal';
import './PreviewModal.scss';

const tn = tNamespaced('InsightsStudio');

export type ExportJobActions = 'DELETE' | 'CANCEL';

const PreviewModal = () => {
  const {
    previewDatasetMatch,
    navigateToCurrentDashboard,
    thoughtspotPreviewDatasetMatch,
  } = useUnifiedDataCardNavigate();

  const { isThoughtSpotView } = useInsightsViewContext();

  const datasetId = useMemo(() => {
    return previewDatasetMatch?.datasetId || thoughtspotPreviewDatasetMatch?.datasetId;
  }, [previewDatasetMatch, thoughtspotPreviewDatasetMatch]);

  // When in thoughtspot routes, always use thoughtspot datasets (matching DatasetList behavior)
  const isThoughtspotRoute = window.location.pathname.toLowerCase().includes('insights-studio/ts/');
  const { data: datasets } = useGetDatasetsQuery(isThoughtspotRoute || isThoughtSpotView);
  const [createExport, { isLoading: exporting }] = useCreateExportMutation();
  const [fetchExportJobs, { data: exportJobs, isFetching: exportJobsFetching }] = useLazyGetExportJobsQuery({
    pollingInterval: datasetId ? TEN_SECONDS : undefined,
  });
  const [fetchTotalCount, { data: countData, error: countError }] = useLazyGetCountQuery();
  const [fetchReadData, { isLoading: readDataFetching }] = useReadDataMutation();

  const { configMode } = useUnifiedDataCardAuthoringContext();

  const [preview, setPreview] = useState<ReturnType<typeof makeDatasetResult>>();
  const [previewError, setPreviewError] = useState<string>();
  const [datasetPageInfo, setDatasetPageInfo] = useState<CursorPageInfo>();

  const {
    cursor,
    pageSize,
    resetPagination,
    setPageSize,
    direction,
    onRequestNextPage,
    onRequestPrevPage,
  } = useCursorPagination();

  const [exportJobId, setExportJobId] = useState<string>();
  const [exportJobAction, setExportJobAction] = useState<ExportJobActions>();
  const [isConfirmationModalOpen, setIsConfirmationModalOpen] = useState(false);

  const { userHasPermission } = useUserHasPermission();

  useEffect(() => {
    if (!datasetId) {
      setPreviewError('');
    }
  }, [datasetId]);

  const dataset = useMemo(() => {
    return datasetId ? datasets?.find((dataset) => dataset.id === datasetId) : null;
  }, [datasets, datasetId]);

  useEffect(() => {
    if (dataset) {
      fetchTotalCount({ dataset, mode: configMode });
      fetchExportJobs(dataset.id);
      resetPagination();
    }
  }, [dataset, fetchTotalCount, fetchExportJobs, resetPagination, configMode]);

  useEffect(() => {
    if (dataset) {
      fetchReadData({
        dataset,
        pageCursor: {
          cursor,
          pageSize,
          direction,
        },
        previousTotalCount: datasetPageInfo?.totalCount,
      })
        .unwrap()
        .then((data) => {
          setDatasetPageInfo(data?.pageInfo);
          setPreview(makeDatasetResult(data));
        })
        .catch((error) => {
          setPreview(undefined);
          setPreviewError(getRtkQueryErrorMessage(error));
        });
    }
  }, [pageSize, cursor, dataset, fetchReadData, datasetPageInfo?.totalCount, direction]);

  const count = useMemo(() => {
    const result = countData && makeDatasetResult(countData);
    return !countError ? Object.values(result?.data?.[0] || {})?.[0] : 0;
  }, [countData, countError]);

  const handleConfirmationModalOpen = useCallback((action: ExportJobActions, exportJobId: string) => {
    setExportJobId(exportJobId);
    setIsConfirmationModalOpen(true);
    setExportJobAction(action);
  }, []);

  const columns: (ColDef | ColGroupDef)[] = useMemo(() => {
    return [
      {
        headerName: tc('username'),
        field: 'userName',
        resizable: true,
      },
      {
        headerName: tc('requested_date'),
        field: 'requestedTime',
        resizable: true,
        cellRendererFramework: ({ data }: { data: DatasetExportJob }) => {
          return <span className="ag-cell-value">{formatDate(data.requestedTime, SHORT_DATE_TIME_FORMAT)}</span>;
        },
      },
      {
        headerName: tc('no_of_records'),
        field: 'numberOfRecords',
        resizable: true,
        cellRendererFramework: ({ data }: { data: DatasetExportJob }) => {
          return (
            <span className="ag-cell-value">{data.numberOfRecords ? numberFormat(data.numberOfRecords) : '-'}</span>
          );
        },
      },
      {
        headerName: tc('expiration_date'),
        field: 'expiredTime',
        resizable: true,
        cellRendererFramework: ({ data }: { data: DatasetExportJob }) => {
          return <span className="ag-cell-value">{formatDate(data.expiredTime, SHORT_DATE_TIME_FORMAT)}</span>;
        },
      },
      {
        headerName: tc('status'),
        field: 'status',
        cellRendererFramework: ({ data }: { data: DatasetExportJob }) => {
          return (
            <span className="ag-cell-value dataset-preview-modal__export-status">{data?.status?.toLowerCase()}</span>
          );
        },
      },
      {
        headerName: tc('actions'),
        resizable: true,
        field: 'status',
        pinned: 'right',
        cellRendererFramework: ({ data }: { data: DatasetExportJob }) => {
          return (
            <ActionRenderer data={data} dataset={dataset} handleConfirmationModalOpen={handleConfirmationModalOpen} />
          );
        },
      },
    ];
  }, [dataset, handleConfirmationModalOpen]);

  const datasetColumns = useMemo(() => {
    return datasetId ? preview?.columns : [];
  }, [datasetId, preview?.columns]);

  const datasetPreview = useMemo(() => {
    return preview?.data?.map((data) => ({ ...data, id: ObjectID.generate() }));
  }, [preview?.data]);

  const validExportJobs = useMemo(() => {
    return exportJobs?.filter((job) => job.status !== 'CANCELLED' && job.status !== 'ERROR');
  }, [exportJobs]);

  const exportLimitReached = useMemo(() => {
    return (validExportJobs?.length || 0) >= 10;
  }, [validExportJobs?.length]);

  const handleClose = useCallback(() => {
    if (isThoughtSpotView) {
      navigate(makeUrl(RouteConstants.INSIGHTS_STUDIO_TS_DATASETS));
    } else {
      navigateToCurrentDashboard();
    }
  }, [navigateToCurrentDashboard, isThoughtSpotView]);

  return (
    <Modal
      title={tn('preview_dataset')}
      centered
      visible={!!datasetId}
      width="90%"
      onOk={handleClose}
      onCancel={handleClose}
      footer={
        <Button type="primary" onClick={handleClose}>
          {tc('close')}
        </Button>
      }
      destroyOnClose>
      <div className="dataset-preview-modal">
        <InlineMessage allowMultiline type={InlineMessageTypes.ERROR} title={previewError}>
          {previewError}
        </InlineMessage>
        <HStack justify="space-between">
          <Text color="gray-900" lineHeight="loose" size="lg" weight="bold">
            {dataset?.displayName || ''}
          </Text>

          <div>
            {tn('total_records')} {numberFormat(count)}
          </div>
        </HStack>

        <AgTable
          className={cx('dataset-preview-modal__table', !preview?.data?.length && 'empty')}
          domLayout="autoHeight"
          loading={readDataFetching}
          columnDefs={datasetColumns}
          rowData={datasetPreview}
          sizeColumnsToFit={ResizeColumnsCondition.WHEN_NARROWER}
          suppressCellSelection
          enableCellTextSelection
          colResizeDefault="shift"
          getRowNodeId={(data) => data.id}
          pagerComponent={
            <CursorBasedPagination
              pageInfo={datasetPageInfo}
              onRequestNextPage={onRequestNextPage}
              onRequestPreviousPage={onRequestPrevPage}
              pageSize={pageSize}
              onPageSizeChange={setPageSize}
              allowPageSizeChange
            />
          }
        />

        <InputWithLabel
          label={tn('export_dataset')}
          tooltip={tn('Tooltips.export_dataset')}
          input={
            <ExportTooltipWrapper exportLimitReached={exportLimitReached}>
              <Button
                className="dataset-preview-modal__export-button"
                disabled={exportLimitReached}
                onClick={() => {
                  if (dataset) {
                    createExport(dataset)
                      .unwrap()
                      .catch((error) => message.error(getRtkQueryErrorMessage(error)));
                  }
                }}>
                {exporting ? <Icon type="loading" /> : <Icon component={(props) => <ExportIcon {...props} />} />}
                {tc('export')}
              </Button>
            </ExportTooltipWrapper>
          }
        />

        {userHasPermission(AllPermissions.VIEW_EXPORT_JOBS) && !!exportJobs?.length && (
          <AgTable
            className="dataset-preview-modal__table"
            domLayout="autoHeight"
            loading={exportJobsFetching}
            columnDefs={columns}
            sizeColumnsToFit={ResizeColumnsCondition.WHEN_NARROWER}
            suppressCellSelection
            enableCellTextSelection
            colResizeDefault="shift"
            rowData={exportJobs}
            getRowNodeId={(data: DatasetExportJob) => data.exportJobId}
          />
        )}
      </div>

      <ConfirmationModal
        isModalOpen={isConfirmationModalOpen}
        setIsModalOpen={setIsConfirmationModalOpen}
        exportJobAction={exportJobAction}
        exportJobId={exportJobId}
      />
    </Modal>
  );
};

export default PreviewModal;

function ExportTooltipWrapper({ exportLimitReached, children }: any) {
  const { userHasPermission } = useUserHasPermission();
  if (userHasPermission(AllPermissions.EXPORT_DATASET)) {
    return <Tooltip title={exportLimitReached && tn('Tooltips.export_dataset_disabled')}>{children}</Tooltip>;
  } else {
    return <Can permission={AllPermissions.EXPORT_DATASET}>{children}</Can>;
  }
}
