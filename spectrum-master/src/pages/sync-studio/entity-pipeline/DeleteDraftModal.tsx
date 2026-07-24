//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Button, Modal } from 'antd';
import { useCallback, useState } from 'react';
import { useDispatch } from 'react-redux';

import { discardEntityPipeline, showDeleteDraftModal } from 'actions/entityPipelineActions';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { Spacer } from 'components/layout';
import { TranslatedText } from 'components/typography';
import { useEnhancedSelector } from 'hooks/redux';
import { selectDeleteEntityDraft } from 'selectors/pipelineSelectors';
import AppConstants from 'utils/AppConstants';
import { tNamespaced, tc } from 'utils/i18nUtil';

const tn = tNamespaced('PipelineEditor');
const tv = tNamespaced('CreateVersionModal');

const DeleteDraftModal = () => {
  const dispatch = useDispatch();
  const { entityId, refreshPipelineOnDelete } = useEnhancedSelector(selectDeleteEntityDraft);

  const [versionName, setVersionName] = useState('');
  const [versionSummary, setVersionSummary] = useState('');
  const [hasValidationError, setHasValidationError] = useState(false);

  const close = useCallback(() => {
    dispatch(showDeleteDraftModal(false));
  }, [dispatch]);

  const discardDraft = useCallback(async () => {
    if (!versionName.trim()) {
      setHasValidationError(true);
      return;
    }

    const options = {
      versionInfo: { name: versionName, summary: versionSummary },
      refreshPipelineOnDelete,
    };
    await dispatch(discardEntityPipeline(entityId, options));
    dispatch(showDeleteDraftModal(false));
  }, [dispatch, entityId, refreshPipelineOnDelete, versionName, versionSummary]);

  return (
    <Modal
      title={tn('delete_draft_question')}
      centered
      visible
      footer={
        <>
          <Button key="cancel" onClick={close}>
            {tc('cancel')}
          </Button>
          <Button key="ok" type="primary" onClick={discardDraft}>
            {tc('delete')}
          </Button>
        </>
      }
      onOk={close}
      onCancel={close}
      destroyOnClose>
      <TranslatedText namespace="PipelineEditor" text="delete_draft_entity_pipeline_versioned" />

      <Spacer y="md" />

      <InputWithLabel
        label={tv('version_name')}
        required
        placeholder={tv('type_version_name')}
        validateStatus={hasValidationError ? 'error' : undefined}
        datatype={AppConstants.INPUT_TYPE.STRING}
        value={versionName}
        onChange={(e: React.ChangeEvent<HTMLInputElement>) => setVersionName(e.target.value)}
      />

      <InputWithLabel
        label={tv('summary_optional')}
        placeholder={tv('add_description')}
        datatype={AppConstants.INPUT_TYPE.TEXTAREA}
        value={versionSummary}
        onChange={(e: React.ChangeEvent<HTMLInputElement>) => setVersionSummary(e.target.value)}
      />
    </Modal>
  );
};

export default DeleteDraftModal;
