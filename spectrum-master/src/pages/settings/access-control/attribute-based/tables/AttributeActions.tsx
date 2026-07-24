import { useState } from 'react';
import { Menu, Modal, message } from 'antd';

import Can from 'components/Can';
import KebabMenu from 'components/KebabMenu';
import { Text } from 'components/typography';

import { useDeleteAttributeMutation } from 'store/access-control/abac/api';
import { tc } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

interface AttributeActionsProps {
  data: any;
  setIsAddAttributeDrawerVisible: (visible: boolean) => void;
}

export default function AttributeActions({ data, setIsAddAttributeDrawerVisible }: AttributeActionsProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [deleteAttribute] = useDeleteAttributeMutation();

  const handleDelete = async () => {
    try {
      await deleteAttribute(data.id).unwrap();
      message.success('Attribute deleted.');
    } catch (error: any) {
      console.error('Error deleting attribute:', error);
      message.error(`Error deleting attribute: ${error?.data?.error} | ${error?.data?.message}`, 7);
    }
  };

  return (
    <div className="custom-synapse__table-actions">
      <KebabMenu
        menuItems={[
          <Can key="edit_attribute" permission={AllPermissions.WRITE_CONNECTOR}>
            <Menu.Item
              key="edit_attribute"
              onClick={() => {
                setMenuOpen(false);
                setIsAddAttributeDrawerVisible(true);
              }}>
              <Text>{tc('edit')}</Text>
            </Menu.Item>
          </Can>,
          <Can key="delete_attribute" permission={AllPermissions.WRITE_CONNECTOR}>
            <Menu.Item
              key="delete_attribute"
              onClick={() => {
                setMenuOpen(false);
                Modal.confirm({
                  title: 'Delete Attribute',
                  content: 'Are you sure you want to delete this attribute?',
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
      />
    </div>
  );
}
