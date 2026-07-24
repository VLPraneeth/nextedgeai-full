import { find, isEmpty, reduce } from 'lodash';
import { useCallback, useEffect, useState } from 'react';
import { useSelector } from 'react-redux';

import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import usePreviousValue from 'hooks/usePreviousValue';
import { useSelectedNodes, useUpdateSelectedNodeIdsQueryParam } from 'pages/sync-studio/pipeline/PipelineEditor.hooks';
import { RootState } from 'reducers/index';
import { selectConnectorsForCurrentEntityPipeline } from 'selectors/entityPipelineSelectors';
import {
  getFieldTestRun,
  getFieldTestRuns,
  getLiveTestRun,
  getLiveTestRuns,
  resetTestResult,
  selectTestRunTestId,
} from 'store/test/actions';
import {
  selectEntityDraftId,
  selectFieldPipelineTestRunTest,
  selectFieldPipelineTestRuns,
  selectGetFieldTestRunStatus,
  selectGetFieldTestRunsStatus,
  selectGetLiveTestRunStatus,
  selectGetLiveTestRunsStatus,
  selectLiveTestRunRecordId,
  selectLiveTestRunRecords,
  selectLiveTestRuns,
  selectTestPanelView,
  selectTestResultVisible,
  selectTestRun,
} from 'store/test/selectors';
import {
  PipelineContextTypes,
  RunLiveTestPayloadWebhookPayload,
  TestPanelView,
  TestResultDetail,
  TestRunModel,
  TestRunsModel,
} from 'store/test/types';
import { FetchStatus } from 'store/types';

import { OVERVIEW_ID, TEST_STATUS } from '../../Test.util';

interface TestResultStatus {
  isLive: boolean;
  visible: boolean;
  testRun?: TestRunModel | null;
  testRunIsProcessing: boolean;
  testRuns: TestRunsModel[];
  testRunStatus: FetchStatus;
  testRunsStatus: FetchStatus;
  liveTestRunRecords?: TestResultDetail[];
  liveTestRunRecordId?: string;
}

/**
 * Abstracts the logic getting the live vs. simulated test results
 */
export const useTestResultContext = (): TestResultStatus => {
  const testPanelView = useEnhancedSelector(selectTestPanelView);

  const visible = useEnhancedSelector(selectTestResultVisible);
  const isLive = testPanelView === TestPanelView.LIVE_RESULTS;

  const testRun = useEnhancedSelector(selectTestRun);
  const simulatedTestRuns = useEnhancedSelector(selectFieldPipelineTestRuns);
  const liveTestRuns = useEnhancedSelector(selectLiveTestRuns);

  const getFieldTestRunStatus = useEnhancedSelector(selectGetFieldTestRunStatus);
  const getFieldTestRunsStatus = useEnhancedSelector(selectGetFieldTestRunsStatus);

  const getLiveTestRunStatus = useEnhancedSelector(selectGetLiveTestRunStatus);
  const getLiveTestRunsStatus = useEnhancedSelector(selectGetLiveTestRunsStatus);

  const liveTestRunRecords = useEnhancedSelector(selectLiveTestRunRecords);
  const liveTestRunRecordId = useEnhancedSelector(selectLiveTestRunRecordId);

  const testRunIsProcessing =
    !!testRun && (testRun.status === TEST_STATUS.NEW || testRun.status === TEST_STATUS.PROCESSING);

  return {
    isLive,
    visible,
    testRun,
    testRunIsProcessing,
    testRuns: isLive ? liveTestRuns : simulatedTestRuns,
    testRunStatus: isLive ? getLiveTestRunStatus : getFieldTestRunStatus,
    testRunsStatus: isLive ? getLiveTestRunsStatus : getFieldTestRunsStatus,
    liveTestRunRecords,
    liveTestRunRecordId,
  };
};

/**
 * Fetches simulated and live test results
 */
export const useFetchTestResults = (pipelineId: string, pipelineContext: PipelineContextTypes) => {
  const { isLive, visible } = useTestResultContext();

  const graphId = useEnhancedSelector(selectEntityDraftId);

  const dispatch = useEnhancedDispatch();

  const getTestRun = useCallback(
    (runId: string) => {
      if (isLive) {
        dispatch(getLiveTestRun({ graphId, runId }));
      } else {
        dispatch(getFieldTestRun({ pipelineContext, fieldPipelineId: pipelineId, runId }));
      }
    },
    [dispatch, graphId, isLive, pipelineContext, pipelineId]
  );

  useEffect(() => {
    if (visible) {
      getTestRun('latest');

      if (isLive) {
        dispatch(getLiveTestRuns({ graphId }));
      } else {
        dispatch(getFieldTestRuns({ pipelineContext, fieldPipelineId: pipelineId }));
      }
    } else {
      dispatch(resetTestResult());
    }
  }, [dispatch, getTestRun, graphId, isLive, pipelineContext, pipelineId, visible]);

  return {
    getTestRun,
  };
};

