import { Button, Modal } from 'antd';

import { tNamespaced, tc } from 'utils/i18nUtil';
import './SQLShortcutsModal.scss';

export interface SQLShortcutsModalProps {
  visible: boolean;
  handleVisibleChange: (visible: boolean) => void;
}

const tn = tNamespaced('InsightsStudio');

export function SQLShortcutsModal({ visible, handleVisibleChange }: SQLShortcutsModalProps) {
  return (
    <Modal
      visible={visible}
      centered
      width={350}
      onCancel={() => handleVisibleChange(false)}
      footer={
        <Button type="primary" onClick={() => handleVisibleChange(false)}>
          {tc('close')}
        </Button>
      }
      title={tn('AdvanceDataset.shortcuts')}>
      <div className="shortcuts-modal">
        <div className="shortcuts-modal__labels">
          <p>{tn('AdvanceDataset.autocomplete')}:</p>
          <p>{tn('AdvanceDataset.create_variable')}:</p>
          <p>{tn('AdvanceDataset.edit_variable')}:</p>
          <p>{tn('AdvanceDataset.preview')}:</p>
        </div>
        <div className="shortcuts-modal__values">
          <pre>{tn('AdvanceDataset.autocomplete_shortcut')}</pre>
          <pre>{tn('AdvanceDataset.create_variable_shortcut')}</pre>
          <pre>{tn('AdvanceDataset.edit_variable_shortcut')}</pre>
          <pre>{tn('AdvanceDataset.preview_shortcut')}</pre>
        </div>
      </div>
    </Modal>
  );
}
