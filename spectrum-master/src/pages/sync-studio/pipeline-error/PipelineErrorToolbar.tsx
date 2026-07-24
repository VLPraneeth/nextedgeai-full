import { Tooltip } from 'antd';
import { isUndefined } from 'lodash/fp';
import { useEffect } from 'react';

import { ReactComponent as ExclamationIcon } from 'assets/icons/exclamation.svg';
import Button from 'components/Button';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { HStack } from 'components/layout';
import { TextTag } from 'components/text-tag';
import { Text } from 'components/typography';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import usePreviousValue from 'hooks/usePreviousValue';
import { showPipelineErrorResultsPanel } from 'store/pipeline-error/slice';

import { usePipelineError } from './PipelineError.hooks';

import './PipelineErrorToolbar.scss';

const DISPLAY_NOTICE_THRESHOLD_LENGTH = 1000;

export const PipelineErrorToolbar = withI18n(() => {
  const { hasError, warningCount, errorCount, hasWarningError, refetch, isFetching } = usePipelineError({});
  const dispatch = useEnhancedDispatch();
  const pipelineTransitioning = useEnhancedSelector((state) => state.entityPipeline.pipelineTransitioning);
  const prevPipelineTransition = usePreviousValue(pipelineTransitioning);

  const requestingResyncStatus = useEnhancedSelector((state) => state.entityPipeline.requestingResyncStatus);
  const prevRequestingResyncStatus = usePreviousValue(requestingResyncStatus);
  const entityPipelineApproving = useEnhancedSelector((state) => state.entityPipeline.entityPipelineApproving);
  const entityPipelineApprovingErrorMsg = useEnhancedSelector(
    (state) => state.entityPipeline.entityPipelineApprovingErrorMsg
  );
  const prevEntityPipelineApproving = usePreviousValue(requestingResyncStatus);

  const { tn, t } = useI18nContext();

  useEffect(() => {
    if (isFetching) {
      return;
    }
    // Refetch the sync errors when
    if (
      // pipeline resumes
      isPipelineResuming(pipelineTransitioning, prevPipelineTransition) ||
      // pipeline is resyncing
      isPipelineResycing(requestingResyncStatus, prevRequestingResyncStatus) ||
      // Pipeline published
      isPipelinePublished(entityPipelineApproving, prevEntityPipelineApproving, entityPipelineApprovingErrorMsg)
    ) {
      refetch();
    }
  }, [
    entityPipelineApproving,
    entityPipelineApprovingErrorMsg,
    isFetching,
    pipelineTransitioning,
    prevEntityPipelineApproving,
    prevPipelineTransition,
    prevRequestingResyncStatus,
    refetch,
    requestingResyncStatus,
  ]);

  useEffect(() => {
    hasError && dispatch(showPipelineErrorResultsPanel(true));
    return () => {
      dispatch(showPipelineErrorResultsPanel(false));
    };
  }, [dispatch, hasError]);

  const visible = useEnhancedSelector((state) => state.pipelineError.resultsPanelVisible);

  return !hasWarningError ? null : (
    <HStack className="error-toolbar" justify="space-between">
      <HStack className="error-toolbar--status" spacing="xxsm">
        <Text weight="bold" color="gray-900">
          {tn('pipeline_status')}
        </Text>
        {errorCount && (
          <TextTag size="sm" text={t('PipelineErrorState.count_error', { count: errorCount })} color="red" />
        )}
        {warningCount && (
          <TextTag size="sm" text={t('PipelineErrorState.count_warning', { count: warningCount })} color="orange" />
        )}
        {errorCount + warningCount >= DISPLAY_NOTICE_THRESHOLD_LENGTH ? (
          <Tooltip title={tn('too_many_warnings', { count: DISPLAY_NOTICE_THRESHOLD_LENGTH })}>
            <ExclamationIcon width={16} height={16} />
          </Tooltip>
        ) : null}
      </HStack>
      <HStack>
        <Button onClick={() => dispatch(showPipelineErrorResultsPanel(!visible))}>
          {tn(visible ? 'hide_status_panel' : 'show_status_panel')}
        </Button>
      </HStack>
    </HStack>
  );
}, 'PipelineErrorToolbar');

const isPipelineResuming = (
  pipelineTransition: Record<string, string>,
  prevPipelineTransition?: Record<string, string | undefined>
) => {
  return (
    pipelineTransition.type === 'resume' &&
    pipelineTransition.status === 'success' &&
    prevPipelineTransition?.type === 'resume' &&
    prevPipelineTransition?.status === 'loading'
  );
};

const isPipelineResycing = (requestingResyncStatus: string, prevRequestingResyncStatus?: string) =>
  requestingResyncStatus === 'success' && prevRequestingResyncStatus === 'loading';

const isPipelinePublished = (
  entityPipelineApproving?: boolean,
  prevEntityPipelineApproving?: boolean,
  entityPipelineApprovingErrorMsg?: string
) =>
  isUndefined(prevEntityPipelineApproving) &&
  entityPipelineApproving === false &&
  entityPipelineApprovingErrorMsg === '';
