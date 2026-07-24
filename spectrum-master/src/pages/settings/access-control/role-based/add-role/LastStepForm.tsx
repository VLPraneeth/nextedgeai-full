import { LabeledValue } from 'antd/lib/select';

import InputWithLabel from 'components/inputs/InputWithLabel';
import Tag from 'components/inputs/Tag';
import { Stack } from 'components/layout';
import ListWithSearch from 'components/list/ListWithSearch';
import Text from 'components/typography/Text';
import { tNamespaced } from 'utils/i18nUtil';

import { FirstStepFormProps } from './FirstStepForm';

type LastStepFormProps = Omit<FirstStepFormProps, 'setRoleName' | 'setRoleDescription' | 'setTags' | 'setStatus'> & {
  rolePermissions: LabeledValue[];
  roleUsers: LabeledValue[];
};

const LastStepForm = ({ roleName, roleDescription, tags, status, rolePermissions, roleUsers }: LastStepFormProps) => {
  const tn = tNamespaced('Settings.AccessControl.RoleDetails');

  const permissions = rolePermissions.map((item) => item.label?.toString());
  const users = roleUsers.map((user) => user.label?.toString());

  return (
    <div className="flex">
      <Stack className="add-role__form-container">
        <Text color="gray-1000" weight="bold">
          {tn('basic_info')}
        </Text>
        <InputWithLabel disabled label={tn('role_name')} value={roleName} />
        <InputWithLabel disabled label={tn('description')} value={roleDescription} />
        <InputWithLabel disabled label={tn('status')} value={status} />
        {tags?.length! > 0 && (
          <InputWithLabel disabled label={tn('tags')} input={<Tag id="role-details" disabled value={tags} />} />
        )}
      </Stack>
      <Stack className="add-role__form-container">
        <ListWithSearch
          label={`${permissions?.length} ${tn('permissions')}`}
          placeholder={tn('permissions_placeholder')}
          listItems={permissions}
          className="role-details__list"
        />
        <ListWithSearch
          label={`${users?.length} ${tn('users')}`}
          placeholder={tn('users_placeholder')}
          listItems={users}
          className="role-details__list"
        />
      </Stack>
    </div>
  );
};

export default LastStepForm;
