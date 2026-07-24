import { message } from 'antd';

import Can from 'components/Can';
import KebabMenu, { MenuItem } from 'components/KebabMenu';
import { Stack } from 'components/layout';
import { useUserInputConfirmationModal } from 'hooks/modal';
import { useDeleteRoleMutation, useGetAllRolesQuery } from 'store/access-control/api';
import { User } from 'store/user/types';
import { tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

import useToggleActivateRoleModal from './hooks';
import { RBACModalVariants } from './RoleBasedAccessControl';

import './ActionMenu.scss';

enum InstanceAction {
  ACTIVATE = 'activateRole',
  EDIT = 'editRole',
  DELETE = 'deleteRole',
  VIEW_DETAILS = 'viewDetails',
}

interface ActionMenuProps {
  roleId: string;
  setSelectedRoleId: (roleId: string) => void;
  setModalVariant: (modalVariant: RBACModalVariants) => void;
}

export default function ActionMenu({ roleId, setSelectedRoleId, setModalVariant }: ActionMenuProps) {
  const tn = tNamespaced('Settings.AccessControl.RoleBasedTableActions');
  const tm = tNamespaced('Settings.AccessControl.RBACDeleteRoleModal');
  const { data } = useGetAllRolesQuery();

  const rowData = data?.find((item) => item.id === roleId);

  const active = rowData?.active!;
  const users = rowData?.users!;
  const roleName = rowData?.name!;
  const system = rowData?.system!;
  const permissions = rowData?.privileges!;

  const toggleActivateModal = useToggleActivateRoleModal({ isActive: active, users, roleId, roleName, permissions });
  const [deleteRole] = useDeleteRoleMutation();

  const showDeleteModal = useUserInputConfirmationModal();

  const onMenuItemAction = ({ key }: { key: string }) => {
    switch (key) {
      case InstanceAction.VIEW_DETAILS:
        setSelectedRoleId(roleId);
        setModalVariant(RBACModalVariants.DETAILS);
        break;
      case InstanceAction.ACTIVATE:
        toggleActivateModal();
        break;
      case InstanceAction.EDIT:
        setSelectedRoleId(roleId);
        setModalVariant(RBACModalVariants.EDIT);
        break;
      case InstanceAction.DELETE:
        showDeleteModal({
          title: tm('title', { roleName: rowData?.name }),
          content: (
            <Stack>
              {users?.length === 0
                ? tm('message_no_users')
                : tm('message', {
                    userAmount: users?.length > 1 ? users.length : '',
                    userWord: users.length > 1 ? tm('users') : tm('user'),
                  })}
              {users && users.length && (
                <Stack className="rbac__confirm-modal">
                  {users.map((user: Omit<User, 'timezone' | 'ghosted'>) => (
                    <div key={user.id}>{`${user.firstName} ${user.lastName} (${user.email})`}</div>
                  ))}
                </Stack>
              )}
            </Stack>
          ),
          onOk: () =>
            deleteRole({ roleId }).then((res: any) => {
              if (res?.error) {
                message.error(tm('delete_role_failed', { roleName }));
              } else {
                message.success(tm('delete_role_success', { roleName }));
              }
            }),
          okText: 'Delete',
          okType: 'danger',
          type: 'error',
        });
        break;
    }
  };

  return (
    <div className="kebab-menu-container" onClick={(e) => e.stopPropagation()}>
      <KebabMenu
        className="rbac__actions"
        testId="rbac-actions"
        menuItems={
          system
            ? [<MenuItem key={InstanceAction.VIEW_DETAILS}>{tn('view')}</MenuItem>]
            : [
                <MenuItem key={InstanceAction.ACTIVATE}>{active ? tn('deactivate') : tn('activate')}</MenuItem>,
                <Can key={InstanceAction.EDIT} permission={AllPermissions.EDIT_ROLE}>
                  <MenuItem>{tn('edit')}</MenuItem>
                </Can>,
                <Can key={InstanceAction.DELETE} permission={AllPermissions.DELETE_ROLE}>
                  <MenuItem>{tn('delete')}</MenuItem>
                </Can>,
                <MenuItem key={InstanceAction.VIEW_DETAILS}>{tn('view')}</MenuItem>,
              ]
        }
        onClick={onMenuItemAction}
      />
    </div>
  );
}
