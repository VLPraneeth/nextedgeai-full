import { Button, message, Modal } from 'antd';
import { partition } from 'lodash';
import { useCallback, useEffect, useState } from 'react';
import InlineSVG from 'react-inlinesvg';

import { setSelectedGraphNode } from 'actions/entityPipelineActions';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { LOOP_SUB_NODE_API_NAMES } from 'pages/sync-studio/pipeline/PipelineEditor.constants';
import { useUpdateSelectedNodeIdsQueryParam } from 'pages/sync-studio/pipeline/PipelineEditor.hooks';
import { selectDeleteMutipleNodesModalVisible, selectSelectedGraphItemsWithIcons } from 'selectors/pipelineSelectors';
import { EMPTY_ARRAY } from 'store/constants';
import { showDeleteMultipleNodesModal } from 'store/pipeline/actions';
import { NodeOrGroup } from 'store/pipeline/types';
import AppConstants from 'utils/AppConstants';
import { humanize } from 'utils/StringUtil';

import './DeleteMultipleNodesModal.less';

export interface DeleteMultipleNodesModalProps {
  editor: any;
}

export const DeleteMultipleNodesModal = withI18n(({ editor }: DeleteMultipleNodesModalProps) => {
  const { tc, tn } = useI18nContext();
  const dispatch = useEnhancedDispatch();
  const selectedGraphItems = useEnhancedSelector(selectSelectedGraphItemsWithIcons);
  const visible = useEnhancedSelector(selectDeleteMutipleNodesModalVisible);
  const updateSelectedNodeIds = useUpdateSelectedNodeIdsQueryParam();

  const [displayedGraphItems, setDisplayedGraphItems] = useState<NodeOrGroup[]>(EMPTY_ARRAY);
  const [graphItemCounts, setGraphItemCounts] = useState({ numberOfNodes: 0, numberOfGroups: 0 });

  const close = useCallback(() => {
    dispatch(showDeleteMultipleNodesModal(false));
  }, [dispatch]);

  useEffect(() => {
    const mutableGraphItems = selectedGraphItems.filter((graphItem: NodeOrGroup) => {
      const isReadOnlyGraphItem =
        graphItem.nodeType === AppConstants.NODE_TYPE.CORE_ENTITY ||
        graphItem.nodeType === AppConstants.NODE_TYPE.CORE_ATTRIBUTE;

      if (isReadOnlyGraphItem) {
        return false;
      }

      if (LOOP_SUB_NODE_API_NAMES.includes(graphItem.apiName)) {
        return false;
      }

      return true;
    });

    let numberOfNodes = 0;
    let numberOfGroups = 0;

    mutableGraphItems.forEach((graphItem) => {
      if (graphItem.nodeType === AppConstants.NODE_TYPE.CUSTOM_GROUP) {
        numberOfGroups++;
      } else {
        numberOfNodes++;
      }
    });

    setDisplayedGraphItems(mutableGraphItems);
    setGraphItemCounts({ numberOfNodes, numberOfGroups });
  }, [selectedGraphItems]);

  const handleDelete = () => {
    // Unselect all nodes before the delete to ensure predicate nodes get
    // automatically removed
    updateSelectedNodeIds([]);
    dispatch(setSelectedGraphNode());

    const page = editor.getCurrentPage();

    // Delete nodes before groups. If we deleted the group first the nodes
    // within the group would not be found in the page.find() below
    const itemsToDelete = partition(
      displayedGraphItems,
      (graphItem) => graphItem.nodeType !== AppConstants.NODE_TYPE.CUSTOM_GROUP
    ).flat();

    itemsToDelete.forEach((graphItem) => {
      const item = page.find(graphItem.id);

      if (item) {
        editor.executeCommand(() => {
          page.remove(item);
        });
      }
    });

    if (graphItemCounts.numberOfGroups) {
      message.success(tn('delete_group_confirmation'), 3);
    } else {
      message.success(tn('delete_nodes_confirmation'), 3);
    }

    close();
  };

  const footer = (
    <>
      <Button onClick={close}>{tc('cancel')}</Button>
      <Button type="primary" onClick={handleDelete}>
        {tn('delete_nodes')}
      </Button>
    </>
  );

  return (
    <Modal
      visible={visible}
      onCancel={close}
      footer={footer}
      centered
      title={tn('title')}
      className="delete-multiple-nodes-modal">
      <div className="delete-multiple-nodes-modal__info">
        <p>{tn('warning')}</p>
        <h1>
          {graphItemCounts.numberOfGroups >= 1
            ? tn('delete_count_items', {
                groupCount: graphItemCounts.numberOfGroups,
                nodeCount: graphItemCounts.numberOfNodes,
              })
            : tn('delete_count_nodes', { nodeCount: graphItemCounts.numberOfNodes })}
        </h1>
      </div>
      <div className="delete-multiple-nodes-modal__node-list">
        {displayedGraphItems.map((graphItem) => {
          return (
            <div key={graphItem.id} className="delete-multiple-nodes-modal__node">
              <InlineSVG
                className="delete-multiple-nodes-modal__node-icon"
                src={graphItem.iconPath ?? graphItem.iconAssetPath ?? ''}
              />
              <div className="delete-multiple-nodes-modal__node-labels">
                <h2>{graphItem.label ?? graphItem.name}</h2>
                <p>{humanize(graphItem.nodeType)}</p>
              </div>
            </div>
          );
        })}
      </div>
    </Modal>
  );
}, 'DeleteMultipleNodesModal');
