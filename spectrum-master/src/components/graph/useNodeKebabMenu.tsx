//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import Menu, { ClickParam } from 'antd/lib/menu';
import Text from 'antd/lib/typography/Text';

import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { selectKebabMenuNode } from 'selectors/appSelectors';
import { nodeKebabAction } from 'store/pipeline/actions';
import AppConstants from 'utils/AppConstants';
import { NodeTypeKeys } from 'utils/AppConstants.types';
import { tNamespaced } from 'utils/i18nUtil';
import { colors } from 'utils/LessConstants';

const tn = tNamespaced('NodeKebabMenu');

const ACTION = {
  CONFIGURE_NODE: 'CONFIGURE_NODE',
  REMOVE_FROM_GROUP: 'REMOVE_FROM_GROUP',
  DELETE: 'DELETE',
  DUPLICATE: 'DUPLICATE',
};

const useNodeKebabMenu = () => {
  const kebabMenuNode = useEnhancedSelector(selectKebabMenuNode);
  const dispatch = useEnhancedDispatch();

  if (kebabMenuNode?.nodeType !== 'node') {
    return null;
  }

  const { node } = kebabMenuNode;
  const duplicateSupportedNodeTypes: NodeTypeKeys[] = [AppConstants.NODE_TYPE.ACTION, AppConstants.NODE_TYPE.FUNCTION];

  const onClick: (param: ClickParam) => void = ({ key }) => {
    switch (key) {
      case ACTION.CONFIGURE_NODE:
        dispatch(nodeKebabAction({ nodeId: node.id, node, action: 'configure' }));
        break;
      case ACTION.REMOVE_FROM_GROUP:
        dispatch(nodeKebabAction({ nodeId: node.id, node, action: 'remove_from_group' }));
        break;
      case ACTION.DUPLICATE:
        dispatch(nodeKebabAction({ nodeId: node.id, node, action: 'duplicate' }));
        break;
      case ACTION.DELETE:
        dispatch(nodeKebabAction({ nodeId: node.id, node, action: 'delete' }));
        break;
    }
  };

  return (
    <Menu onClick={onClick}>
      <Menu.Item key={ACTION.CONFIGURE_NODE}>
        <Text>{tn('configure')}</Text>
      </Menu.Item>
      {!!node.groupId && (
        <Menu.Item key={ACTION.REMOVE_FROM_GROUP}>
          <Text>{tn('remove_from_group')}</Text>
        </Menu.Item>
      )}
      {/* Disabling duplicate until we add increased support for handling dangling configuration */}
      {false && duplicateSupportedNodeTypes.includes(node.nodeType) && (
        <Menu.Item key={ACTION.DUPLICATE}>
          <Text>{tn('duplicate')}</Text>
        </Menu.Item>
      )}
      <Menu.Divider />
      <Menu.Item key={ACTION.DELETE}>
        <Text style={{ color: colors.red500 }}>{tn('delete_node')}</Text>
      </Menu.Item>
    </Menu>
  );
};

export default useNodeKebabMenu;
