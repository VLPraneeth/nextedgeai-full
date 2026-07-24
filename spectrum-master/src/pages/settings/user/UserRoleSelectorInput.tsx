import { Checkbox, Select, Tooltip } from 'antd';
import cx from 'classnames';

import Can from 'components/Can';
import { EntityItem } from 'components/inputs/FieldOptions';
import { Instance } from 'store/instances/slice';
import { Role } from 'store/user/types';
import { tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

import './UserRoleSelectorInput.less';

const Option = Select.Option;

const tn = tNamespaced('UserRoleSelector');

interface UserRoleSelectorInputProps {
  checked: boolean;
  isOrgAdmin?: boolean;
  instance: Instance;
  availableRoles: Role[];
  roles: string[];
  onChange: (instanceId: string, roles?: string[]) => void;
}

const UserRoleSelectorInput = ({
  checked,
  isOrgAdmin,
  instance,
  availableRoles,
  roles = [],
  onChange,
}: UserRoleSelectorInputProps) => {
  const handleChange = (roles?: string[]) => {
    onChange(instance.syncariId, roles);
  };

  const toggleCheckbox = () => {
    if (checked) {
      // calling with no roles unchecks the box
      handleChange();
    } else {
      handleChange([]);
    }
  };

  return (
    <li
      key={instance.syncariId}
      className={cx('instance-role-selector', checked && 'is-checked', isOrgAdmin && 'is-disabled')}>
      <span className="instance-checkbox">
        <Tooltip title={`${instance.name} (${instance.syncariId})`} mouseEnterDelay={1}>
          <Checkbox
            checked={checked}
            className="org-admin-disabled"
            disabled={isOrgAdmin}
            onChange={toggleCheckbox}
            data-testid={instance.name}>
            <EntityItem displayName={instance.name} apiName={instance.syncariId} />
          </Checkbox>
        </Tooltip>
      </span>
      {checked && !isOrgAdmin && (
        <span className="instance-roles-select">
          <Can permission={[AllPermissions.ADD_ROLE_TO_USR, AllPermissions.REMOVE_ROLE_FROM_USR]}>
            <Select
              mode="multiple"
              style={{ width: '100%' }}
              placeholder={tn('placeholder')}
              value={roles}
              onChange={handleChange}>
              {availableRoles?.map((role) => (
                <Option key={role.id} value={role.name}>
                  {role.name}
                </Option>
              ))}
            </Select>
          </Can>
        </span>
      )}
    </li>
  );
};

export default UserRoleSelectorInput;
