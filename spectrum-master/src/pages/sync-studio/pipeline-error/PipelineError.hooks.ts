import { useMatch } from '@reach/router';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import useQueryParams from 'hooks/useQueryParams';
import { SystemFilterQuery } from 'store/logs/types';
import { useGetPipelineErrorQuery } from 'store/pipeline-error/api';
import { showPipelineErrorResultsPanel } from 'store/pipeline-error/slice';
import { PipelineSyncError, PipelineSyncWarning } from 'store/pipeline-error/types';
import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('PipelineErrorState');

interface PipelineErrorParams {
  updateNodes?: () => void;
  nodeId?: string;
}

export const usePipelineError = ({ updateNodes, nodeId }: PipelineErrorParams) => {
  const dispatch = useEnhancedDispatch();

  const detailsEntityId = useMatch('/sync-studio/details/:entityId/*')?.entityId;

  const entityId = useMatch('/sync-studio/entity/:entityId/*')?.entityId || detailsEntityId;
  const fieldId = useMatch('/sync-studio/entity/:entityId/field/:fieldId/*')?.fieldId;

  const { data: pipelineErrors, refetch, isFetching } = useGetPipelineErrorQuery(
    { entityId: entityId || '' },
    { skip: !entityId }
  );

  const displayedGraph = useEnhancedSelector((state) => state.pipeline.displayedGraph);
  const resultsPanelVisible = useEnhancedSelector((state) => state.pipelineError.resultsPanelVisible);

  const allErrors: (PipelineSyncError | PipelineSyncWarning)[] = useMemo(() => {
    const error = pipelineErrors?.error ? [pipelineErrors.error] : [];
    return [...error, ...(pipelineErrors?.warnings || [])].filter(Boolean);
  }, [pipelineErrors?.error, pipelineErrors?.warnings]);

  const contextFilter = useCallback(
    (error) => {
      if (!error) {
        return false;
      }

      // Show only the error for the selected node
      if (nodeId) {
        if (error.nodeId === nodeId) {
          return true;
        }
        return false;
      }

      // Show only field warnings and errors if we're looking a field
      if (fieldId) {
        return error.targetId === fieldId;
      }

      // Show everything if no node is selected
      if (!nodeId) {
        return true;
      }

      return false;
    },
    [fieldId, nodeId]
  );

  const syncErrors = useMemo(() => {
    return allErrors?.filter(contextFilter);
  }, [allErrors, contextFilter]);

  const errors = pipelineErrors?.error ? [pipelineErrors.error] : [];

  const warnings = (pipelineErrors?.warnings || []).filter(contextFilter);

  useEffect(() => {
    if (resultsPanelVisible) {
      updateNodes?.();
    }
  }, [resultsPanelVisible, updateNodes]);

  const [filterText, setFilterText] = useState('');

  const filteredErrors = useMemo(() => {
    if (filterText.length <= 0) {
      return syncErrors;
    }
    return syncErrors.filter((error) => {
      if (error.errorMessage?.toLowerCase()?.indexOf(filterText.toLowerCase()) !== -1) {
        return true;
      }
      if (error.errorDetail?.toLowerCase()?.indexOf(filterText.toLowerCase()) !== -1) {
        return true;
      }
      return false;
    });
  }, [filterText, syncErrors]);

  return {
    pipelineErrors,
    refetch,
    isFetching,
    hasError: Boolean(allErrors.length),
    hasWarningError: Boolean(errors.length + warnings.length),
    errorCount: errors.length,
    warningCount: warnings.length,
    errors,
    warnings,
    syncErrors,
    filteredErrors,
    setFilterText,
    isPipelineErrorVisible: displayedGraph === AppConstants.GRAPH_STATUS.APPROVED,
    entityId,
    fieldId,
    resultsPanelVisible,
    showPipelineErrorResultsPanel: (visible: boolean) => dispatch(showPipelineErrorResultsPanel(visible)),
    updateNodes,
  };
};

export const usePipelineErrorSystemFilter = () => {
  const [{ message, nodeId, syncCycleId }] = useQueryParams<SystemFilterQuery>();
  const [showSystemFilter, setShowSystemFilter] = useState(Boolean(message));

  const params = useMemo(() => {
    return showSystemFilter
      ? {
          message,
          nodeId,
          syncCycleId,
        }
      : {};
  }, [message, nodeId, showSystemFilter, syncCycleId]);

  return { errorMessage: message, showSystemFilter, setShowSystemFilter, queryParams: params };
};

export const getErrorText = ({ errorCount, warningCount }: { errorCount?: number; warningCount?: number }) => {
  return [
    errorCount && tn('count_error', { count: errorCount }),
    warningCount && tn('count_warning', { count: warningCount }),
  ]
    .filter(Boolean)
    .join(', ');
};
