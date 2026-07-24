//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { useMatch } from '@reach/router';
import { useCallback } from 'react';

import Modal from 'components/Modal';
import { useEnhancedDispatch as useDispatch } from 'hooks/redux';
import {
  useDeleteCustomActionMutation,
  usePublishCustomActionMutation,
  useShareCustomActionMutation,
} from 'store/custom-action/api';
import { getEntityPipelineActions, getFieldPipelineActions } from 'store/pipeline-actions';
import { RequestExceptionType } from 'utils/AjaxUtil';
import { tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('CustomAction');

export const useActionStudio = () => {
  const [deleteCustomAction] = useDeleteCustomActionMutation();
  const [publishCustomAction] = usePublishCustomActionMutation();
  const [shareCustomAction] = useShareCustomActionMutation();
  const dispatch = useDispatch();
  const epIdMatch = useMatch('/sync-studio/entity/:entityId/*');
  const fpIdMatch = useMatch('/sync-studio/entity/:entityId/field/:fieldId/*');

  const publishAction = useCallback(
    (actionId: string) => {
      publishCustomAction({ actionId })
        .unwrap()
        .then(() => {
          dispatch(
            fpIdMatch?.fieldId
              ? getFieldPipelineActions(fpIdMatch.fieldId)
              : getEntityPipelineActions(epIdMatch?.entityId || '')
          );
        })
        .catch((error: RequestExceptionType) => {
          Modal.error({
            title: tn('publish_this_action'),
            content: error.data.message,
          });
        });
    },
    [dispatch, epIdMatch?.entityId, fpIdMatch?.fieldId, publishCustomAction]
  );

  const shareAction = useCallback(
    (actionId: string, shareWithOrg: boolean, shareGlobally: boolean) => {
      shareCustomAction({ actionId, shareWithOrg, shareGlobally })
        .unwrap()
        .then(() => {
          dispatch(
            fpIdMatch?.fieldId
              ? getFieldPipelineActions(fpIdMatch.fieldId)
              : getEntityPipelineActions(epIdMatch?.entityId || '')
          );
        })
        .catch((error: RequestExceptionType) => {
          Modal.error({
            title: tn('share_this_action'),
            content: error.data.message,
          });
        });
    },
    [dispatch, epIdMatch?.entityId, fpIdMatch?.fieldId, shareCustomAction]
  );

  const deleteAction = useCallback(
    (actionId: string) => {
      deleteCustomAction({ actionId })
        .unwrap()
        .then(() => {
          dispatch(
            fpIdMatch?.fieldId
              ? getFieldPipelineActions(fpIdMatch.fieldId)
              : getEntityPipelineActions(epIdMatch?.entityId || '')
          );
        })
        .catch((error: RequestExceptionType) => {
          Modal.error({
            title: tn('delete_this_action'),
            content: error.data.message,
          });
        });
    },
    [deleteCustomAction, dispatch, epIdMatch?.entityId, fpIdMatch?.fieldId]
  );

  return {
    publishAction,
    shareAction,
    deleteAction,
  };
};
