import { Alert, Button, Modal, message } from 'antd';
import { useCallback, useState } from 'react';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { selectCreateVersionModalVisible } from 'selectors/pipelineSelectors';
import { useCreatePipelineVersionMutation } from 'store/pipeline/api';
import { showCreateVersionModal } from 'store/pipeline/slice';
import AppConstants from 'utils/AppConstants';

export interface CreateVersionModalProps {
  entityId: string;
}

const CreateVersionModal = ({ entityId }: CreateVersionModalProps) => {
  const { tn, tc } = useI18nContext();
  const dispatch = useEnhancedDispatch();
  const { visible } = useEnhancedSelector(selectCreateVersionModalVisible);

  const [name, setVersionName] = useState('');
  const [summary, setSummary] = useState('');
  const [createVersionError, setCreateVersionError] = useState('');
  const [loading, setLoading] = useState(false);
  const [hasValidationError, setHasValidationError] = useState(false);

  const [createVersion] = useCreatePipelineVersionMutation();

  const handleClose = useCallback(() => {
    setVersionName('');
    setSummary('');
    dispatch(showCreateVersionModal({ visible: false }));
  }, [dispatch]);

  const handleCreateVersion = () => {
    if (!name.trim()) {
      setHasValidationError(true);
      return;
    }

    setLoading(true);

    createVersion({
      name: name.trim(),
      summary,
      syncariEntityId: entityId,
    })
      .unwrap()
      .then(() => {
        message.success(tn('version_created_successfully'));
        handleClose();
      })
      .catch((resp) => {
        setCreateVersionError(resp.data.message || resp.data.error);
      })
      .finally(() => {
        setLoading(false);
      });
  };

  const footer = (
    <>
      {createVersionError && <Alert type="warning" message={createVersionError} />}
      <Button onClick={handleClose}>{tc('cancel')}</Button>
      <Button onClick={handleCreateVersion} type="primary" loading={loading}>
        {tc('create')}
      </Button>
    </>
  );

  return (
    <Modal footer={footer} title={tn('title')} onCancel={handleClose} centered visible={visible}>
      <InputWithLabel
        label={tn('version_name')}
        placeholder={tn('type_version_name')}
        required
        value={name}
        autoFocus
        validateStatus={hasValidationError ? 'error' : undefined}
        onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
          setHasValidationError(false);
          setVersionName(e.target.value);
        }}
      />
      <InputWithLabel
        label={tn('summary_optional')}
        placeholder={tn('add_description')}
        datatype={AppConstants.INPUT_TYPE.TEXTAREA}
        value={summary}
        onChange={(e: React.ChangeEvent<HTMLInputElement>) => setSummary(e.target.value)}
      />
    </Modal>
  );
};

export default withI18n(CreateVersionModal, 'CreateVersionModal');
