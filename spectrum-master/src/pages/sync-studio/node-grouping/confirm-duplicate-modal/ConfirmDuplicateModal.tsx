import { Button, message, Modal } from 'antd';
import ObjectID from 'bson-objectid';
import { useCallback } from 'react';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { useUpdateSelectedNodeIdsQueryParam } from 'pages/sync-studio/pipeline/PipelineEditor.hooks';
import { Editor } from 'pages/sync-studio/pipeline/PipelineEditor.types';
import { selectConfirmDuplicateModalVisible } from 'selectors/pipelineSelectors';
import { showConfirmDuplicateModal } from 'store/pipeline/actions';
import { Node } from 'store/pipeline/types';
import AppConstants from 'utils/AppConstants';
import { NODE_STACKING_BOUNDARY } from 'utils/Pipeline.utils';

// TODO: Properly type existing Node/EditorNode type and get rid of this.
export interface DuplicateNodeModel extends Node {
  x: number;
  y: number;
  metadata: any;
}

export interface ConfirmDuplicateModalProps {
  editor: Editor;
}

export const ConfirmDuplicateModal = withI18n(({ editor }: ConfirmDuplicateModalProps) => {
  const { tn, tc } = useI18nContext();
  const dispatch = useEnhancedDispatch();
  const { visible, node } = useEnhancedSelector(selectConfirmDuplicateModalVisible);
  const updateSelectedNodeIdsQueryParam = useUpdateSelectedNodeIdsQueryParam();

  const handleClose = useCallback(() => {
    dispatch(showConfirmDuplicateModal({ visible: false, node: undefined }));
  }, [dispatch]);

  const handleDuplicate = useCallback(() => {
    if (node) {
      const newId = ObjectID.generate();
      const nodeToDuplicate = { ...(node as DuplicateNodeModel) };

      editor.executeCommand(AppConstants.NODE_ACTION.ADD, {
        type: AppConstants.GRAPH_ITEM_TYPE.NODE,
        addModel: {
          ...nodeToDuplicate,
          x: nodeToDuplicate.x + NODE_STACKING_BOUNDARY,
          y: nodeToDuplicate.y + NODE_STACKING_BOUNDARY,
          id: newId,
          configuration: nodeToDuplicate.configuration || nodeToDuplicate.metadata?.configuration,
          metadata: undefined,
          parent: undefined,
          shouldSkipNotification: true,
        },
      });

      updateSelectedNodeIdsQueryParam([newId]);
      message.success(tn('confirmation'), 3);
    }

    handleClose();
  }, [editor, handleClose, node, tn, updateSelectedNodeIdsQueryParam]);

  const footer = (
    <>
      <Button onClick={handleClose}>{tc('cancel')}</Button>
      <Button onClick={handleDuplicate} type="primary">
        {tn('duplicate')}
      </Button>
    </>
  );

  return (
    <Modal footer={footer} title={tn('title')} onCancel={handleClose} centered visible={visible}>
      {tn('description')}
    </Modal>
  );
}, 'ConfirmDuplicateModal');
