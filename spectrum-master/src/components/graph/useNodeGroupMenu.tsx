//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import Menu, { ClickParam } from 'antd/lib/menu';
import Text from 'antd/lib/typography/Text';

import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { selectKebabMenuNode } from 'selectors/appSelectors';
import { showConfirmUngroupModal, showCreateGroupPanel, showDeleteMultipleNodesModal } from 'store/pipeline/actions';
import { tNamespaced } from 'utils/i18nUtil';
import { colors } from 'utils/LessConstants';

const tn = tNamespaced('NodeGroupMenu');

const ACTION = {
  CONFIGURE_GROUP: 'CONFIGURE_GROUP',
  UNGROUP: 'UNGROUP',
  DELETE: 'DELETE',
};

const useNodeGroupMenu = () => {
  const kebabMenuNode = useEnhancedSelector(selectKebabMenuNode);
  const dispatch = useEnhancedDispatch();

  if (kebabMenuNode?.nodeType !== 'group') {
    return null;
  }

  const { group } = kebabMenuNode;

  const onClick: (param: ClickParam) => void = ({ key }) => {
    switch (key) {
      case ACTION.CONFIGURE_GROUP:
        dispatch(showCreateGroupPanel({ visible: true, selectedGroup: group as any }));
        break;
      case ACTION.UNGROUP:
        dispatch(showConfirmUngroupModal({ visible: true, groupId: group.id }));
        break;
      case ACTION.DELETE:
        dispatch(showDeleteMultipleNodesModal(true));
        break;
    }
  };

  return (
    <Menu onClick={onClick}>
      <Menu.Item key={ACTION.CONFIGURE_GROUP}>
        <Text>{tn('configure_group')}</Text>
      </Menu.Item>
      <Menu.Divider />
      <Menu.Item key={ACTION.UNGROUP}>
        <Text>{tn('ungroup')}</Text>
      </Menu.Item>
      <Menu.Divider />
      <Menu.Item key={ACTION.DELETE}>
        <Text style={{ color: colors.red500 }}>{tn('delete_group_and_contents')}</Text>
      </Menu.Item>
    </Menu>
  );
};

export default useNodeGroupMenu;
