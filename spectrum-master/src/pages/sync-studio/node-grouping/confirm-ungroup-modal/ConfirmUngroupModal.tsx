import { Button, Modal } from 'antd';
import { useCallback } from 'react';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { selectConfirmUngroupModalVisible } from 'selectors/pipelineSelectors';
import { groupNodeUpdateAction, showConfirmUngroupModal } from 'store/pipeline/actions';

export const ConfirmUngroupModal = withI18n(() => {
  const { tn, tc } = useI18nContext();
  const dispatch = useEnhancedDispatch();
  const { visible, groupId } = useEnhancedSelector(selectConfirmUngroupModalVisible);

  const handleClose = useCallback(() => {
    dispatch(showConfirmUngroupModal({ visible: false, groupId: undefined }));
  }, [dispatch]);

  const handleUngroup = useCallback(() => {
    if (groupId) {
      dispatch(groupNodeUpdateAction({ groupId, action: 'ungroup' }));
    }

    handleClose();
  }, [dispatch, groupId, handleClose]);

  const footer = (
    <>
      <Button onClick={handleClose}>{tc('cancel')}</Button>
      <Button onClick={handleUngroup} type="primary">
        {tn('ungroup')}
      </Button>
    </>
  );

  return (
    <Modal footer={footer} title={tn('title')} onCancel={handleClose} centered visible={visible}>
      {tn('description')}
    </Modal>
  );
}, 'ConfirmUngroupModal');
