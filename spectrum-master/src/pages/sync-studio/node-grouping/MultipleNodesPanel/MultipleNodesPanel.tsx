import { Icon, Tooltip } from 'antd';
import { useCallback, useEffect, useState } from 'react';

import { ReactComponent as ClipboardIcon } from 'assets/icons/record-transactions.svg';
import Button from 'components/Button';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import PropertyPanelTitle from 'components/PropertyPanelTitle';
import { ScrollableArea } from 'components/scrollable-area/ScrollableArea';
import { SelectionInfoWithAction } from 'components/SelectionInfoWithAction';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import {
  useClearSelectedNodes,
  useCopySelectedItems,
  useMultipleNodeCapability,
  usePasteNodes,
} from 'pages/sync-studio/pipeline/PipelineEditor.hooks';
import { Editor } from 'pages/sync-studio/pipeline/PipelineEditor.types';
import {
  selectCurrentGraphEdges,
  selectCurrentGraphNodes,
  selectSelectedGraphItems,
} from 'selectors/pipelineSelectors';
import { showCreateGroupPanel, showDeleteMultipleNodesModal } from 'store/pipeline/actions';
import { Node } from 'store/pipeline/types';
import { validateNodesToCreateGroup } from 'utils/Pipeline.utils';

import './MultipleNodesPanel.less';
export interface CopiedGraphItems {
  instanceId: string;
  scope: string;
  copyNodes: Node[];
  copyEdges: any[];
  copyGroups: any[];
}

export interface MultipleNodesPanelProps {
  editor: Editor;
  scope: string;
}

export const MultipleNodesPanel = withI18n(({ editor, scope }: MultipleNodesPanelProps) => {
  const { tn } = useI18nContext();
  const dispatch = useEnhancedDispatch();
  const selectedGraphItems = useEnhancedSelector(selectSelectedGraphItems);
  const currentGraphNodes = useEnhancedSelector(selectCurrentGraphNodes);
  const currentGraphEdges = useEnhancedSelector(selectCurrentGraphEdges);
  const clearSelectedNodes = useClearSelectedNodes();

  const [createGroupValidationMessage, setCreateGroupValidationMessage] = useState<string | undefined>(undefined);

  useEffect(() => {
    const msg = validateNodesToCreateGroup(currentGraphNodes, currentGraphEdges, selectedGraphItems);
    setCreateGroupValidationMessage(msg);
  }, [currentGraphEdges, currentGraphNodes, selectedGraphItems]);

  const handleCreateGroup = useCallback(() => {
    dispatch(showCreateGroupPanel({ visible: true }));
  }, [dispatch]);

  const handleDeleteNodes = useCallback(() => {
    dispatch(showDeleteMultipleNodesModal(true));
  }, [dispatch]);

  const copyNodes = useCopySelectedItems(editor, scope);
  const pasteNodes = usePasteNodes(editor, scope);
  const { canClone, cloneDisableTooltip, canCopy, copyDisableTooltip } = useMultipleNodeCapability(editor);

  return (
    <div className="multiple-nodes-panel">
      <PropertyPanelTitle title={tn('title')} onClose={clearSelectedNodes} />
      <ScrollableArea className="multiple-nodes-panel__content">
        <SelectionInfoWithAction
          selectionText={tn('selected_nodes', { nodeCount: selectedGraphItems.length })}
          action={clearSelectedNodes}
          actionText={tn('clear_selection')}
        />
        <Tooltip title={createGroupValidationMessage}>
          <div>
            <Button
              onClick={handleCreateGroup}
              size="large"
              className="multiple-nodes-panel__action-button"
              disabled={Boolean(createGroupValidationMessage)}>
              <Icon type="plus" className="multiple-nodes-panel__action-icon" />
              <span>{tn('create_group')}</span>
            </Button>
          </div>
        </Tooltip>
        <Tooltip title={cloneDisableTooltip}>
          <div>
            <Button
              onClick={() => {
                copyNodes(false);
                pasteNodes();
              }}
              disabled={!canClone}
              size="large"
              className="multiple-nodes-panel__action-button">
              <Icon type="copy" className="multiple-nodes-panel__action-icon" />
              <span>{tn('clone_selection')}</span>
            </Button>
          </div>
        </Tooltip>
        <Tooltip title={copyDisableTooltip}>
          <div>
            <Button
              onClick={() => copyNodes()}
              size="large"
              className="multiple-nodes-panel__action-button"
              disabled={!canCopy}>
              <div className="multiple-nodes-panel__action-icon-svg">
                <ClipboardIcon />
              </div>
              <span className="multiple-nodes-panel__action-span">{tn('copy_selection')}</span>
            </Button>
          </div>
        </Tooltip>
        <Button onClick={handleDeleteNodes} size="large" className="multiple-nodes-panel__action-button">
          <Icon type="delete" className="multiple-nodes-panel__action-icon" />
          <span>{tn('delete_nodes')}</span>
        </Button>
      </ScrollableArea>
    </div>
  );
}, 'MultipleNodesPanel');
