import { Modal, Button } from 'antd';
import { Fragment } from 'react';

import { tNamespaced } from 'utils/i18nUtil';

import './ChangesInProgressModal.less';

interface UploadInProgressModalProps {
  leftButtonAction: () => void;
  rightButtonAction: () => void;
  onCancel: () => void;
}

const tn = tNamespaced('ChangesInProgressModal.Upload');

const UploadInProgressModal = ({ leftButtonAction, rightButtonAction, onCancel }: UploadInProgressModalProps) => (
  <Modal
    title={tn('title')}
    className="changes-in-progress-modal"
    centered
    visible
    footer={
      <Fragment>
        <Button key="cancel" onClick={leftButtonAction}>
          {tn('keep_editing')}
        </Button>
        <Button key="ok" type="primary" onClick={rightButtonAction}>
          {tn('discard_changes')}
        </Button>
      </Fragment>
    }
    onCancel={onCancel}
    destroyOnClose>
    <div className="content-container">
      <div className="description">{tn('paragraph1')}</div>
      <div className="description">{tn('paragraph2')}</div>
    </div>
  </Modal>
);

export default UploadInProgressModal;
