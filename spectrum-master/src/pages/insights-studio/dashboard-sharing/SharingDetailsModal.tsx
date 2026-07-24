import { ColDef, ColGroupDef, GridApi, GridReadyEvent } from 'ag-grid-community';
import { Button, Col, Icon, Input, message, Tooltip } from 'antd';
import cx from 'classnames';
import { useCallback, useEffect, useMemo, useState } from 'react';

import './SharingDetails.scss';
import { CursorBasedPagination } from 'components/AgTable';
import AgTable from 'components/AgTable/AgTable';
import Can from 'components/Can';
import DrawerPanel from 'components/DrawerPanel';
import { HStack } from 'components/layout';
import Modal from 'components/Modal';
import { useUtcTimeInUsersTimezone } from 'hooks/moment';
import { useCursorPagination } from 'hooks/pagination';
import useDebounce from 'hooks/useDebounce';
import {
  useDeleteSharingDetailsMutation,
  useLazyGetSharingDetailsQuery,
  useReshareMutation,
} from 'store/insights-studio';
import { ShareDetailsRecord } from 'store/insights-studio/types';
import { packageData } from 'utils/ErrorUtils';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import { humanize } from 'utils/StringUtil';

import { useUnifiedDataCardNavigate } from '../utils/useUnifiedDataCardNavigate';
import { ExpiryDatePicker } from './ExpiryDatePicker';
const tn = tNamespaced('InsightsStudio');

