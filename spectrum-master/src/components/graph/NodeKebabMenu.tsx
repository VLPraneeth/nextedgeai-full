import { Dropdown } from 'antd';
import { ReactNode } from 'react';

import { useEnhancedSelector } from 'hooks/redux';
import { selectKebabMenuNode } from 'selectors/appSelectors';

import useConnectorMenu from './useConnectorMenu';
import useEdgeOptionsMenu from './useEdgeOptionsMenu';
import useNodeGroupMenu from './useNodeGroupMenu';
import useNodeKebabMenu from './useNodeKebabMenu';

const NodeKebabMenu = () => {
  const selectedNode = useEnhancedSelector(selectKebabMenuNode);
  const connectorMenu = useConnectorMenu();
  const groupMenu = useNodeGroupMenu();
  const nodeMenu = useNodeKebabMenu();
  const edgeMenu = useEdgeOptionsMenu();

  let overlay: ReactNode = <div />;

  if (selectedNode?.nodeType === 'connector') {
    overlay = connectorMenu;
  } else if (selectedNode?.nodeType === 'group') {
    overlay = groupMenu;
  } else if (selectedNode?.nodeType === 'node') {
    overlay = nodeMenu;
  } else if (selectedNode?.nodeType === 'predicate-node') {
    overlay = edgeMenu;
  }

  return (
    <Dropdown overlay={overlay} trigger={['click']} key="connectorMenuDropdown">
      <div id="nodeKebabMenu" />
    </Dropdown>
  );
};

export default NodeKebabMenu;
