//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { RouteComponentProps } from '@reach/router';
import { Button, Dropdown, Icon, Menu, message } from 'antd';
import { keyBy } from 'lodash';
import { useEffect, useMemo, useState } from 'react';

import Can from 'components/Can';
import StatusBadge, { StatusBadgeType } from 'components/StatusBadge';
import Table from 'components/Table';
import SearchBox from 'components/SearchBox';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { useWindowTitle } from 'hooks/windowTitle';
import { selectAllInstances } from 'selectors/instanceSelectors';
import { getInstances, OrgInstancesMap } from 'store/instances/slice';
import { PromiseThunkAction } from 'store/types';
import { showInviteUserModal } from 'store/user/actions';
import { useUserRolesForCurrentInstance } from 'store/user/hooks';
import { useUserData } from 'store/user/selector.hooks';
import { selectOrgId } from 'store/user/selectors';
import { activateUser, deactivateUser, deleteUser, getUsers, removeUser, resendInvite } from 'store/user/thunks';
import { User } from 'store/user/types';
import AppConstants from 'utils/AppConstants';
import { t, tc, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

import ManageInstancesModal from './ManageInstancesModal';

import './UserList.less';

const ACTION_KEYS = {
  ACTIVATE: 'activate',
  DEACTIVATE: 'deactivate',
  DELETE: 'delete',
  REFRESH: 'refresh',
  REMOVE: 'remove',
} as const;

const { USER_STATUS } = AppConstants;
const tn = tNamespaced('Settings.Users');

const isActive = (user: User) => user.status.toLowerCase() === USER_STATUS.ACTIVE;
const isInactive = (user: User) => user.status.toLowerCase() === USER_STATUS.INACTIVE;

export interface InstanceCellProps {
  value: number;
  orgInstances: OrgInstancesMap;
  onRequestManage: () => void;
}

const InstancesCell = ({ value: associatedInstancesCount, orgInstances, onRequestManage }: InstanceCellProps) => {
  const { roles } = useUserRolesForCurrentInstance();

  return (
    <div className="user-instances">
      <span className="instance-count">{tn('instance_count', { count: associatedInstancesCount })}</span>
      <Can permission={AllPermissions.LIST_ROLES}>
        <Button type="primary" size="small" onClick={onRequestManage}>
          {roles.superAdmin ? tc('edit') : tn('manage_button_text')}
        </Button>
      </Can>
    </div>
  );
};

interface StatusBadgeCellProps {
  text: any;
  record: any;
}

const StatusBadgeCell = ({ text: _text, record }: StatusBadgeCellProps) => {
  const text = _text.toLowerCase();
  const { resendingUserInvites } = useUserData();
  const isSendingInvite = resendingUserInvites.includes(record.id);
  const dispatch = useEnhancedDispatch();
  const resendUserInvite = () => dispatch(resendInvite(record.id));

  const status =
    text === USER_STATUS.ACTIVE
      ? StatusBadgeType.SUCCESS
      : text === USER_STATUS.INACTIVE
      ? StatusBadgeType.INFO
      : StatusBadgeType.DEFAULT;

  return (
    <div className="status-badge-cell">
      <StatusBadge type={status}>{text}</StatusBadge>
      {record.isApiUser && (
        <StatusBadge className="status-badge-cell__additional-badge" type={StatusBadgeType.SPECIAL}>
          {tn('api_user')}
        </StatusBadge>
      )}
      {text === USER_STATUS.PENDING && (
        <Can permission={AllPermissions.REINVITE_USER}>
          <Button
            className="status-badge-cell__reinvite-button"
            disabled={isSendingInvite}
            loading={isSendingInvite}
            onClick={resendUserInvite}
            type="link">
            {tn('reinvite_button_text')}
          </Button>
        </Can>
      )}
    </div>
  );
};

export interface UserListProps extends RouteComponentProps {}

const UserList = (props: UserListProps) => {
  useWindowTitle(t('Settings.Users.page_title'));
  const { userUpdatesPending, users, fetchingUsers } = useUserData();
  const orgInstances = useEnhancedSelector(selectAllInstances);
  const userOrgId = useEnhancedSelector(selectOrgId);
  const dispatch = useEnhancedDispatch();

  const [userIdToManage, setUserIdToManage] = useState('');
  const [selectedRows, setSelectedRows] = useState<string[]>([]);
  const [filterString, setFilterString] = useState('');

  const closeManageModal = () => setUserIdToManage('');

  // users for selected rows
  const selectedUsers = useMemo(() => {
    return users?.filter((user) => selectedRows.includes(user.id));
  }, [users, selectedRows]);
  const anyUpdatesPending = useMemo(() => {
    return Boolean(userUpdatesPending?.length > 0);
  }, [userUpdatesPending]);
  const anyUsersSelected = useMemo(() => selectedUsers.length > 0, [selectedUsers]);
  const noActiveUsersSelected = useMemo(() => !selectedUsers?.some(isActive), [selectedUsers]);
  const noInactiveUsersSelected = useMemo(() => !selectedUsers?.some(isInactive), [selectedUsers]);

  const handleAction = async (actionType: string) => {
    let users: User[] = [];
    let action: (userId: string) => PromiseThunkAction;

    switch (actionType) {
      case ACTION_KEYS.ACTIVATE:
        action = activateUser;
        users = selectedUsers.filter(isInactive);
        if (users.length !== selectedUsers.length) {
          message.warning(tn('only_activating_inactive_users'));
        }
        break;
      case ACTION_KEYS.DEACTIVATE:
        action = deactivateUser;
        users = selectedUsers.filter(isActive);
        if (users.length !== selectedUsers.length) {
          message.warning(tn('only_deactivating_active_users'));
        }
        break;
      case ACTION_KEYS.DELETE:
        action = deleteUser;
        users = selectedUsers;
        break;
      case ACTION_KEYS.REMOVE:
        action = removeUser;
        users = selectedUsers;
        break;
      default:
        break;
    }

    const results = await Promise.all(
      users.map(({ id: userId, orgId }) => {
        if (orgId !== userOrgId) {
          return Promise.resolve({
            success: false,
            userId,
          });
        } else {
          return dispatch(action(userId));
        }
      })
    );

    if (results.every((result) => result?.success)) {
      switch (actionType) {
        case ACTION_KEYS.ACTIVATE:
          message.success(tn('user_activated', { count: users.length }));
          break;
        case ACTION_KEYS.DEACTIVATE:
          message.success(tn('user_deactivated', { count: users.length }));
          break;
        case ACTION_KEYS.DELETE:
          message.success(tn('user_deleted', { count: users.length }));
          break;
        case ACTION_KEYS.REMOVE:
          message.success(tn('user_removed', { count: users.length }));
          break;
        default:
          break;
      }
    } else {
      results
        .filter((result) => !result?.success)
        .forEach(({ userId }) => {
          const selectedUser = selectedUsers.find((user) => user.id === userId);
          if (selectedUser) {
            const { firstName, lastName } = selectedUser;
            message.error(tn('action_error', { actionType, firstName, lastName }));
          }
        });
    }

    // Refresh user list after all actions are completed
    dispatch(getUsers());
  };

  useEffect(() => {
    dispatch(getUsers());
    dispatch(getInstances());
  }, [dispatch]);

  const tableData = useMemo(
    () =>
      users?.filter((user) => {
        return (
          user?.firstName?.toLowerCase().includes(filterString.toLocaleLowerCase()) ||
          user?.lastName?.toLowerCase().includes(filterString.toLocaleLowerCase()) ||
          user?.email?.toLowerCase().includes(filterString.toLocaleLowerCase()) ||
          user?.status?.toLowerCase().includes(filterString.toLocaleLowerCase())
        );
      }),
    [users, filterString]
  );

  // rowSelection object indicates the need for row selection
  const rowSelection = {
    onChange: (rowKeys: any) => setSelectedRows(rowKeys),
    getCheckboxProps: (record: any) => ({ name: record.name }),
  };

  const userActionHandler = (action: { key: string }) => {
    switch (action.key) {
      case ACTION_KEYS.REFRESH:
        dispatch(getUsers());
        break;
      default:
        handleAction(action.key);
        break;
    }
  };

  const actionsMenu = (
    <Menu onClick={userActionHandler}>
      <Menu.Item key={ACTION_KEYS.REFRESH} disabled={anyUpdatesPending}>
        {tn('refresh')}
      </Menu.Item>
      {anyUsersSelected && (
        <Can key={ACTION_KEYS.REMOVE} permission={AllPermissions.REMOVE_USER}>
          <Menu.Item disabled={anyUpdatesPending}>{tn('remove_user', { count: selectedUsers.length })}</Menu.Item>
        </Can>
      )}
      {anyUsersSelected && (
        <Can key={ACTION_KEYS.ACTIVATE} permission={AllPermissions.ACTIVATE_USER}>
          <Menu.Item disabled={anyUpdatesPending || noInactiveUsersSelected}>
            {tn('activate_user', { count: selectedUsers.length })}
          </Menu.Item>
        </Can>
      )}
      {anyUsersSelected && (
        <Can key={ACTION_KEYS.DEACTIVATE} permission={AllPermissions.DEACTIVATE_USER}>
          <Menu.Item disabled={anyUpdatesPending || noActiveUsersSelected}>
            {tn('deactivate_user', { count: selectedUsers.length })}
          </Menu.Item>
        </Can>
      )}
      {anyUsersSelected && (
        <Can key={ACTION_KEYS.DELETE} permission={AllPermissions.DELETE_USR}>
          <Menu.Item disabled={anyUpdatesPending}>{tn('delete_user', { count: selectedUsers.length })}</Menu.Item>
        </Can>
      )}
    </Menu>
  );

  // map of orgInstances, { syncariId: Instance }
  const orgInstanceMap = useMemo(() => {
    return keyBy(orgInstances, 'syncariId');
  }, [orgInstances]);

  const COLUMNS = [
    {
      title: tn('first_name'),
      dataIndex: 'firstName',
    },
    {
      title: tn('last_name'),
      dataIndex: 'lastName',
    },
    {
      title: tn('email'),
      dataIndex: 'email',
    },
    {
      title: tn('status'),
      dataIndex: 'status',
      render: (text: string, record: any) => <StatusBadgeCell text={text} record={record} />,
    },
    {
      title: tn('instances'),
      dataIndex: 'associatedInstancesCount',
      render: (value: number, user: User) => {
        return (
          <InstancesCell
            value={value}
            orgInstances={orgInstanceMap}
            onRequestManage={() => setUserIdToManage(user.id)}
          />
        );
      },
    },
  ];

  return (
    <>
      <div className="actions-container">
        <SearchBox
          onChange={(event) => setFilterString(event.target.value)}
          placeholder={tc('search')}
          className="user-list__search"
          value={filterString}
        />
        <Can permission={AllPermissions.INVITE_USER}>
          <Button icon="plus" type="primary" onClick={() => dispatch(showInviteUserModal(true))}>
            {tn('invite_user')}
          </Button>
        </Can>
        <Dropdown overlay={actionsMenu} trigger={['click']}>
          <Button>
            {tn('actions')} <Icon type="down" />
          </Button>
        </Dropdown>
      </div>
      <Table columns={COLUMNS} dataSource={tableData} loading={fetchingUsers} rowKey="id" rowSelection={rowSelection} />
      {Boolean(userIdToManage) && (
        <ManageInstancesModal
          onRequestClose={closeManageModal}
          orgInstances={orgInstanceMap}
          userId={userIdToManage}
          visible
        />
      )}
    </>
  );
};

export default UserList;
