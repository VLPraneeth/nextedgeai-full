import { Handle, NodeProps, Position } from '@xyflow/react';
import { Dropdown, Menu } from 'antd';
import { ClickParam } from 'antd/lib/menu';
import cx from 'classnames';
import { memo } from 'react';

import { showNodeConfigModal } from 'actions/entityPipelineActions';
import { ReactComponent as ChevronDown } from 'assets/icons/chevron-down.svg';
import { useEnhancedDispatch } from 'hooks/redux';
import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';
import { humanize } from 'utils/StringUtil';

import produce from 'immer';
import { map } from 'lodash';
import { useConnectionContext } from '../context/ConnectionContext';
import { usePillDropdownContext } from '../context/PillDropdownContext';
import { usePipelineEditor } from '../context/PipelineEditorV2.context';
import useEnhancedReactFlow from '../hooks/useEnhancedReactFlow';
import useOptionsForPredicateNode from '../hooks/useOptionsForPredicateNode';
import { useSelectedNodeIds } from '../hooks/useSelectedGraphNode';
import { ExtraDataFunctionActionNode, ReactFlowNodeV2 } from '../types/ReactFlow.types';

const tnp = tNamespaced('PipelineEditor');

export const EDITABLE_PILL_NODES: string[] = [
  AppConstants.PREDICATE_FUNCTION_NAME,
  AppConstants.CASE_BRANCH_FUNCTION_NAME,
];

export const nodeIsEditiblePill = (node: ReactFlowNodeV2) => {
  const { functionActionApiName } = node.data.extraData as ExtraDataFunctionActionNode;
  return node.type === 'pillNode' && EDITABLE_PILL_NODES.includes(functionActionApiName);
};

const staticLabelMap: Record<string, string> = {
  [AppConstants.FOR_EACH_FUNCTION_NAME]: tnp('for_each'),
  [AppConstants.END_LOOP_FUNCTION_NAME]: tnp('end_loop'),
};

const PillNode = memo((props: NodeProps<ReactFlowNodeV2>) => {
  const { data, selected, dragging } = props;
  const { functionActionApiName } = data.extraData as ExtraDataFunctionActionNode;

  const { setNodes } = useEnhancedReactFlow();
  const { savePipeline } = usePipelineEditor();

  const options = useOptionsForPredicateNode(props);

  const { connectionSource, connectionTarget } = useConnectionContext();
  const dispatch = useEnhancedDispatch();

  const { dropdownVisibleNodeId, setDropdownVisibleNodeId } = usePillDropdownContext();
  const selectedNodeCount = useSelectedNodeIds().nodeIds.length;

  const isSelected = Boolean(selected || (connectionSource && connectionTarget === props.id));

  const showExtras = !connectionSource && !dragging;

  let label = '\u00A0';
  let isDynamic = true;
  let menu = null;
  if (staticLabelMap[functionActionApiName]) {
    label = staticLabelMap[functionActionApiName];
    isDynamic = false;
  } else if (options && EDITABLE_PILL_NODES.includes(functionActionApiName)) {
    label = humanize(data.fullNode.configuration.value as string);

    menu = (
      <Menu
        onClick={(ev: ClickParam) => {
          const key = ev.item.props.eventKey;

          setNodes((nodes) => {
            return nodes.map((node) => {
              if (node.id === props.id) {
                const value = options[key] ?? key;
                return produce(node, (draftNode) => {
                  draftNode.data.fullNode.configuration.value = value;
                });
              }
              return node;
            });
          });

          // Timeout waits for changes to be stored in the React Flow internal state
          setTimeout(() => {
            savePipeline();
          }, 200);
        }}>
        {map(options, (value, key) => (
          <Menu.Item key={key}>{key}</Menu.Item>
        ))}
      </Menu>
    );
  }

  const isOpen =
    Boolean(menu) && dropdownVisibleNodeId === props.id && selected && !dragging && selectedNodeCount === 1;

  return (
    <>
      <div className="bottom-handle-container" style={{ opacity: showExtras ? undefined : 0 }}>
        <Handle type="source" position={Position.Bottom} className="bottom-handle" />
      </div>
      <Dropdown overlay={menu} trigger={['click']} visible={isOpen}>
        <div
          onDoubleClick={() => {
            dispatch(showNodeConfigModal(true));
          }}
          className={cx('pipeline-node-wrapper', isSelected && 'selected')}>
          <div className={cx('pipeline-pill-node-wrapper', isSelected && 'selected')}>
            <span className="pipeline-pill-node-wrapper__label">{label}</span>
            {isDynamic && (
              <ChevronDown
                className={cx('pipeline-pill-node-wrapper__arrow', {
                  'is-open': isOpen,
                })}
              />
            )}
          </div>
        </div>
      </Dropdown>
      <Handle type="target" position={Position.Bottom} className="custom-target-handle" isConnectableStart={false} />
    </>
  );
});

export default PillNode;
