import { Button } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { getEntityPipeline } from 'actions/entityPipelineActions';
import DrawerPanel from 'components/DrawerPanel';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import SkullPanel from 'components/skull-panel/SkullPanel';
import TabPanelSpin from 'components/TabPanelSpin';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { useGetPipelineSettingsMetaQuery, usePatchPipelineSettingsMutation } from 'store/pipeline/api';
import { showSettingsPanel } from 'store/pipeline/slice';
import { ENTITY_DRAWER_HEIGHT_OFFSET } from 'styles/style.constants';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tc } from 'utils/i18nUtil';
import { usePipelineSettings } from './Settings.hooks';

export const Settings = () => {
  const { syncariEntityId, settings, isDraft, pipeline, version } = usePipelineSettings();

  const visible = useEnhancedSelector((state) => state.pipeline.settingsPanel?.visible);
  const dispatch = useEnhancedDispatch();
  const { data, isLoading: spinning } = useGetPipelineSettingsMetaQuery(
    { syncariEntityId },
    { skip: !visible || !syncariEntityId }
  );

  const [
    saveSettings,
    { isError: isSaveError, error: saveError, isLoading: isSaving },
  ] = usePatchPipelineSettingsMutation();
  const [fieldValues, setFieldValues] = useState<Record<string, string | boolean>>({});
  const errorMessage = getRtkQueryErrorMessage(saveError);

  const close = useCallback(() => {
    dispatch(showSettingsPanel({ visible: false }));
  }, [dispatch]);

  useEffect(() => {
    if (settings) {
      setFieldValues(settings);
    }
    return () => {
      close();
    };
  }, [close, settings]);

  const save = useCallback(() => {
    const graph = isDraft ? pipeline?.draft || pipeline : pipeline;

    saveSettings({
      entityId: syncariEntityId,
      payload: {
        ...graph,
        settings: fieldValues,
      },
    })
      .unwrap()
      .then(() => {
        dispatch(getEntityPipeline(syncariEntityId, version));
        close();
      });
  }, [close, dispatch, fieldValues, isDraft, pipeline, saveSettings, syncariEntityId, version]);

  const configurations = useMemo(() => {
    // Filter out settings that are not configurable in the Settings panel
    const validConfigurations = data?.configurations?.filter(
      (config) => !['realtimePipeline', 'realtimeEndpoint'].includes(config.name)
    );
    if (!isDraft) {
      return validConfigurations?.map((configuration) => ({
        ...configuration,
        disabled: true,
      }));
    }
    return validConfigurations;
  }, [data?.configurations, isDraft]);

  return (
    <DrawerPanel
      absolutePositioning
      additionalHeightOffset={ENTITY_DRAWER_HEIGHT_OFFSET}
      onClose={close}
      title={tc('settings')}
      footer={
        isDraft ? (
          <>
            <Button onClick={close}>{tc('cancel')}</Button>
            <Button disabled={isSaving} onClick={save} type="primary">
              {tc('save')}
            </Button>
          </>
        ) : (
          <Button onClick={close} type="primary">
            {tc('close')}
          </Button>
        )
      }
      visible={visible}>
      {isSaveError && (
        <InlineMessage type={InlineMessageTypes.ERROR} title={errorMessage}>
          {errorMessage}
        </InlineMessage>
      )}
      <TabPanelSpin spinning={spinning} tip={tc('loading')}>
        <SkullPanel configurations={configurations || []} value={fieldValues} onChange={setFieldValues} />
      </TabPanelSpin>
    </DrawerPanel>
  );
};
