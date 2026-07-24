import { Modal } from 'antd';

import { showResyncDraftWarningModal, showResyncModal } from 'actions/entityPipelineActions';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { tc, tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('EntitySyncStatus');

export const ResyncDraftWarningModal = () => {
  const dispatch = useEnhancedDispatch();
  const visible = useEnhancedSelector((state) => state.entityPipeline.showingResyncDraftWarningModal);

  const handleClose = () => {
    dispatch(showResyncDraftWarningModal(false));
  };

  const handleOk = () => {
    handleClose();
    dispatch(showResyncModal(true));
  };

  return (
    <Modal
      key="draft-warning-modal"
      visible={visible}
      centered
      title={tn('draft_modal_warning_title')}
      okText={tc('continue')}
      onOk={handleOk}
      onCancel={handleClose}>
      <div dangerouslySetInnerHTML={{ __html: tn('draft_modal_warning_body_html') }} />
    </Modal>
  );
};
