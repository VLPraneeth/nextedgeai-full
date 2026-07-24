import { Alert, Button, Modal, message } from 'antd';
import Radio from 'antd/lib/radio';
import cx from 'classnames';
import { partition, sortBy } from 'lodash';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import Select from 'components/inputs/Select';
import { HStack, Stack } from 'components/layout';
import Tooltip from 'components/tooltip/Tooltip';
import { TranslatedText } from 'components/typography';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { selectRestoreVersionModalVisible } from 'selectors/pipelineSelectors';
import { useGetPipelinesForVersionQuery, useRestorePipelineVersionMutation } from 'store/pipeline/api';
import { showRestoreVersionModal } from 'store/pipeline/slice';

export interface RestoreVersionModalProps {
  entityId: string;
}

const RestoreVersionModal = ({ entityId }: RestoreVersionModalProps) => {
  const { tn, tc } = useI18nContext();
  const dispatch = useEnhancedDispatch();
  const { versionId, name, visible, versionTwoId, versionOneNumber, versionTwoNumber } = useEnhancedSelector(
    selectRestoreVersionModalVisible
  );

  const defaultVersion = versionTwoId || versionId;

  const [restoreVersionError, setRestoreVersionError] = useState('');
  const [loading, setLoading] = useState(false);
  const [restoreAll, setRestoreAll] = useState(true);
  const [selectedPipelineIds, setSelectedPipelineIds] = useState<string[]>([]);

  const [selectedVersionToRestore, setSelectedVersionToRestore] = useState(defaultVersion);

  useEffect(() => {
    setSelectedVersionToRestore(defaultVersion);
  }, [defaultVersion]);

  const [restoreVersion] = useRestorePipelineVersionMutation();
  const { data } = useGetPipelinesForVersionQuery(
    {
      syncariEntityId: entityId,
      versionId: selectedVersionToRestore as string,
    },
    { skip: !selectedVersionToRestore }
  );

  const [entityPipelines, fieldPipelines] = partition(data, (pipeline) => pipeline.pipelineType === 'ENTITY');
  const entityPipelineId = entityPipelines[0]?.targetId;

  const handleClose = useCallback(() => {
    setRestoreAll(true);
    setRestoreVersionError('');
    setSelectedPipelineIds([]);
    dispatch(showRestoreVersionModal({ visible: false }));
  }, [dispatch]);

  const handleRestoreVersion = () => {
    setLoading(true);

    const [entityIds, fieldIds] = partition(selectedPipelineIds, (id) => id === entityPipelineId);

    restoreVersion({
      syncariEntityId: entityId,
      versionId: selectedVersionToRestore as string,
      restoreAll,
      fieldIds,
      restoreEntity: entityIds.length > 0,
    })
      .unwrap()
      .then(() => {
        message.success(tn('version_restored_successfully'));
        handleClose();
      })
      .catch((resp) => {
        setRestoreVersionError(resp.data.message || resp.data.error);
      })
      .finally(() => {
        setLoading(false);
      });
  };

  const restoreDisabled = !restoreAll && !selectedPipelineIds.length;
  const footer = (
    <HStack justify="end" spacing="xs">
      {restoreVersionError && <Alert type="warning" message={restoreVersionError} />}
      <Button onClick={handleClose}>{tc('cancel')}</Button>
      <Tooltip title={restoreDisabled ? tn('select_pipeline_to_restore') : ''}>
        <Button onClick={handleRestoreVersion} type="primary" loading={loading} disabled={restoreDisabled}>
          {tn('restore')}
        </Button>
      </Tooltip>
    </HStack>
  );

  const optionData = useMemo(() => {
    const pipelineGroupsMap = {
      ENTITY: tn('entity_pipelines', { count: entityPipelines?.length }),
      ATTRIBUTE: tn('field_pipelines', { count: fieldPipelines?.length }),
    };

    return sortBy(data, (item) => item.pipelineType === 'ATTRIBUTE')?.map((item) => {
      return {
        label: item.displayName,
        value: item.targetId,
        picklistGroup: pipelineGroupsMap[item.pipelineType],
      };
    });
  }, [data, entityPipelines?.length, fieldPipelines?.length, tn]);

  return (
    <Modal
      footer={footer}
      title={tn(versionTwoId ? 'title_select_version' : 'title', { versionName: name })}
      onCancel={handleClose}
      centered
      width={620}
      visible={visible}>
      <Stack spacing="md">
        {Boolean(versionTwoId) && <TranslatedText text="which_version" />}
        {Boolean(versionTwoId) && (
          <Radio.Group
            onChange={(evt) => {
              setSelectedPipelineIds([]);
              setSelectedVersionToRestore(evt.target.value);
            }}
            value={selectedVersionToRestore}>
            <Stack spacing="xs">
              <Radio value={versionId}>{tn('version', { versionNumber: versionOneNumber })}</Radio>
              <Radio value={versionTwoId}>{tn('version', { versionNumber: versionTwoNumber })}</Radio>
            </Stack>
          </Radio.Group>
        )}

        <TranslatedText text="which_pipelines" />
        <Radio.Group onChange={(evt) => setRestoreAll(evt.target.value)} value={restoreAll}>
          <Stack spacing="xs">
            <Radio value>{tn('all_pipelines', { count: fieldPipelines?.length || 0 })}</Radio>
            <Radio value={false}>{tn('specific_pipelines')}</Radio>
          </Stack>
        </Radio.Group>

        <Select
          className={cx('pipeline-dropdown', !restoreAll && 'show')}
          placeholder={tn('select_pipelines')}
          value={selectedPipelineIds}
          mode="multiple"
          optionData={optionData}
          onChange={(selectedIds) => {
            setSelectedPipelineIds(selectedIds);
          }}
        />
      </Stack>
    </Modal>
  );
};

export default withI18n(RestoreVersionModal, 'RestoreVersionModal');