export function SharingDetails() {
  const { dashboardSharingDetailsMatch, navigateToCurrentDashboard } = useUnifiedDataCardNavigate();
  const [
    fetchSharingDetails,
    { isFetching: sharingDetailsLoading, data: sharingDetails },
  ] = useLazyGetSharingDetailsQuery();
  const [deleteSharingDetails, { isLoading: isDeleting }] = useDeleteSharingDetailsMutation();
  const [reshare, { isLoading: isResharing }] = useReshareMutation();
  const [gridApi, setGridApi] = useState<GridApi>();
  const [emailSearch, setEmailSearch] = useState<string>('');
  const debouncedEmailSearch = useDebounce<string>(emailSearch, 1000);

  const [selectedRowsCount, setSelectedRowsCount] = useState(0);
  const utcToLocal = useUtcTimeInUsersTimezone();

  const onGridReady = (params: GridReadyEvent) => {
    setGridApi(params.api);
  };

  const onRowSelected = () => {
    gridApi && setSelectedRowsCount(gridApi?.getSelectedRows()?.length);
  };

  const {
    cursor,
    pageSize,
    resetPagination,
    setPageSize,
    direction,
    onRequestNextPage,
    onRequestPrevPage,
  } = useCursorPagination();

  const columns: (ColDef | ColGroupDef)[] = useMemo(() => {
    return [
      {
        headerName: '',
        field: 'select',
        minWidth: 48,
        maxWidth: 48,
        checkboxSelection: true,
        headerCheckboxSelection: true,
        suppressMovable: true,
      },
      {
        headerName: tc('email_address'),
        field: 'emailId',
        resizable: true,
      },
      {
        headerName: tn('InsightsSharing.invitation_status'),
        field: 'status',
        resizable: true,
        cellRendererFramework: ({ data }: { data: ShareDetailsRecord }) => {
          return <span className="ag-cell-value">{humanize(data.status)}</span>;
        },
      },
      {
        headerName: tn('InsightsSharing.last_visit'),
        field: 'lastVisitedDate',
        resizable: true,
        cellRendererFramework: ({ data }: { data: ShareDetailsRecord }) => {
          return (
            <span className="ag-cell-value">{data?.lastVisitedDate ? utcToLocal(data.lastVisitedDate) : '-'}</span>
          );
        },
      },
      {
        headerName: tn('InsightsSharing.access_expiry'),
        resizable: true,
        editable: true,
        suppressKeyboardEvent: () => true,
        suppressMovable: true,
        field: 'expiryDate',
        valueSetter: () => {
          return true;
        },
        cellEditorFramework: ExpiryDatePicker,
        cellRendererFramework: ({ data }: { data: ShareDetailsRecord }) => {
          return <span className="ag-cell-value">{data?.expiryDate ? utcToLocal(data.expiryDate) : '-'}</span>;
        },
      },
    ];
  }, [utcToLocal]);

  const cleanup = useCallback(() => {
    gridApi?.deselectAll();
    setEmailSearch('');
    resetPagination();
  }, [resetPagination, gridApi]);

  useEffect(() => {
    cleanup();
  }, [dashboardSharingDetailsMatch?.dashboardId, cleanup]);

  useEffect(() => {
    if (dashboardSharingDetailsMatch?.dashboardId) {
      fetchSharingDetails({
        dashboardId: dashboardSharingDetailsMatch?.dashboardId,
        cursor,
        direction,
        pageSize,
        predicate: debouncedEmailSearch ? getPredicate(debouncedEmailSearch) : undefined,
      });
    }
  }, [
    dashboardSharingDetailsMatch?.dashboardId,
    fetchSharingDetails,
    cursor,
    pageSize,
    direction,
    debouncedEmailSearch,
  ]);

  function handleDelete() {
    const ids = gridApi?.getSelectedRows()?.map((row) => row.sharedItemId) || [];

    Modal.confirm({
      title: tn('InsightsSharing.delete_addresses_title'),
      content: tn('InsightsSharing.delete_addresses_content', { count: selectedRowsCount }),
      onOk: () => {
        deleteSharingDetails(ids)
          .unwrap()
          .then(() => {
            message.success(tn('InsightsSharing.emails_deleted', { count: selectedRowsCount }));
          })
          .catch((error) => message.error(getRtkQueryErrorMessage(error)));
      },
      okText: tc('delete'),
      okType: 'danger',
    });
  }

  function handleResendInvite() {
    const ids = gridApi?.getSelectedRows()?.map((row: ShareDetailsRecord) => row.sharedItemId) || [];
    const hasExpired = gridApi?.getSelectedRows()?.some((row: ShareDetailsRecord) => row?.status === 'EXPIRED');

    Modal.confirm({
      title: tn('InsightsSharing.resend_invite_title'),
      content: (
        <>
          <p>{tn('InsightsSharing.resend_invite_content', { count: selectedRowsCount })}</p>
          {hasExpired && <p>{tn('InsightsSharing.resend_invite_after_expiry_content')}</p>}
        </>
      ),
      onOk: () => {
        reshare(ids)
          .unwrap()
          .then((data) => {
            const erroredInvites = data?.filter((d) => d.errorMessage !== null);
            if (erroredInvites.length) {
              if (erroredInvites.length === data.length) {
                message.error(tn('InsightsSharing.error_invite'));
              } else {
                message.warning(tn('InsightsSharing.partial_error_invite'));
              }
            } else {
              message.success(tn('InsightsSharing.invite_sent'));
              gridApi?.deselectAll();
            }
          })
          .catch((error) => message.error(getRtkQueryErrorMessage(error)));
      },
      okText: tc('resend'),
      okType: 'primary',
    });
  }

  function handleClose() {
    cleanup();
    navigateToCurrentDashboard();
  }

  return (
    <DrawerPanel
      absolutePositioning
      onClose={handleClose}
      title={
        <HStack>
          {tn('sharing_details')}
          <Tooltip title={tn('InsightsSharing.sharing_details_tooltip')}>
            <div className={'synri-tooltip'}>
              <Icon type={'question-circle'} theme="filled" />
            </div>
          </Tooltip>
        </HStack>
      }
      visible={!!dashboardSharingDetailsMatch?.dashboardId}
      width="xlarge"
      destroyOnClose
      footer={
        <Button onClick={handleClose} type="primary">
          {tc('close')}
        </Button>
      }>
      <div className="sharing-details">
        <HStack justify="space-between">
          <Col span={8}>
            <Input
              placeholder={tn('InsightsSharing.search_email')}
              value={emailSearch}
              onChange={(e) => {
                setEmailSearch(e.target.value);
                resetPagination();
              }}
            />
          </Col>
          <Col>
            <Can permission={AllPermissions.DELETE_SHARED_DASHBOARD_DETAILS}>
              <Button
                className="sharing-details__delete-button"
                disabled={!gridApi?.getSelectedRows()?.length}
                loading={isDeleting}
                onClick={handleDelete}>
                {tn('InsightsSharing.delete_selected')}
              </Button>
            </Can>
            <Can permission={AllPermissions.SHARE_DASHBOARD}>
              <Button
                disabled={!gridApi?.getSelectedRows()?.length}
                loading={isResharing}
                type="primary"
                onClick={handleResendInvite}>
                {tn('InsightsSharing.resend_invite')}
              </Button>
            </Can>
          </Col>
        </HStack>
        <AgTable
          className={cx('sharing-details__table', !sharingDetails?.shareDetailsRecords.length && 'empty')}
          onGridReady={onGridReady}
          onRowSelected={onRowSelected}
          domLayout="autoHeight"
          columnDefs={columns}
          rowData={sharingDetails?.shareDetailsRecords}
          rowSelection="multiple"
          getRowNodeId={(data) => data?.sharedItemId}
          suppressCellSelection
          loading={sharingDetailsLoading}
          suppressRowClickSelection
          singleClickEdit
          pagerComponent={
            <CursorBasedPagination
              pageInfo={sharingDetails?.pageInfo}
              onRequestNextPage={onRequestNextPage}
              onRequestPreviousPage={onRequestPrevPage}
              pageSize={pageSize}
              onPageSizeChange={(size) => {
                resetPagination();
                setPageSize(size);
              }}
              allowPageSizeChange
              totalRecords={sharingDetails?.pageInfo.totalCount}
            />
          }
        />
      </div>
    </DrawerPanel>
  );
}

function getPredicate(debouncedEmailSearch: string) {
  return packageData({
    predicates: [
      {
        left: {
          dataType: 'string',
          label: 'recipientsEmailId',
          picklistGroup: '',
          type: 'variable',
          value: 'recipientsEmailId',
        },
        operator: 'starts_with',
        right: {
          value: debouncedEmailSearch,
          type: 'literal',
        },
      },
    ],
    operator: 'AND',
  });
}
