//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { useCallback, useMemo, useState } from 'react';
import { ColDef, GridApi } from 'ag-grid-community';
import { Button, Modal, message } from 'antd';
import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import Can from 'components/Can';
import { AllPermissions } from 'utils/PermissionsConstants';
import { useGetGhostAccessQuery, useRevokeGhostAccessMutation } from 'store/user/api';
import { GhostAccessAudit } from 'store/user/types';
import { useEnhancedSelector as useSelector } from 'hooks/redux';
import useUserLocalMoment from 'hooks/moment';
import { SHORT_DATE_TIME_FORMAT } from 'utils/DateUtil';

const ActiveAccessTable = () => {
  const [gridApi, setGridApi] = useState<GridApi>();
  const { data, isLoading, refetch } = useGetGhostAccessQuery({ status: 'ACTIVE' });
  const [revokeGhostAccess] = useRevokeGhostAccessMutation();
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

  const handleRevoke = useCallback(
    (record: GhostAccessAudit) => {
      Modal.confirm({
        title: 'Revoke Ghost Access',
        content: `Are you sure you want to revoke access for ${record.requesterEmail} to ${record.syncariId}?`,
        onOk: async () => {
          try {
            await revokeGhostAccess({
              userId: record.requesterId,
              syncariId: record.syncariId,
            }).unwrap();
            message.success('Access revoked successfully');
            refetch();
          } catch (error: any) {
            message.error(error?.data?.message || 'Failed to revoke access');
          }
        },
      });
    },
    [revokeGhostAccess, refetch]
  );

  const ActionButtons = useCallback(
    (params: any) => (
      <Can permission={AllPermissions.LIST_ORG}>
        <Button type="link" size="small" onClick={() => handleRevoke(params.data)}>
          Revoke
        </Button>
      </Can>
    ),
    [handleRevoke]
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
        headerName: 'Granted At',
        field: 'approvedAt',
        sortable: true,
        valueFormatter: (params) => (params.value ? userMoment(params.value).format(SHORT_DATE_TIME_FORMAT) : '-'),
      },
      {
        headerName: 'Expires At',
        field: 'expireAt',
        sortable: true,
        valueFormatter: (params) => (params.value ? userMoment(params.value).format(SHORT_DATE_TIME_FORMAT) : '-'),
      },
      {
        headerName: 'Reason',
        field: 'reason',
        flex: 1,
      },
      {
        headerName: 'Actions',
        field: 'actions',
        cellRenderer: 'actionButtons',
        pinned: 'right',
        maxWidth: 120,
      },
    ],
    [userMoment]
  );

  const frameworkComponents = useMemo(
    () => ({
      actionButtons: ActionButtons,
    }),
    [ActionButtons]
  );

  return (
    <AgTable
      rowData={filteredData || []}
      columnDefs={columns}
      loading={isLoading}
      frameworkComponents={frameworkComponents}
      pagination={true}
      paginationPageSize={20}
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

export default ActiveAccessTable;
