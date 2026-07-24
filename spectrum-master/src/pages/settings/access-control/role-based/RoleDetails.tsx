import { message } from 'antd';
import { useMemo } from 'react';

import DrawerPanel from 'components/DrawerPanel';
import { useI18nNamespace } from 'components/I18nProvider';
import InputWithLabel from 'components/inputs/InputWithLabel';
import Tag from 'components/inputs/Tag';
import { Stack } from 'components/layout';
import ListWithSearch from 'components/list/ListWithSearch';
import Text from 'components/typography/Text';
import { useGetRoleByIdQuery } from 'store/access-control/api';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';

import './RoleDetails.scss';
import InformationSnippet from './InformationSnippet';

export default function RoleDetails({
  selectedRoleId,
  close,
  visible,
}: {
  selectedRoleId: string;
  close: () => void;
  visible: boolean;
}) {
  const { data, error, isFetching } = useGetRoleByIdQuery({ roleId: selectedRoleId }, { skip: !visible });
  const tn = useI18nNamespace('Settings.AccessControl.RoleDetails');

  const permissions = useMemo(() => data?.privileges.map((item) => item.displayName) || [], [data?.privileges]);
  const users = useMemo(() => data?.users?.map((user) => `${user.firstName} ${user.lastName}`) || [], [data?.users]);

  if (error && 'data' in error && !isFetching && selectedRoleId) {
    message.error(getRtkQueryErrorMessage(error));
  }

  return (
    <DrawerPanel
      absolutePositioning
      maskClosable
      onClose={() => close()}
      mask
      title={tn('role_details')}
      width="full"
      visible={visible}>
      <div className="role-details">
        <Stack className="role-details__left" spacing="lg">
          <Text color="gray-1000" style={{ marginTop: '10px' }} weight="bold">
            {tn('basic_info')}
          </Text>
          <InformationSnippet label={tn('role_name')} value={data?.name!} />
          <InformationSnippet label={tn('description')} value={data?.description!} />
          <InformationSnippet label={tn('status')} value={data?.active! ? 'Active' : 'Inactive'} />
          <InformationSnippet label={tn('system_role')} value={data?.system.toString()!} />
          {data?.tags?.length! > 0 && (
            <InputWithLabel
              disabled
              label={
                <Text color="gray-700" size="md" weight="bold">
                  {tn('tags')}
                </Text>
              }
              input={<Tag id="role-details" disabled value={data?.tags!} />}
            />
          )}
        </Stack>
        <Stack className="role-details__right">
          <ListWithSearch
            label={`${permissions?.length} ${tn('permissions')}`}
            placeholder={tn('permissions_placeholder')}
            listItems={permissions}
          />
          <ListWithSearch
            label={`${users.length} ${tn('users')}`}
            placeholder={tn('users_placeholder')}
            listItems={users}
          />
        </Stack>
      </div>
    </DrawerPanel>
  );
}
