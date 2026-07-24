import { message, Modal } from 'antd';
import { capitalize } from 'lodash';

import { Stack } from 'components/layout';
import { useEditRoleMutation } from 'store/access-control/api';
import { UserPermission } from 'store/access-control/types';
import { User } from 'store/user/types';
import { tNamespaced } from 'utils/i18nUtil';

interface UseToggleActivateRoleModalProps {
  isActive: boolean;
  roleId: string;
  roleName: string;
  users: Omit<User, 'timezone' | 'ghosted'>[];
  permissions: UserPermission[];
}

const useToggleActivateRoleModal = ({
  isActive,
  users,
  roleId,
  roleName,
  permissions,
}: UseToggleActivateRoleModalProps) => {
  const tn = tNamespaced('Settings.AccessControl.RBACActivateRoleModal');
  const activatePromptText = isActive ? tn('deactivate') : tn('activate');
  const activatedPromptText = isActive ? tn('deactivated') : tn('activated');

  const [editRoleMutation] = useEditRoleMutation();

  return () => {
    return Modal.confirm({
      title: tn('title'),
      content: (
        <Stack>
          {tn('message', { activatePromptText })}
          <Stack className="rbac__confirm-modal">
            {users.map((user: Omit<User, 'timezone' | 'ghosted'>) => (
              <div key={user.id}>{`${user.firstName} ${user.lastName} (${user.email})`}</div>
            ))}
          </Stack>
        </Stack>
      ),
      okText: capitalize(activatePromptText),
      cancelText: tn('cancel'),
      onOk: () =>
        editRoleMutation({
          roleId,
          name: roleName,
          active: !isActive,
          users: users.map((user) => user.id),
          privileges: permissions.map((permission) => permission.privilegeId),
        })
          .catch(({ error }) => message.error(error?.error?.data?.message))
          .then((res) => {
            if (res?.error) {
              message.error(tn('role_activation_message_failed', { activatedPromptText }));
            } else {
              message.success(tn('role_activation_message_success', { activatedPromptText }));
            }
          }),
    });
  };
};

export default useToggleActivateRoleModal;
