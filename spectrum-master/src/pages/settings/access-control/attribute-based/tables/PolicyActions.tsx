import { useState } from 'react';
import { Menu, Modal, message } from 'antd';

import Can from 'components/Can';
import KebabMenu from 'components/KebabMenu';
import { Text } from 'components/typography';
import { useDeletePolicyMutation } from 'store/access-control/abac/api';
import { tc } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

interface PolicyActionsProps {
  data: any;
  setIsAddDrawerVisible: (visible: boolean) => void;
}

export default function PolicyActions({ data, setIsAddDrawerVisible }: PolicyActionsProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [deletePolicy] = useDeletePolicyMutation();

  const handleDelete = async () => {
    try {
      await deletePolicy(data.id).unwrap();
      message.success('Policy deleted.');
    } catch (error: any) {
      message.error(`Error deleting policy: ${error?.data?.error} | ${error?.data?.message}`);
      message.error(`Error deleting policy: ${error?.data?.error} | ${error?.data?.message}`, 7);
    }
  };

  return (
    <div className="custom-synapse__table-actions">
      <KebabMenu
        menuItems={[
          <Can key="edit_policy" permission={AllPermissions.WRITE_CONNECTOR}>
            <Menu.Item
              key="edit_policy"
              onClick={() => {
                setMenuOpen(false);
                setIsAddDrawerVisible(true);
              }}>
              <Text>{tc('edit')}</Text>
            </Menu.Item>
          </Can>,
          <Can key="delete_policy" permission={AllPermissions.WRITE_CONNECTOR}>
            <Menu.Item
              key="delete_policy"
              onClick={() => {
                setMenuOpen(false);
                Modal.confirm({
                  title: 'Delete Policy',
                  content: 'Are you sure you want to delete this policy?',
                  okText: tc('delete'),
                  okType: 'danger',
                  okButtonProps: { type: 'danger' },
                  onOk: handleDelete,
                });
              }}>
              <Text color="red-300">{tc('delete')}</Text>
            </Menu.Item>
          </Can>,
        ]}
        visible={menuOpen}
        onVisibleChange={setMenuOpen}
        onClick={() => setMenuOpen(false)}
        size="large"
      />
    </div>
  );
}
