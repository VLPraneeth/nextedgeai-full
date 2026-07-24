import { Link, Redirect, RouteComponentProps } from '@reach/router';
import { message } from 'antd';
import Spin from 'antd/lib/spin';
import Tooltip from 'antd/lib/tooltip';
import * as React from 'react';
import { useMemo, useState } from 'react';

import { ReactComponent as ExportIcon } from 'assets/icons/export.svg';
import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import { MAX_AMOUNT_OF_NON_VIRTUALIZED_COLUMNS } from 'components/AgTable/constants';
import DrawerPanel from 'components/DrawerPanel';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { HStack, Stack } from 'components/layout';
import StatusBadge, { StatusBadgeSize, StatusBadgeType } from 'components/StatusBadge';
import { TableFilterButton } from 'components/TableFilters';
import { Text, TranslatedText } from 'components/typography';
import useQueryParams from 'hooks/useQueryParams';
import { useReferenceData, useReferenceDataPreview } from 'store/reference-data';
import { format, LONG_DATETIME_FORMAT_WITH_TZ } from 'utils/DateUtil';
import RouteConstants from 'utils/RouteConstants';
import { humanize } from 'utils/StringUtil';

import ReferenceDataRecordKebabMenu, { ReferenceListAction } from './ReferenceDataRecordKebabMenu';
import ReferenceDataUpsertPanel from './ReferenceDataUpsertPanel';
import { downloadReferenceData } from './utils';

export enum PanelType {
  DETAILS = 'details',
  UPDATE = 'update',
}

export interface ReferenceDataGridQueryParams {
  panel?: PanelType;
}

interface ReferenceDataKeyValueProps {
  titleKey: string;
  children?: React.ReactNode;
  value?: string;
}

export const ReferenceDataKeyValue = ({ titleKey, value, children }: ReferenceDataKeyValueProps) => {
  return (
    <Stack spacing="xxs">
      <TranslatedText as="div" color="gray-700" size="sm" weight="bold" text={titleKey} />
      {value ? <Text color="gray-1000">{value}</Text> : children}
    </Stack>
  );
};

const ReferenceDataGrid = ({ location, navigate, refDataId }: RouteComponentProps<{ refDataId: string }>) => {
  const { tc, tn } = useI18nContext();
  const [{ panel }] = useQueryParams<ReferenceDataGridQueryParams>();
  const { data: record, loading: recordLoading, error } = useReferenceData(refDataId || '');
  const { data: preview, loading: previewLoading } = useReferenceDataPreview(refDataId || '');
  const [isFileExporting, setIsFileExporting] = useState(false);

  const handleClosePanel = () => {
    navigate && location && navigate(location.pathname);
  };

  const columnDefs = useMemo(() => {
    if (!preview) {
      return [];
    }

    const { headerColumns } = preview;

    return headerColumns.map((headerName) => ({
      headerName: humanize(headerName),
      colId: headerName,
      field: headerName,
    }));
  }, [preview]);

  if (error && error.includes(`${refDataId} not found`)) {
    return <Redirect to={RouteConstants.DATA_STUDIO_ROOT} noThrow />;
  }

  return (
    <div className="data-studio-main-content content-section">
      <Stack fill>
        <HStack justify="space-between" className="data-studio-meta-row">
          {record && (
            <HStack spacing="xs">
              <Text color="gray-900" weight="semibold">
                {record.name}
              </Text>
              <Tooltip title={<TranslatedText text="preview_description" />}>
                {/* this div wrapper is needed for tooltip triggering to work properly */}
                <div>
                  <StatusBadge size={StatusBadgeSize.SMALL} type={StatusBadgeType.SUCCESS}>
                    Preview
                  </StatusBadge>
                </div>
              </Tooltip>
            </HStack>
          )}
          <HStack justify="end" spacing="xs">
            {recordLoading || !record ? (
              <Spin spinning size="small" />
            ) : (
              [
                <TableFilterButton
                  loading={isFileExporting}
                  key="export-btn"
                  aria-label={tc('export')}
                  onClick={() => {
                    setIsFileExporting(true);
                    downloadReferenceData(record)
                      .catch(() => message.error(tn('export_failed')))
                      .finally(() => setIsFileExporting(false));
                  }}
                  size="default">
                  <ExportIcon height="20px" width="20px" />
                  <TranslatedText namespace="Common" text="export" />
                </TableFilterButton>,
                <ReferenceDataRecordKebabMenu
                  key="kebab"
                  actionsToExclude={[ReferenceListAction.DOWNLOAD]}
                  referenceData={record}
                />,
              ]
            )}
          </HStack>
        </HStack>

        <AgTable
          // Suppress virtualization when only a few columns appear to fix column width issue
          suppressColumnVirtualisation={columnDefs.length < MAX_AMOUNT_OF_NON_VIRTUALIZED_COLUMNS}
          getRowNodeId={(r) => r.__uniqueId}
          columnDefs={columnDefs}
          loading={previewLoading}
          rowData={preview?.rows}
          sizeColumnsToFit={ResizeColumnsCondition.WHEN_NARROWER}
        />
        <DrawerPanel
          absolutePositioning
          visible={panel === PanelType.DETAILS}
          mask
          maskClosable
          title={
            recordLoading ? (
              <TranslatedText namespace="Common" text="loading" />
            ) : (
              <TranslatedText text="details_title" args={{ name: record?.name }} />
            )
          }
          onClose={handleClosePanel}>
          {recordLoading || !record ? (
            <Spin spinning />
          ) : (
            <Stack spacing="xl">
              <ReferenceDataKeyValue key="details_source_type" titleKey="details_source_type" value={record.type} />
              <ReferenceDataKeyValue key="details_location" titleKey="details_location" value={record.location} />
              <ReferenceDataKeyValue key="details_status" titleKey="details_status" value={record.status} />
              <ReferenceDataKeyValue
                key="details_last_imported"
                titleKey="details_last_imported"
                value={
                  record.lastImported ? format(record.lastImported, LONG_DATETIME_FORMAT_WITH_TZ) : record.lastImported
                }
              />
              <ReferenceDataKeyValue
                key="details_total_records"
                titleKey="details_total_records"
                value={record.totalRecords}
              />
              <ReferenceDataKeyValue key="details_used_in" titleKey="details_used_in">
                <Stack>
                  {record.usedInPipelines?.length > 0 ? (
                    record.usedInPipelines.map((pipeline) => (
                      <Link key={pipeline.id} to={pipeline.path}>
                        {pipeline.name}
                      </Link>
                    ))
                  ) : (
                    <TranslatedText text="details_used_in_empty" />
                  )}
                </Stack>
              </ReferenceDataKeyValue>
            </Stack>
          )}
        </DrawerPanel>
        <ReferenceDataUpsertPanel
          referenceData={record}
          visible={panel === PanelType.UPDATE}
          onRequestClose={handleClosePanel}
        />
      </Stack>
    </div>
  );
};

export default withI18n(ReferenceDataGrid, 'ReferenceDataList');
