import { List, message } from 'antd';
import { LabeledValue } from 'antd/lib/select';
import { Dispatch, SetStateAction } from 'react';

import Can from 'components/Can';
import InputWithLabel from 'components/inputs/InputWithLabel';
import Select from 'components/inputs/Select';
import { Spacer, Stack } from 'components/layout';
import { useGetAllPermissionsQuery, useGetAllUsersQuery } from 'store/access-control/api';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

import './AddRoleWizard.scss';
import { RoleFormTypes } from './AddRoleWizard';

interface StepTwoFormProps {
  formType?: RoleFormTypes;
  rolePermissions: LabeledValue[];
  roleUsers: LabeledValue[];
  setRolePermissions: Dispatch<SetStateAction<LabeledValue[]>>;
  setRoleUsers: Dispatch<SetStateAction<LabeledValue[]>>;
}

const StepTwoForm = ({ formType, rolePermissions, roleUsers, setRolePermissions, setRoleUsers }: StepTwoFormProps) => {
  const tn = tNamespaced('Settings.AccessControl.Forms');
  const {
    data: permissionsData,
    isFetching: permissionsIsFetching,
    error: permissionsError,
  } = useGetAllPermissionsQuery();

  const { data: userQueryData, isFetching: userQueryIsFetching, error: userQueryError } = useGetAllUsersQuery();

  const allPermissions = permissionsData?.map((permission) => ({
    label: permission.displayName,
    value: permission.privilegeId,
  }));

  const allUsers = userQueryData?.map((user) => {
    const name = (user.firstName || '') + ' ' + (user.lastName || '');
    return {
      label: name.trim() || user.email,
      value: user.id,
    };
  });

  if ((permissionsError && 'data' in permissionsError) || (userQueryError && 'data' in userQueryError)) {
    permissionsError && message.error(getRtkQueryErrorMessage(permissionsError));
    userQueryError && message.error(getRtkQueryErrorMessage(userQueryError));
  }

  return (
    <Stack className="add-role__form-container">
      <InputWithLabel
        label={tn('amount_permissions', { amount: rolePermissions.length })}
        input={
          <Can permission={formType === RoleFormTypes.edit ? AllPermissions.ADD_PRIV_TO_ROLE : undefined}>
            <Select
              className="add-role__search"
              labelInValue
              value={rolePermissions}
              loading={permissionsIsFetching}
              showSearch
              optionData={allPermissions}
              placeholder={tn('select_permissions')}
              mode="multiple"
              onDeselect={(item: any) =>
                setRolePermissions(rolePermissions.filter((listItem) => item.key !== listItem.key))
              }
              onSelect={(item: any) => setRolePermissions([...rolePermissions, item])}
            />
          </Can>
        }
      />
      <List
        className="add-role__list"
        dataSource={rolePermissions}
        renderItem={(item: LabeledValue) => (
          <List.Item className="add-role__list-item">
            {item?.label}
            <Can permission={formType === RoleFormTypes.edit ? AllPermissions.REMOVE_PRIV_FROM_ROLE : undefined}>
              <a
                onClick={() => {
                  setRolePermissions(
                    rolePermissions.filter((listItem) => {
                      return item.key !== listItem.key;
                    })
                  );
                }}>
                {tn('remove')}
              </a>
            </Can>
          </List.Item>
        )}
      />
      <Spacer />
      <InputWithLabel
        label={tn('amount_users', { amount: roleUsers.length })}
        input={
          <Can permission={formType === RoleFormTypes.edit ? AllPermissions.ADD_ROLE_TO_USR : undefined}>
            <Select
              className="add-role__search"
              value={roleUsers}
              labelInValue
              loading={userQueryIsFetching}
              maxTagTextLength={0}
              maxTagCount={0}
              showSearch
              optionData={allUsers}
              placeholder={tn('select_users')}
              mode="multiple"
              onSelect={(item: LabeledValue) => {
                setRoleUsers([...roleUsers, item]);
              }}
            />
          </Can>
        }
      />
      <List
        className="add-role__list"
        dataSource={roleUsers}
        renderItem={(item: LabeledValue) => (
          <List.Item className="add-role__list-item">
            {item.label}
            <Can permission={formType === RoleFormTypes.edit ? AllPermissions.REMOVE_ROLE_FROM_USR : undefined}>
              <a
                onClick={() =>
                  setRoleUsers(
                    roleUsers.filter((listItem) => {
                      return item.key !== listItem.key;
                    })
                  )
                }>
                {tn('remove')}
              </a>
            </Can>
          </List.Item>
        )}
      />
    </Stack>
  );
};

export default StepTwoForm;
