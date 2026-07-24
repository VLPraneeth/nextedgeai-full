import { useMatch } from '@reach/router';
import { useCallback, useEffect } from 'react';

import { getEntityPipeline } from 'actions/entityPipelineActions';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import AppConstants from 'utils/AppConstants';
import { getPipelineDraftStatus } from 'utils/PipelineUtil';
import { ValuesOf } from 'utils/TypeUtils';
const { GRAPH_STATUS } = AppConstants;

export const PipelineSettings = {
  continuousPipeline: 'continuousPipeline',
  nodeLoggingEnabled: 'nodeLoggingEnabled',
  simpleLoops: 'simpleLoops',
};

export type PipelineSettingsName = ValuesOf<typeof PipelineSettings>;

export const usePipelineSettings = () => {
  const epMatch = useMatch(`/sync-studio/entity/:syncariEntityId/pipeline/:version/*`);
  const epMatchLogs = useMatch(`/sync-studio/entity/:syncariEntityId/pipeline-logs/:version/*`);
  const match = epMatch?.syncariEntityId ? epMatch : epMatchLogs;
  const syncariEntityId = match?.syncariEntityId || '';
  const version = getPipelineDraftStatus(match?.version?.toUpperCase() || GRAPH_STATUS.NEW);
  const isDraft = version === GRAPH_STATUS.NEW;
  const dispatch = useEnhancedDispatch();

  const pipeline = useEnhancedSelector((state) => state.entityPipeline.entityPipeline);
  const entityPipelineError = useEnhancedSelector((state) => state.entityPipeline.entityPipelineError);
  const entityPipelineFetching = useEnhancedSelector((state) => state.entityPipeline.entityPipelineFetching);

  const settings = isDraft ? pipeline?.draft?.settings || pipeline?.settings : pipeline?.settings;

  useEffect(() => {
    if (!Object.keys(pipeline || {}).length && syncariEntityId && !entityPipelineError && !entityPipelineFetching) {
      dispatch(getEntityPipeline(syncariEntityId, version));
    }
  }, [dispatch, entityPipelineError, entityPipelineFetching, pipeline, syncariEntityId, version]);

  const isSettingsEnabled = useCallback((name: PipelineSettingsName) => Boolean(settings?.[name]), [settings]);

  return { syncariEntityId, version, settings, isSettingsEnabled, isDraft, pipeline };
};
