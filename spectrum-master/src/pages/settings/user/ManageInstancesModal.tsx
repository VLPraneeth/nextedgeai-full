//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Button, Icon, Modal, Spin, Tooltip } from 'antd';
import Checkbox, { CheckboxChangeEvent } from 'antd/lib/checkbox';
import { isUndefined } from 'lodash';
import { useState, Dispatch, SetStateAction, useEffect, useMemo } from 'react';

import Can from 'components/Can';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import { HStack, Stack } from 'components/layout';
import { Text } from 'components/typography';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { EMPTY_ARRAY } from 'store/constants';
import { OrgInstancesMap } from 'store/instances/slice';
import { useGetUserRolesByIdQuery } from 'store/user/api';
import { useUserRolesForCurrentInstance } from 'store/user/hooks';
import { selectIsUpdatingUser, selectUser } from 'store/user/selectors';
import { updateUserRoles } from 'store/user/thunks';
import { AllRolesAllInstance, User } from 'store/user/types';
import CapConstants from 'utils/CapConstants';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { t, tc, tNamespaced } from 'utils/i18nUtil';

import { getRolesForUser, validateFormData } from './ManageInstancesModal.utils';
import UserRoleSelectorInput from './UserRoleSelectorInput';

import './ManageInstancesModal.less';

const tn = tNamespaced('ManageUserRolesModal');

interface ManageInstancesModalContentProps {
  errorMessage: string;
  selectedUser?: User;
  ghostUser: boolean;
  setGhostUser: Dispatch<SetStateAction<boolean>>;
  orgAdmin: boolean;
  setOrgAdmin: Dispatch<SetStateAction<boolean>>;
  allInstanceRoles: AllRolesAllInstance;
  orgInstances: OrgInstancesMap;
  userId: string;
  setParentUserRoles: Dispatch<SetStateAction<Record<string, string[]>>>;
}

const ManageInstancesModalContent = ({
  errorMessage,
  selectedUser,
  ghostUser,
  setGhostUser,
  orgAdmin,
  setOrgAdmin,
  allInstanceRoles,
  orgInstances,
  userId,
  setParentUserRoles,
}: ManageInstancesModalContentProps) => {
  const { userCan } = useUserRolesForCurrentInstance();

  const { userRoles: initialUserRoles, isOrgAdmin } = useMemo(() => {
    return getRolesForUser(allInstanceRoles, userId);
  }, [allInstanceRoles, userId]);

  const [userRoles, setUserRoles] = useState(initialUserRoles);

  useEffect(() => {
    setOrgAdmin(!!isOrgAdmin);
  }, [isOrgAdmin, selectedUser?.isSuperAdmin, setOrgAdmin]);

  // Backend needs this role attached to the user in order to do calculate the number
  // of associated user instances. We add / remove this when a user hits the org admin
  // checkbox.
  useEffect(() => {
    setUserRoles((prevRoles) => {
      const userRoles: Record<string, string[]> = {};

      if (orgAdmin) {
        // Add the Org Admin role to each instance
        Object.keys(orgInstances).forEach((instanceId) => {
          userRoles[instanceId] = [...(prevRoles[instanceId] ?? EMPTY_ARRAY), CapConstants.ADMIN];
        });
      } else {
        // Remove the Org Admin role from each instance
        Object.keys(prevRoles).forEach((instanceId) => {
          const updatedRoles = prevRoles[instanceId]?.filter((role) => role !== CapConstants.ADMIN);

          if (updatedRoles.length > 0) {
            userRoles[instanceId] = updatedRoles;
          }
        });
      }

      return userRoles;
    });
  }, [orgAdmin, orgInstances]);

  useEffect(() => {
    setParentUserRoles(userRoles);
  }, [setParentUserRoles, userRoles]);

  const handleInstanceRoleChange = (instanceId: string, roles?: string[]) =>
    setUserRoles((prev) => {
      // undefined is used to remove the instance from our list
      if (!roles) {
        let newUserRoles = { ...prev };
        delete newUserRoles[instanceId];

        return newUserRoles;
      } else {
        // if we're given `[]`, and instanceId was not in previousState
        // then the checkbox was just checked.
        // We'll check to see if this instance was initially configured
        // and if it was, re-set the initial roles. This is a nicer UX
        // if someone accidentally unchecked an instance and then re-check it
        if (!(instanceId in prev) && instanceId in initialUserRoles) {
          roles = initialUserRoles[instanceId];
        }

        return { ...prev, [instanceId]: roles };
      }
    });

  const name = (selectedUser?.firstName || '') + ' ' + (selectedUser?.lastName || '');

  return (
    <Stack>
      <InlineMessage type={InlineMessageTypes.ERROR} title={errorMessage}>
        {errorMessage}
      </InlineMessage>
      <Stack>
        <Text className="permissions-section-header">{t('InviteUserModal.user_name')}</Text>
        <Text>{name.trim() || '-'}</Text>
      </Stack>
      <Can capability={[CapConstants.SUPER_ADMIN, CapConstants.ADMIN]}>
        <Stack spacing={userCan(CapConstants.SUPER_ADMIN) ? 'md' : 'xs'}>
          <Text className="permissions-section-header">{t('InviteUserModal.user_type_header')}</Text>
          <Can capability={[CapConstants.SUPER_ADMIN]}>
            <HStack spacing="sm">
              <Checkbox
                id="ghostUser"
                name="ghostUser"
                checked={ghostUser}
                onChange={(evt: CheckboxChangeEvent) => setGhostUser(evt.target.checked)}
              />
              <label htmlFor="ghostUser">{t('InviteUserModal.ghost_user')}</label>
              <Tooltip title={t('InviteUserModal.ghost_tooltip')}>
                <Icon type="question-circle" theme="filled" />
              </Tooltip>
            </HStack>
          </Can>
          <HStack spacing="sm">
            <Checkbox
              id="orgAdmin"
              name="orgAdmin"
              checked={orgAdmin}
              onChange={(evt: CheckboxChangeEvent) => setOrgAdmin(evt.target.checked)}
            />
            <label htmlFor="orgAdmin">{t('InviteUserModal.org_admin')}</label>
            <Tooltip title={t('InviteUserModal.org_admin_tooltip')}>
              <Icon type="question-circle" theme="filled" />
            </Tooltip>
          </HStack>
        </Stack>
      </Can>
      <Text className="permissions-section-header">{t('InviteUserModal.instance_permissions')}</Text>
      <ul className="instance-permissions-well">
        {Object.keys(allInstanceRoles)
          ?.filter((instanceId) => Boolean(orgInstances[instanceId]))
          .map((instanceId) => {
            const roles = allInstanceRoles?.[instanceId]
              // The "Org Admin" role is hidden from the user, controlled via checkbox
              .filter((role) => role.active && role.name !== CapConstants.ADMIN)
              .map((role) => ({ id: role.id, name: role.name }));

            const instance = orgInstances[instanceId];
            return (
              <UserRoleSelectorInput
                key={instanceId}
                checked={orgAdmin ? orgAdmin : instanceId in userRoles}
                isOrgAdmin={orgAdmin}
                roles={userRoles[instanceId]?.filter((role) => role !== CapConstants.ADMIN)}
                onChange={handleInstanceRoleChange}
                availableRoles={roles}
                instance={instance}
              />
            );
          })}
      </ul>
    </Stack>
  );
};
export interface ManageInstancesModalProps {
  onRequestClose: () => void;
  orgInstances: OrgInstancesMap;
  userId: string;
  visible: boolean;
}

