//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { RouteComponentProps } from '@reach/router';
import { Button, Dropdown, Icon, Menu, Tooltip } from 'antd';
import { useCallback, useEffect } from 'react';

import AgTable from 'components/AgTable';
import Can from 'components/Can';
import { CapitalizedStringRenderer, truncatedTextRenderer, withAntRenderer } from 'components/renderers';
import { PlaceholderRenderer } from 'components/renderers/PlaceholderRenderer';
import StatusCellRenderer from 'components/renderers/StatusCellRenderer';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import useToastForFetchStatusChange from 'hooks/useToastForFetchStatusChange';
import { useWindowTitle } from 'hooks/windowTitle';
import { getInstances, InstanceCopyModalType, showInstanceCopyModal, showInstanceModal } from 'store/instances/slice';
import { useUserRolesForCurrentInstance } from 'store/user/hooks';
import AppConstants from 'utils/AppConstants';
import CapConstants from 'utils/CapConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

import { InstanceCopyModal } from './InstanceCopyModal';
import InstanceKebabMenu from './InstanceKebabMenu';
import { ManageTrialInstanceModal } from './manage-trial-instance-modal/ManageTrialInstanceModal';

const tn = tNamespaced('Settings.Instances');

const INSTITUTION_LIMIT = 3;

enum InstanceListAction {
  COPY = 'copyInstance',
  REFRESH = 'refreshInstance',
}

const frameworkComponents = {
  dropdownMenu: InstanceKebabMenu,
  status: withAntRenderer(StatusCellRenderer),
  type: withAntRenderer(CapitalizedStringRenderer),
  truncatedText: withAntRenderer(truncatedTextRenderer((text: string) => text)),
  date: withAntRenderer((text: string) => PlaceholderRenderer(text, tc('unknown'), true)),
  placeholder: withAntRenderer((text: string) => PlaceholderRenderer(text, tc('unknown'))),
};

const defaultColDef = { flex: 1 };

// eslint-disable-next-line no-empty-pattern
const InstanceList = ({}: RouteComponentProps) => {
  useWindowTitle(tn('page_title'));
  const dispatch = useEnhancedDispatch();
  const instances = useEnhancedSelector((state) => state.instance.instances);
  const instancesStatus = useEnhancedSelector((state) => state.instance.instancesStatus);
  const instanceCreatingStatus = useEnhancedSelector((state) => state.instance.instanceCreatingStatus);
  const instanceCreatingErrorMessage = useEnhancedSelector((state) => state.instance.instanceCreatingErrorMessage);
  const instanceUpdatingStatus = useEnhancedSelector((state) => state.instance.instanceUpdatingStatus);
  const instanceUpdatingErrorMessage = useEnhancedSelector((state) => state.instance.instanceUpdatingErrorMessage);

  const instanceCopyStatus = useEnhancedSelector((state) => state.instance.instanceCopyStatus);

  const orgType = useEnhancedSelector((state) => state.user.orgType);

  const { userCan } = useUserRolesForCurrentInstance();
  const isSuperAdmin = userCan([CapConstants.SUPER_ADMIN]);

  const colDefs = [
    {
      headerName: tc('syncari_id'),
      field: 'syncariId',
      resizable: true,
    },
    {
      headerName: tc('name'),
      field: 'name',
      cellRenderer: 'truncatedText',
      resizable: true,
    },
    {
      headerName: tc('display_name'),
      field: 'displayName',
      cellRenderer: 'truncatedText',
      resizable: true,
    },
    {
      headerName: tc('type'),
      field: 'type',
      cellRenderer: 'type',
      resizable: true,
    },
    {
      headerName: tc('status'),
      field: 'status',
      cellRenderer: 'status',
    },
    {
      headerName: tc('plan_name'),
      field: 'planName',
      resizable: true,
      cellRenderer: 'placeholder',
    },
    {
      headerName: tc('created_by'),
      field: 'createdBy',
      resizable: true,
      cellRenderer: 'placeholder',
    },
    {
      headerName: tc('created_at'),
      field: 'createdAt',
      cellRenderer: 'date',
      resizable: true,
    },
    {
      headerName: tc('actions'),
      field: 'actions',
      cellRenderer: 'dropdownMenu',
      // Make the actions column fixed size
      flex: 0,
      width: 100,
    },
  ];

  const createStatus = useToastForFetchStatusChange(instanceCreatingStatus, {
    loading: tn('create_instance_pending'),
    success: tn('create_instance_success'),
    error: instanceCreatingErrorMessage ?? tn('create_instance_error'),
  });

  const updateStatus = useToastForFetchStatusChange(instanceUpdatingStatus, {
    loading: tn('edit_instance_pending'),
    success: tn('edit_instance_success'),
    error: instanceUpdatingErrorMessage ?? tn('edit_instance_error'),
  });

  const copyStatus = useToastForFetchStatusChange(instanceCopyStatus, {
    success: tn('copy_instance_success'),
    error: tn('copy_instance_error'),
  });

  const instancesHaveChanged = [createStatus, updateStatus, copyStatus].includes(AppConstants.FETCH_STATUS.SUCCESS);

  useEffect(() => {
    if (instancesHaveChanged) {
      // Re-fetch instances after successful create/edit/copy instance
      dispatch(getInstances());
    }
  }, [dispatch, instancesHaveChanged]);

  useEffect(() => {
    dispatch(getInstances());
  }, [dispatch]);

  const actionsMenu = (
    <Menu
      onClick={(action) => {
        switch (action.key) {
          case InstanceListAction.COPY:
            dispatch(showInstanceCopyModal({ visible: true, modalType: InstanceCopyModalType.GLOBAL }));
            break;
          case InstanceListAction.REFRESH:
            dispatch(getInstances());
            break;
        }
      }}>
      <Can key={InstanceListAction.COPY} capability={[CapConstants.SUPER_ADMIN]}>
        <Menu.Item>{tn('copy_instance')}</Menu.Item>
      </Can>
      <Menu.Item key={InstanceListAction.REFRESH}>{tc('refresh')}</Menu.Item>
    </Menu>
  );

  const openModal = useCallback(() => {
    return dispatch(showInstanceModal(true));
  }, [dispatch]);

  const isLoading = instancesStatus === AppConstants.FETCH_STATUS.LOADING;

  // Users can only create two instances per subscription unless it's a partner
  // subscription or user is a super admin (see PROD-897).
  const instanceAddDisabled =
    isLoading || (!isSuperAdmin && orgType !== 'partner' && instances.length >= INSTITUTION_LIMIT);

  const createInstanceButton = (
    <Tooltip title={instanceAddDisabled && tn('max_instances_reached')}>
      <Can permission={AllPermissions.ADD_INSTANCE}>
        <Button className="apply-action" icon="plus" onClick={openModal} type="primary" disabled={instanceAddDisabled}>
          {tn('create_instance')}
        </Button>
      </Can>
    </Tooltip>
  );

  return (
    <>
      <div className="actions-container">
        {createInstanceButton}
        <Dropdown overlay={actionsMenu} trigger={['click']}>
          <Button>
            {tc('actions')} <Icon type="down" />
          </Button>
        </Dropdown>
      </div>

      <AgTable
        columnDefs={colDefs}
        defaultColDef={defaultColDef}
        domLayout="autoHeight"
        frameworkComponents={frameworkComponents}
        loading={isLoading}
        rowData={instances}
        suppressCellSelection
        enableCellTextSelection
      />
      <Can capability={[CapConstants.SUPER_ADMIN]}>
        <InstanceCopyModal />
      </Can>
      <ManageTrialInstanceModal />
    </>
  );
};

export default InstanceList;