export const useSetSelectedTest = () => {
  const dispatch = useEnhancedDispatch();
  const { selectedNodeIds } = useSelectedNodes();
  const selectedNodeId = selectedNodeIds.length === 1 ? selectedNodeIds[0] : undefined;
  const updateSelectedNodeIdsQueryParam = useUpdateSelectedNodeIdsQueryParam();
  const selectedTestRun = useSelector(selectFieldPipelineTestRunTest);
  const { testRun } = useTestResultContext();

  const [didInitialRender, setDidInitialRender] = useState(false);

  // By default select the first test run and first node IFF there was no node
  // selected previously. The logic in this useEffect should only run once when
  // the test result panel is opened. Otherwise, the navigation should be
  // controlled by the user.
  useEffect(() => {
    const defaultNodeId = testRun?.resultDetails?.[0]?.nodes?.[0]?.nodeId;

    if (defaultNodeId && !selectedNodeId && !didInitialRender) {
      if (defaultNodeId !== OVERVIEW_ID) {
        updateSelectedNodeIdsQueryParam([defaultNodeId]);
      }

      dispatch(selectTestRunTestId(testRun.resultDetails?.[0]?.id));
    }

    setDidInitialRender(true);
  }, [
    dispatch,
    selectedNodeId,
    didInitialRender,
    selectedNodeIds.length,
    testRun?.resultDetails,
    updateSelectedNodeIdsQueryParam,
  ]);

  useEffect(() => {
    // Alway fetch the first test if nothing is selected
    if (!selectedTestRun && testRun?.resultDetails?.[0]) {
      dispatch(selectTestRunTestId(testRun.resultDetails?.[0]?.id));
    }
  }, [dispatch, testRun, selectedTestRun]);
};

interface IdList {
  label: string;
  id: string;
}

interface RecordIdTestCriteria {
  type: 'recordIds';
  idList: IdList[];
}

interface DateRangeTestCriteria {
  type: 'dateRange';
  startDate: string;
  endDate: string;
}

interface WebhookTestCriteria {
  type: 'webhook';
  webhook: RunLiveTestPayloadWebhookPayload['webhook'];
}

export const useTestRunCriteria = (
  testRun: TestRunModel
): RecordIdTestCriteria | DateRangeTestCriteria | WebhookTestCriteria => {
  const pipelineConnectors = useEnhancedSelector(selectConnectorsForCurrentEntityPipeline);
  if (!isEmpty(testRun.recordIds)) {
    const idList = reduce<Record<string, string[]>, IdList[]>(
      testRun.recordIds,
      (fullIds, list, sourceEntityId) => {
        const pipelineConnector = find(pipelineConnectors, { sourceEntityId });

        list.forEach((id) => {
          fullIds.push({ label: pipelineConnector?.label || 'Missing', id });
        });

        return fullIds;
      },
      []
    );

    return {
      type: 'recordIds',
      idList,
    };
  }

  if (testRun.webhook && !isEmpty(testRun.webhook)) {
    return {
      type: 'webhook',
      webhook: testRun.webhook,
    };
  }

  return {
    type: 'dateRange',
    startDate: testRun.startTime,
    endDate: testRun.endTime,
  };
};

export const useFetchLatestTestWhenCompleted = () => {
  const dispatch = useEnhancedDispatch();

  const graphId = useEnhancedSelector(selectEntityDraftId);
  const liveTestCompletedTimestamp = useEnhancedSelector(
    (state: RootState) => state.entityPipeline.liveTestCompletedTimestamp
  );

  const previousTimestamp = usePreviousValue(liveTestCompletedTimestamp);

  useEffect(() => {
    if (graphId && previousTimestamp !== liveTestCompletedTimestamp) {
      dispatch(getLiveTestRun({ graphId, runId: 'latest' }));
    }
  }, [dispatch, graphId, liveTestCompletedTimestamp, previousTimestamp]);
};
