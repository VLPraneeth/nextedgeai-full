import { Button, Modal } from 'antd';
import cx from 'classnames';
import { useCallback } from 'react';

import { getEntityPipeline } from 'actions/entityPipelineActions';
import InlineMessage from 'components/InlineMessage';
import { Stack } from 'components/layout';
import { useEnhancedDispatch } from 'hooks/redux';
import { tCommon, tNamespaced } from 'utils/i18nUtil';

import { usePipelineSettings } from '../settings/Settings.hooks';
import { useRealtimePipelineContext } from './RealtimePipeline.context';

import './RealtimePipelineModal.scss';

export interface DisableRealtimePipelineModalProps {
  entityId: string;
  saveChanges: any;
}

const tn = tNamespaced('RealtimePipeline');

const DisableRealtimePipelineModal = ({ entityId, saveChanges }: DisableRealtimePipelineModalProps) => {
  const dispatch = useEnhancedDispatch();

  const { disabledVisible, setDisabledVisible, setEnabled } = useRealtimePipelineContext();

  const { version } = usePipelineSettings();

  const onClose = useCallback(() => {
    setDisabledVisible(false);
  }, [setDisabledVisible]);

  const onDisableRealtime = useCallback(() => {
    saveChanges(undefined, undefined, undefined, { realtimePipeline: false }).then(() => {
      dispatch(getEntityPipeline(entityId, version));
    });
    setEnabled(false);
    onClose();
  }, [saveChanges, setEnabled, onClose, dispatch, entityId, version]);

  return (
    <Modal
      title={tn('confirm_turn_off_realtime')}
      className={cx('realtime-pipeline-modal')}
      centered
      visible={disabledVisible}
      onOk={() => onClose()}
      onCancel={() => onClose()}
      footer={
        <>
          <Button key="cancel" onClick={onClose}>
            {tCommon('cancel')}
          </Button>
          <Button key="ok" type="primary" onClick={onDisableRealtime}>
            {tCommon('save')}
          </Button>
        </>
      }
      cancelText=""
      destroyOnClose>
      <div className="realtime-pipeline-modal__content">
        <Stack spacing="md">
          <InlineMessage allowMultiline initallyExpanded type="info">
            {tn('turn_off_description')}
          </InlineMessage>
        </Stack>
      </div>
    </Modal>
  );
};

export default DisableRealtimePipelineModal;