const ManageInstancesModal = ({ onRequestClose, orgInstances, userId, visible }: ManageInstancesModalProps) => {
  const dispatch = useEnhancedDispatch();
  const { data: allInstanceRoles, isFetching, isError, error, refetch } = useGetUserRolesByIdQuery({ userId });
  const selectedUser = useEnhancedSelector(selectUser(userId));
  const isUpdatingUserStatus = useEnhancedSelector((state) => selectIsUpdatingUser(state, { userId }));

  const [userRoles, setUserRoles] = useState<Record<string, string[]>>({});
  const [errorMessage, setErrorMessage] = useState('');
  const [ghostUser, setGhostUser] = useState(selectedUser ? selectedUser.isGhostUser : false);
  const [orgAdmin, setOrgAdmin] = useState(false);
  const isValid = validateFormData(userRoles, orgAdmin);
  const { roles } = useUserRolesForCurrentInstance();

  useEffect(() => {
    if (visible) {
      refetch();
    }
  }, [refetch, visible]);

  const handleSave = async () => {
    setErrorMessage('');
    const result = await dispatch(updateUserRoles(userId, userRoles, ghostUser, orgAdmin));

    if (result?.success) {
      onRequestClose();
    } else {
      setErrorMessage(result?.message.errorMessage);
    }
  };

  const modalWidth = 600;

  return (
    <Modal
      className="manage-instances-modal"
      title={roles.superAdmin ? tn('title_edit_user') : tn('title')}
      visible={visible}
      onOk={handleSave}
      onCancel={onRequestClose}
      maskClosable={false}
      width={modalWidth}
      footer={
        <>
          <Button key="cancel" onClick={onRequestClose}>
            {tc('cancel')}
          </Button>
          <Button key="ok" type="primary" onClick={handleSave} disabled={!isValid || isUpdatingUserStatus}>
            {isUpdatingUserStatus ? tc('saving') : tc('save')}
          </Button>
        </>
      }>
      {isError ? (
        <InlineMessage type={InlineMessageTypes.ERROR} title={getRtkQueryErrorMessage(error)}>
          {getRtkQueryErrorMessage(error)}
        </InlineMessage>
      ) : isUndefined(allInstanceRoles) || isFetching ? (
        <Spin>{tc('loading')}</Spin>
      ) : (
        <ManageInstancesModalContent
          errorMessage={errorMessage}
          selectedUser={selectedUser}
          ghostUser={ghostUser}
          setGhostUser={setGhostUser}
          orgAdmin={orgAdmin}
          setOrgAdmin={setOrgAdmin}
          allInstanceRoles={allInstanceRoles}
          orgInstances={orgInstances}
          userId={userId}
          setParentUserRoles={setUserRoles}
        />
      )}
    </Modal>
  );
};

export default ManageInstancesModal;
