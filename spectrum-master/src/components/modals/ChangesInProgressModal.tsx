import { Modal, Button } from 'antd';
import { Fragment } from 'react';

import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { setChangesInProgress, setChangesInProgressModal } from 'store/app/actions';
import { tNamespaced } from 'utils/i18nUtil';

import UploadInProgressModal from './UploadInProgressModal';

import './ChangesInProgressModal.less';

export enum ChangesInProgressModalVariants {
  upload = 'Upload',
}
interface ChangesInProgressModalProps {
  variant: ChangesInProgressModalVariants;
}

const ChangesInProgressModal = ({ variant }: ChangesInProgressModalProps) => {
  const tn = tNamespaced('ChangesInProgressModal');

  const dispatch = useEnhancedDispatch();
  const changesInProgressModalState = useEnhancedSelector((state) => state.app.changesInProgressModal);

  const closeModal = () => {
    dispatch(setChangesInProgressModal({ visible: false }));
  };

  const discardChanges = () => {
    changesInProgressModalState.discardChangesAction();
    dispatch(setChangesInProgress(false));
    closeModal();
  };

  const keepEditing = () => {
    changesInProgressModalState.keepEditingAction();
    closeModal();
  };

  if (variant === ChangesInProgressModalVariants.upload) {
    return (
      <UploadInProgressModal leftButtonAction={keepEditing} rightButtonAction={discardChanges} onCancel={keepEditing} />
    );
  }
  return (
    <Modal
      title={tn('title')}
      className="changes-in-progress-modal"
      centered
      visible
      footer={
        <Fragment>
          <Button key="cancel" onClick={keepEditing}>
            {tn('keep_editing')}
          </Button>
          <Button key="ok" type="primary" onClick={discardChanges}>
            {tn('discard_changes')}
          </Button>
        </Fragment>
      }
      onOk={discardChanges}
      onCancel={keepEditing}
      destroyOnClose>
      <div className="content-container">
        <div className="description">{tn('paragraph1')}</div>
        <div className="description">{tn('paragraph2')}</div>
      </div>
    </Modal>
  );
};

export default ChangesInProgressModal;
