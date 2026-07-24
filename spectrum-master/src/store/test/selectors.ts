//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import produce from 'immer';
import { cloneDeep, find } from 'lodash';
import { createSelector } from 'reselect';

import { OVERVIEW_ID } from 'pages/sync-studio/test/Test.util';
import { RootState } from 'reducers/index';
import AppConstants from 'utils/AppConstants';
import { t } from 'utils/i18nUtil';

import { TestDataModel, TestPanelView } from './types';

export const selectFieldPipelineSelectedTestRunTestId = (state: RootState) => state.test.selectedTestRunTestId;
export const selectFieldPipelineTestRuns = (state: RootState) => state.test.fieldTestRuns;
export const selectLiveTestRuns = (state: RootState) => state.test.liveTestRuns;
export const selectTests = (state: RootState) => state.test.fieldPipelineTests;
export const selectPicklistValues = (state: RootState) => state.test.fieldPipelinePicklistValues;
export const selectTestVisible = (state: RootState) => !!state.test.testPanelView;
export const selectTestPanelView = (state: RootState) => state.test.testPanelView;
export const selectCreateTestVisible = (state: RootState) => state.test.createTestVisible;
export const selectSaveTestErrorMessage = (state: RootState) => state.test.saveTestErrorMessage;
export const selectSaveTestStatus = (state: RootState) => state.test.saveFieldPipelineTestStatus;
export const selectTestResultVisible = createSelector([selectTestPanelView], (testPanelView) =>
  [TestPanelView.LIVE_RESULTS, TestPanelView.SIMULATED_RESULTS].includes(testPanelView)
);
export const selectRunFieldTestsStatus = (state: RootState) => state.test.runFieldTestsStatus;
export const selectRunFieldTestsErrorMessage = (state: RootState) => state.test.runFieldTestsErrorMessage;
export const selectUpdatedTestRunId = (state: RootState) => state.test.updatedTestRunId;
export const selectedGetFieldPipelineTestsStatus = (state: RootState) => state.test.getFieldPipelineTestsStatus;
export const selectGetFieldTestRunStatus = (state: RootState) => state.test.getFieldTestRunStatus;
export const selectGetFieldTestRunsStatus = (state: RootState) => state.test.getFieldTestRunsStatus;
export const selectGetLiveTestRunStatus = (state: RootState) => state.test.getLiveTestRunStatus;
export const selectGetLiveTestRunsStatus = (state: RootState) => state.test.getLiveTestRunsStatus;
export const selectEditTestId = (state: RootState) => state.test.editTestId;
export const selectTest = (state: RootState) => state.test.pipelineTest;
export const selectFieldPipelineTestRun = (state: RootState) => state.test.fieldPipelineTestRun;
export const selectLiveTestRun = (state: RootState) => state.test.liveTestRun;
export const selectTestRunVisible = (state: RootState) => state.test.testRunVisible;
export const selectTestRunTestIds = (state: RootState) => state.test.testRunTestIds;

export const selectEntityDraftId = (state: RootState) => {
  const { entityPipeline } = state.entityPipeline;
  if (entityPipeline.draftStatus === AppConstants.GRAPH_STATUS.NEW) {
    return entityPipeline.id;
  }
  return entityPipeline.draft?.id;
};

const mergeResult = (testData: TestDataModel) => {
  const newTestData = cloneDeep(testData);
  newTestData.actualResult?.forEach((value) => {
    if (value.failed) {
      value.expectedValue = testData?.expectedResult?.find((v) => v.apiName === value.apiName)?.value;
    }
  });
  return newTestData;
};

export const selectLiveTestRunRecords = createSelector(
  [selectLiveTestRun],
  (liveTestRun) => liveTestRun?.resultDetails
);
export const selectLiveTestRunRecordId = (state: RootState) => state.test.liveTestRunRecordId;

export const selectTestRun = createSelector(
  [selectTestPanelView, selectLiveTestRun, selectFieldPipelineTestRun, selectLiveTestRunRecordId],
  (view, liveTestRun, fieldPipelineTestRun, liveTestRunRecordId) => {
    switch (view) {
      case TestPanelView.LIVE_RESULTS:
        return produce(liveTestRun, (draft) => {
          if (draft?.resultDetails?.length) {
            // Only returns a single test result since all other results are
            // just additional records processed on the same test
            draft.resultDetails = [find(draft.resultDetails, { id: liveTestRunRecordId }) || draft.resultDetails[0]];
          }
          return draft;
        });

      case TestPanelView.SIMULATED_RESULTS:
        return produce(fieldPipelineTestRun, (draft) => {
          if (draft?.resultDetails) {
            draft.resultDetails.forEach((testRunResult) => {
              if (testRunResult.nodes?.length > 1) {
                // Add expectedResult to all simulated tests
                const nodes = testRunResult.nodes.map((node) => {
                  node.testData = mergeResult({
                    ...node.testData,
                    expectedResult: testRunResult.testData.expectedResult,
                  });
                  return node;
                });
                testRunResult.nodes = [
                  {
                    nodeId: OVERVIEW_ID,
                    displayName: t('TestResultContent.overview'),
                    testData: mergeResult(testRunResult.testData),
                    status: testRunResult.status,
                  },
                  ...nodes,
                ];
              }
            });
          }
          return draft;
        });

      case null:
        return null;
    }
  }
);

export const selectFieldPipelineTestRunTest = createSelector(
  [selectTestRun, selectFieldPipelineSelectedTestRunTestId],
  (testRun, selectedTestRunTestId) => {
    return testRun?.resultDetails?.find((test) => {
      return test.id === selectedTestRunTestId;
    });
  }
);

export const selectFieldPiepelineTestNode = createSelector(
  [
    selectTestRun,
    (state: RootState, selectedNodeId: string | undefined) => selectedNodeId,
    selectFieldPipelineTestRunTest,
  ],
  (testRun, selectedNodeId, testRunTest) => {
    if (!selectedNodeId) {
      const test = testRun?.resultDetails?.find((test) => test.id === testRunTest?.id);
      // Overview node is always be the first node element
      if (test?.nodes?.[0]?.nodeId) {
        return { ...test.nodes[0], testData: mergeResult(test.testData), status: test.status };
      }
    } else {
      return testRun?.resultDetails
        ?.find((test) => test.id === testRunTest?.id)
        ?.nodes?.find((node) => node.nodeId === selectedNodeId);
    }
  }
);
