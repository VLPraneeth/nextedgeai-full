//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { navigate } from '@reach/router';
import { Button, Modal } from 'antd';
import { Fragment } from 'react';

import { updateEntityPipeline } from 'actions/entityPipelineActions';
import { updateFieldPipeline } from 'actions/fieldPipelineActions';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { graphChanged, showUnsavedConfirmModal } from 'store/pipeline/actions';
import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';

import './UnsavedConfirmModal.less';

const tn = tNamespaced('UnsavedConfirmModal');

const UnsavedConfirmModal = () => {
  const dispatch = useEnhancedDispatch();

  const { pipelineId, currentGraph } = useEnhancedSelector((state) => state.pipeline);
  const { navigatingTo } = useEnhancedSelector((state) => state.app);
  const { pipelineContext } = useEnhancedSelector((state) => state.entityPipeline);

  const saveChanges = () => {
    dispatch(
      graphChanged({
        changed: null,
        changedScope: null,
        changedId: null,
      })
    );
    if (pipelineId) {
      if (pipelineContext === AppConstants.PIPELINE_CONTEXT.ENTITY) {
        dispatch(updateEntityPipeline(pipelineId, currentGraph));
      } else {
        dispatch(updateFieldPipeline(pipelineId, currentGraph));
      }
    }
    navigatingTo && navigate(navigatingTo);
    close();
  };

  const close = () => {
    dispatch(showUnsavedConfirmModal(false));
  };

  const discardChanges = () => {
    dispatch(
      graphChanged({
        changed: null,
        changedScope: null,
        changedId: null,
      })
    );
    navigatingTo && navigate(navigatingTo);
    close();
  };

  return (
    <Modal
      title={tn('title')}
      className="unsaved-confirm-modal"
      centered
      visible
      footer={
        <Fragment>
          <Button key="cancel" onClick={discardChanges}>
            {tn('discard_changes')}
          </Button>
          <Button key="ok" type="primary" onClick={saveChanges}>
            {tn('save_changes')}
          </Button>
        </Fragment>
      }
      onOk={() => close()}
      onCancel={() => close()}
      destroyOnClose>
      <div className="content-container">
        <div className="description">{tn('description_1')}</div>
        <div className="description">{tn('description_2')}</div>
      </div>
    </Modal>
  );
};

export default UnsavedConfirmModal;
