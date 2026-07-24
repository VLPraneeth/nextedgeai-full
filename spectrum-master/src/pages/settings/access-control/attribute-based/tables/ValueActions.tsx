import { Menu, Modal } from 'antd';
import { useState } from 'react';

import Can from 'components/Can';
import KebabMenu from 'components/KebabMenu';
import { Text } from 'components/typography';
import { tc } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

interface ValueActionsProps {
  data: any;
  onEditActionClick: () => void;
  onDeleteActionClick: () => void;
}

export default function ValueActions({ data, onEditActionClick, onDeleteActionClick }: ValueActionsProps) {
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <div className="custom-synapse__table-actions">
      <KebabMenu
        menuItems={[
          <Can key="edit_value" permission={AllPermissions.WRITE_CONNECTOR}>
            <Menu.Item
              key="edit_value"
              onClick={() => {
                setMenuOpen(false);
                onEditActionClick?.();
              }}>
              <Text>{tc('edit')}</Text>
            </Menu.Item>
          </Can>,
          <Can key="delete_value" permission={AllPermissions.WRITE_CONNECTOR}>
            <Menu.Item
              key="delete_value"
              onClick={() => {
                setMenuOpen(false);
                Modal.confirm({
                  title: 'Delete Value?',
                  content: 'Are you sure you want to delete this value?',
                  okText: tc('delete'),
                  okType: 'danger',
                  okButtonProps: { type: 'danger' },
                  onOk: onDeleteActionClick,
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
