//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { useMemo, useState } from 'react';
import { ColDef, GridApi } from 'ag-grid-community';
import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import StatusBadge, { StatusBadgeType } from 'components/StatusBadge';
import { useGetGhostAccessQuery } from 'store/user/api';
import { GhostAccessAudit } from 'store/user/types';
import { useEnhancedSelector as useSelector } from 'hooks/redux';
import useUserLocalMoment from 'hooks/moment';
import { SHORT_DATE_TIME_FORMAT } from 'utils/DateUtil';

const AuditHistoryTable = () => {
  const [gridApi, setGridApi] = useState<GridApi>();
  const { data, isLoading } = useGetGhostAccessQuery(); // All records
  const userMoment = useUserLocalMoment();
  const currentUserId = useSelector((state) => state.user.id);
  const isSuperAdmin = useSelector((state) => state.user.isSuperAdmin);

  // Filter data based on user role
  const filteredData = useMemo(() => {
    if (isSuperAdmin) {
      return data; // Superadmins see all
    }
    // Ghost users see only their own records
    return data?.filter((record: GhostAccessAudit) => record.requesterId === currentUserId);
  }, [data, isSuperAdmin, currentUserId]);

  const StatusBadgeRenderer = useMemo(
    () => (params: any) => {
      const status = params.value;
      switch (status) {
        case 'ACTIVE':
          return <StatusBadge type={StatusBadgeType.SUCCESS}>Active</StatusBadge>;
        case 'COMPLETED':
          return <StatusBadge type={StatusBadgeType.DEFAULT}>Completed</StatusBadge>;
        case 'ERROR':
          return <StatusBadge type={StatusBadgeType.ERROR}>Error</StatusBadge>;
        default:
          return <StatusBadge type={StatusBadgeType.DEFAULT}>{status}</StatusBadge>;
      }
    },
    []
  );

  const columns: ColDef[] = useMemo(
    () => [
      {
        headerName: 'User',
        field: 'requesterEmail',
        sortable: true,
        filter: 'agTextColumnFilter',
      },
      {
        headerName: 'Granted By',
        field: 'approverEmail',
        sortable: true,
        filter: 'agTextColumnFilter',
      },
      {
        headerName: 'Instance',
        field: 'syncariId',
        sortable: true,
        filter: 'agTextColumnFilter',
      },
      {
        headerName: 'Role',
        field: 'roleName',
        sortable: true,
        filter: 'agTextColumnFilter',
      },
      {
        headerName: 'Requested At',
        field: 'requestedAt',
        sortable: true,
        sort: 'desc',
        valueFormatter: (params) => (params.value ? userMoment(params.value).format(SHORT_DATE_TIME_FORMAT) : '-'),
      },
      {
        headerName: 'Granted At',
        field: 'approvedAt',
        sortable: true,
        valueFormatter: (params) => (params.value ? userMoment(params.value).format(SHORT_DATE_TIME_FORMAT) : '-'),
      },
      {
        headerName: 'Expired At',
        field: 'expireAt',
        sortable: true,
        valueFormatter: (params) => (params.value ? userMoment(params.value).format(SHORT_DATE_TIME_FORMAT) : '-'),
      },
      {
        headerName: 'Status',
        field: 'status',
        sortable: true,
        cellRenderer: 'statusBadge',
        filter: 'agSetColumnFilter',
        maxWidth: 130,
      },
      {
        headerName: 'Reason',
        field: 'reason',
      },
      {
        headerName: 'Access Details',
        field: 'accessDetails',
        flex: 1,
      },
    ],
    [userMoment]
  );

  const frameworkComponents = useMemo(
    () => ({
      statusBadge: StatusBadgeRenderer,
    }),
    [StatusBadgeRenderer]
  );

  return (
    <AgTable
      rowData={filteredData || []}
      columnDefs={columns}
      loading={isLoading}
      frameworkComponents={frameworkComponents}
      pagination={true}
      paginationPageSize={50}
      onGridReady={(params) => setGridApi(params.api)}
      sizeColumnsToFit={ResizeColumnsCondition.ALWAYS}
      domLayout="autoHeight"
      rowHeight={40}
      defaultColDef={{
        resizable: true,
        sortable: true,
      }}
    />
  );
};

export default AuditHistoryTable;
