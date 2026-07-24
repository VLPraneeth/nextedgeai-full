import { Button, Modal } from 'antd';
import cx from 'classnames';
import { useState } from 'react';

import { getConnectorsMetadata } from 'actions/connectorActions';
import Spinner from 'components/Spinner';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { useApproveCustomSynapseMutation } from 'store/custom-synapse/sdk/api';
import { showCustomSynapseApprovalModal } from 'store/custom-synapse/sdk/slice';
import { tNamespaced, tc } from 'utils/i18nUtil';

import './CustomSynapseApprovalModal.scss';

const tn = tNamespaced('CustomSynapseApprovalModal');

export const CustomSynapseApprovalModal = () => {
  const dispatch = useEnhancedDispatch();
  const { customSynapse, visible } = useEnhancedSelector((state) => state.customSynapse.customSynapseApprovalModal);

  const [approveSynapse] = useApproveCustomSynapseMutation();
  const [isProcessing, setIsProcessing] = useState(false);

  const approveDisabled = isProcessing || !customSynapse;

  const handleClose = () => {
    dispatch(showCustomSynapseApprovalModal({ visible: false, customSynapse: null }));
  };

  const handleApprove = async () => {
    if (customSynapse) {
      setIsProcessing(true);
      await approveSynapse({ connectorMetaDefinitionId: customSynapse.id });
      await dispatch(getConnectorsMetadata());
      setIsProcessing(false);
      handleClose();
    }
  };

  const footer = (
    <>
      <Button onClick={handleClose}>{tc('close')}</Button>
      <Button
        className={cx(isProcessing && 'custom-synapse-approval-modal__approve-button')}
        onClick={handleApprove}
        disabled={approveDisabled}
        type="primary">
        {isProcessing && <Spinner />}
        {isProcessing ? tn('approving') : tn('approve')}
      </Button>
    </>
  );

  return (
    <Modal
      visible={visible}
      title={tn('title')}
      onCancel={handleClose}
      onOk={handleApprove}
      footer={footer}
      className="custom-synapse-approval-modal">
      <div>
        <span>{tn('warning')}</span>
        <br />
        <br />
        <span>{tn('confirmation')}</span>
      </div>
    </Modal>
  );
};
