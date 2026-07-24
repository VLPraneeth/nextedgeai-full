import { navigate } from '@reach/router';
import MenuItem from 'antd/lib/menu/MenuItem';
import { useCallback } from 'react';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import KebabMenu from 'components/KebabMenu';
import { TextTag } from 'components/text-tag';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { useUpdateSelectedNodeIdsQueryParam } from 'pages/sync-studio/pipeline/PipelineEditor.hooks';
import { useSyncStudioMatch } from 'pages/sync-studio/SyncStudio.hooks';
import { PipelineError, PipelineSyncError, PipelineSyncWarning } from 'store/pipeline-error/types';
import { setIsGotoBetweenFieldPipelines } from 'store/validation/slice';
import { getCoreNode } from 'store/validation/utils';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import './PipelineErrorResultsItem.scss';

export interface PipelineErrorResultsItemProps {
  entityId?: string;
  entityPipelineId?: string;
  result: PipelineSyncError | PipelineSyncWarning;
  pipelineErrors?: PipelineError;
}

export const PipelineErrorResultsItem = withI18n(
  ({ entityId, entityPipelineId, result, pipelineErrors }: PipelineErrorResultsItemProps) => {
    const { tn, tc } = useI18nContext();
    const dispatch = useEnhancedDispatch();
    const { entityPipeline } = useEnhancedSelector((state) => state.entityPipeline);
    const entityPipelineDraft = entityPipeline?.draft ?? null;

    const updateSelectedNodeIdsQueryParam = useUpdateSelectedNodeIdsQueryParam();

    // For some errors, the backend returns a nodeId equal to the entityPipelineId
    // and a targetId equal to the entityId. The fuction below determines if this
    // is the case.
    const isCoreError = () => {
      if (
        entityId &&
        entityPipelineId &&
        result.nodeId &&
        result.targetId &&
        result.targetId === entityId &&
        result.nodeId === entityPipelineId
      ) {
        return true;
      }
      return false;
    };

    const match = useSyncStudioMatch();

    const isGotoEnabled = !!result.nodeId && !!result.targetId;

    const getBaseUrl = (level: string) =>
      level === 'ATTRIBUTE' ? RouteConstants.FIELD_PIPELINE_ERROR : RouteConstants.ENTITY_PIPELINE_ERROR;

    const handleGoto = () => {
      if (match) {
        const { entityId, graphVersion, fieldId } = match;
        const coreNode = getCoreNode(entityPipelineDraft ? entityPipelineDraft.nodes : entityPipeline?.nodes);
        const nodeId = isCoreError() && coreNode ? coreNode.id : result.nodeId;

        if (fieldId && result.targetId !== fieldId) {
          dispatch(setIsGotoBetweenFieldPipelines(true));
        }

        const url = makeUrl(getBaseUrl(result.level), {
          entityId,
          graphVersion,
          fieldId: result.targetId,
        });

        setTimeout(() => {
          updateSelectedNodeIdsQueryParam([nodeId], url, nodeId);
        });
      }
    };

    const handleNavigateSyncErrors = useCallback(() => {
      if ('errorType' in result && result.errorType === 'SYNC') {
        navigate(
          makeUrl(
            RouteConstants.LOGS_SYNC_ERRORS,
            {},
            {
              message: result.errorMessage,
              syncCycleId: pipelineErrors?.syncCycleId,
              nodeId: result.nodeId,
            },
            { encodeToPlus: false }
          )
        );
      } else {
        const url = makeUrl(
          RouteConstants.LOGS_TRANSACTIONS,
          {},
          {
            message: result.errorMessage,
            syncCycleId: pipelineErrors?.syncCycleId,
            nodeId: result.nodeId,
          },
          { encodeToPlus: false }
        );
        navigate(url);
      }
    }, [pipelineErrors?.syncCycleId, result]);

    const menuItems = [
      result.nodeId ? (
        <MenuItem key="goto" onClick={handleGoto} disabled={!isGotoEnabled}>
          {tn('goto')}
        </MenuItem>
      ) : undefined,
      'errorType' in result && result.nodeId ? (
        <MenuItem key="dismiss" onClick={handleNavigateSyncErrors}>
          {tn('view_warnings')}
        </MenuItem>
      ) : undefined,
    ].filter(Boolean);

    return (
      <div className="pipeline-error-result-item">
        <div className="pipeline-error-result-item--header">
          <h1 className="pipeline-error-result-item--title">{result.errorMessage || tc('unknown_error_message')}</h1>
          {menuItems.length ? <KebabMenu menuItems={menuItems} /> : null}
        </div>
        <div>
          <TextTag
            text={'errorType' in result ? tc('warning') : tc('error')}
            color={'errorType' in result ? 'orange' : 'red'}
            size="md"
          />
        </div>
      </div>
    );
  },
  'PipelineErrorResultsItem'
);
