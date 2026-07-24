import { GridApi, RowSelectedEvent, ValueFormatterParams } from 'ag-grid-community';
import { Icon, Tooltip } from 'antd';
import _ from 'lodash';
import { useCallback, useEffect, useState } from 'react';

import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import Button from 'components/Button';
import Can, { PermissionErrorModes } from 'components/Can';
import { Stack } from 'components/layout';
import StatusBadge, { StatusBadgeType } from 'components/StatusBadge';
import { Text } from 'components/typography';
import { useBreadcrumb } from 'pages/breadcrumbs/useBreadcrumb';
import { useGetAllRolesQuery } from 'store/access-control/api';
import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';

import ActionMenu from './ActionMenu';
import AddRoleWizard, { RoleStatus } from './add-role/AddRoleWizard';
import EditRoleWizard from './add-role/EditRoleWizard';
import RoleDetails from './RoleDetails';

export enum RBACModalVariants {
  ADD = 'add',
  EDIT = 'edit',
  DELETE = 'delete',
  DETAILS = 'details',
}

const tn = tNamespaced('Settings.AccessControl');
const th = tNamespaced('Settings.AccessControl.RoleBasedTableHeaders');

export default function RoleBasedAccessControl({ path }: { path: string }) {
  const [gridApi, setGridApi] = useState<GridApi>();
  const { data, isFetching, error } = useGetAllRolesQuery();

  const [selectedRoleId, setSelectedRoleId] = useState<null | string>(null);
  const [modalVariant, setModalVariant] = useState<null | RBACModalVariants>(null);
  const [currentStepIndex, setCurrentStepIndex] = useState(0);

  const columns = [
    {
      headerName: th('name'),
      field: 'name',
      cellRenderer: 'roleName',
      sortable: true,
    },
    // privileges is an old verbiage still used in api. We use permission since its much easier to spell :)
    {
      headerName: th('permissions'),
      field: 'privileges',
      cellRenderer: 'permissionsCount',
      sortable: true,
    },
    {
      headerName: th('users'),
      field: 'users',
      cellRenderer: 'usersCount',
      sortable: true,
    },
    {
      headerName: th('status'),
      field: 'active',
      cellRenderer: 'status',
      sortable: true,
    },
    {
      headerName: '',
      colId: 'actions',
      field: 'actions',
      cellRenderer: 'dropdownMenu',
      width: 30,
      pinned: 'right',
    },
  ];

  const frameworkComponents = {
    dropdownMenu: (event: any) => {
      return (
        <ActionMenu roleId={event.data.id} setSelectedRoleId={setSelectedRoleId} setModalVariant={setModalVariant} />
      );
    },
    status: (event: any) => {
      const status = event.value ? StatusBadgeType.SUCCESS : StatusBadgeType.DEFAULT;

      return <StatusBadge type={status}>{event.value ? RoleStatus.active : RoleStatus.inactive}</StatusBadge>;
    },
    permissionsCount: (params: ValueFormatterParams) => params.value.length,
    usersCount: (params: ValueFormatterParams) => params.value.length,
    roleName: (event: any) => {
      return (
        <div>
          <Tooltip title={event.value} mouseEnterDelay={AppConstants.TOOLTIP_DELAY_SECONDS}>
            <Text>{event.value}</Text>
          </Tooltip>{' '}
          {event.data.system && (
            <Tooltip title={tn('system_default_role')} mouseEnterDelay={AppConstants.TOOLTIP_DELAY_SECONDS}>
              <Icon style={{ color: '#595959' }} type="lock" />
            </Tooltip>
          )}
        </div>
      );
    },
  };

  const onRowSelected = useCallback(
    (event: RowSelectedEvent) => {
      if (event.node.isSelected()) {
        setModalVariant(RBACModalVariants.DETAILS);
        setSelectedRoleId(event.data.id);
        gridApi?.deselectAll();
      }
    },
    [gridApi]
  );

  const addRole = () => {
    setModalVariant(RBACModalVariants.ADD);
  };

  const sortedData = _.sortBy(data, ['system', (user) => user.name.toLowerCase()]);

  const { setUrlName } = useBreadcrumb();

  useEffect(() => {
    setUrlName(RouteConstants.SETTINGS_ACCESS_CONTROL, tn('access_control_window_title'));
    setUrlName(RouteConstants.SETTINGS_RBAC, tn('role_based_window_title'));
  }, [setUrlName]);

  return (
    <div>
      <Stack>
        <Can permission={AllPermissions.ADD_ROLE}>
          <Button type="primary" onClick={addRole}>
            {tn('new_role')}
          </Button>
        </Can>
        <Can permission={AllPermissions.LIST_ROLES} errorMode={PermissionErrorModes.ReplaceWithText}>
          <AgTable
            rowHeight={40}
            domLayout="autoHeight"
            rowData={sortedData}
            rowStyle={{ display: 'flex', alignItems: 'center', cursor: 'pointer' }}
            rowSelection="single"
            onRowSelected={onRowSelected}
            sizeColumnsToFit={ResizeColumnsCondition.ALWAYS}
            columnDefs={columns}
            loading={isFetching}
            frameworkComponents={frameworkComponents}
            suppressCellSelection
            disableRowSelectionForCells={['dropdownMenu']}
            error={error as Error}
            onGridReady={(evt) => setGridApi(evt.api)}
          />
        </Can>
      </Stack>
      <AddRoleWizard
        visible={modalVariant === RBACModalVariants.ADD}
        currentStepIndex={currentStepIndex}
        setCurrentStepIndex={setCurrentStepIndex}
        close={() => setModalVariant(null)}
      />

      <EditRoleWizard
        visible={modalVariant === RBACModalVariants.EDIT && !isFetching && selectedRoleId !== null}
        currentStepIndex={currentStepIndex}
        setCurrentStepIndex={setCurrentStepIndex}
        close={() => setModalVariant(null)}
        selectedRoleId={selectedRoleId!}
      />
      <RoleDetails
        visible={modalVariant === RBACModalVariants.DETAILS && !isFetching && selectedRoleId !== null}
        close={() => setSelectedRoleId(null)}
        selectedRoleId={selectedRoleId!}
      />
    </div>
  );
}
