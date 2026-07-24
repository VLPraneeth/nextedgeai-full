//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Modal } from 'antd';
import message from 'antd/lib/message';
import { useCallback } from 'react';

import { useDeleteCustomSynapseMutation, useWithdrawApprovalCustomSynapseMutation } from 'store/custom-synapse/sdk/api';
import { useSubmitForApprovalCustomSynapseMutation } from 'store/custom-synapse/sdk/api';
import { RequestExceptionType } from 'utils/AjaxUtil';
import { tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('CustomSynapse');

export const useSDKCustomSynapseStudio = () => {
  const [deleteCustomSynapse] = useDeleteCustomSynapseMutation();
  const [submitSynapseForApproval] = useSubmitForApprovalCustomSynapseMutation();
  const [withdrawSynapseForApproval] = useWithdrawApprovalCustomSynapseMutation();

  const deleteSynapse = useCallback(
    (connectorMetaDefinitionId: string) => {
      return deleteCustomSynapse({ connectorMetaDefinitionId })
        .unwrap()
        .catch((error: RequestExceptionType) => {
          Modal.error({
            title: tn('unable_to_delete_synapse'),
            content: error.data.message,
          });
        });
    },
    [deleteCustomSynapse]
  );

  const submitForApproval = useCallback(
    (connectorMetaDefinitionId: string, name: string) => {
      return submitSynapseForApproval({ connectorMetaDefinitionId })
        .unwrap()
        .then(() => {
          message.success(tn('submitted_success', { name }));
        })
        .catch((error: RequestExceptionType) => {
          Modal.error({
            title: tn('submitted_error', { name }),
            content: error.data.message,
          });
        });
    },
    [submitSynapseForApproval]
  );

  const withdrawFromApproval = useCallback(
    (connectorMetaDefinitionId: string, name: string) => {
      return withdrawSynapseForApproval({ connectorMetaDefinitionId })
        .unwrap()
        .then(() => {
          message.success(tn('withdraw_success', { name }));
        })
        .catch((error: RequestExceptionType) => {
          Modal.error({
            title: tn('withdraw_error', { name }),
            content: error.data.message,
          });
        });
    },
    [withdrawSynapseForApproval]
  );

  return { deleteSynapse, submitForApproval, withdrawFromApproval };
};
