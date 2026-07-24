//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { createSlice } from '@reduxjs/toolkit';
import { find } from 'lodash';

import AppConstants from 'utils/AppConstants';

import {
  deleteFieldTest,
  getFieldPicklistValues,
  getFieldPipelineTest,
  getFieldPipelineTests,
  getFieldTestRun,
  getFieldTestRuns,
  resetCreateTest,
  resetSimulatedTestRun,
  resetTestResult,
  runFieldTests,
  saveFieldPipelineTest,
  selectTestRunTestId,
  setLiveTestRunRecordId,
  setTestPanelView,
  showCreateTest,
  showRunTest,
} from './actions';
import { getLiveTestRun, getLiveTestRuns } from './thunks';
import { TestPanelView, TestState } from './types';

const { FETCH_STATUS } = AppConstants;

const SERVER_EVENTS = {
  SIMULATE_PIPELINE_COMPLETED: 'SIMULATE_PIPELINE_COMPLETED',
};

export const initialTestState: TestState = {
  getFieldPipelineTestsStatus: FETCH_STATUS.IDLE,
  fieldPipelineTests: [],
  getFieldPipelinePicklistValuesStatus: FETCH_STATUS.IDLE,
  fieldPipelinePicklistValues: {},
  getFieldPipelineTestRunStatus: FETCH_STATUS.IDLE,
  getLiveTestRunStatus: FETCH_STATUS.IDLE,
  fieldTestRuns: [],
  liveTestRuns: [],
  getLiveTestRunsStatus: FETCH_STATUS.IDLE,
  runFieldTestsStatus: FETCH_STATUS.IDLE,
  deleteFieldTestStatus: FETCH_STATUS.IDLE,
  getFieldPicklistValuesStatus: FETCH_STATUS.IDLE,
  saveFieldPipelineTestStatus: FETCH_STATUS.IDLE,
  testPanelView: TestPanelView.CLOSED,
  createTestVisible: false,
  getFieldTestRunStatus: FETCH_STATUS.IDLE,
  getFieldTestRunsStatus: FETCH_STATUS.IDLE,
  getFieldPipelineTestStatus: FETCH_STATUS.IDLE,
  testRunVisible: false,
  testRunTestIds: [],
};

// TODO: Update the types on ignored TS lines
const testSlice = createSlice({
  name: 'test',
  initialState: initialTestState,
  reducers: {},
  extraReducers: (builder) => {
    // Getting the list of tests
    builder.addCase(getFieldPipelineTests.pending, (state) => {
      state.getFieldPipelineTestsStatus = FETCH_STATUS.LOADING;
    });
    builder.addCase(getFieldPipelineTests.fulfilled, (state, action) => {
      state.getFieldPipelineTestsStatus = FETCH_STATUS.SUCCESS;
      state.fieldPipelineTests = action.payload.data;
    });

    // Get a test
    builder.addCase(getFieldPipelineTest.pending, (state, action) => {
      state.getFieldPipelineTestStatus = FETCH_STATUS.LOADING;
    });
    builder.addCase(getFieldPipelineTest.fulfilled, (state, action) => {
      state.getFieldPipelineTestStatus = FETCH_STATUS.SUCCESS;
      state.pipelineTest = action.payload.data;
    });
    builder.addCase(getFieldPipelineTest.rejected, (state, action) => {
      state.getFieldPipelineTestStatus = FETCH_STATUS.ERROR;
    });

    // Run test modal
    builder.addCase(showRunTest, (state, action) => {
      state.runFieldTestsErrorMessage = '';
      state.testRunVisible = action.payload.visible;
      state.testRunTestIds = action.payload.testIds;
    });

    // Run the field tests
    builder.addCase(runFieldTests.pending, (state, action) => {
      state.runFieldTestsStatus = FETCH_STATUS.LOADING;
      state.runFieldTestsErrorMessage = '';
    });
    builder.addCase(runFieldTests.fulfilled, (state, action) => {
      state.runFieldTestsStatus = FETCH_STATUS.SUCCESS;
    });
    builder.addCase(runFieldTests.rejected, (state, action) => {
      state.runFieldTestsStatus = FETCH_STATUS.ERROR;
      // TODO: type error
      // @ts-ignore
      state.runFieldTestsErrorMessage = action.payload.message;
    });

    // Delete a test
    builder.addCase(deleteFieldTest.pending, (state, action) => {
      state.deleteFieldTestStatus = FETCH_STATUS.LOADING;
    });
    builder.addCase(deleteFieldTest.fulfilled, (state, action) => {
      state.deleteFieldTestStatus = FETCH_STATUS.SUCCESS;
    });
    builder.addCase(deleteFieldTest.rejected, (state, action) => {
      state.deleteFieldTestStatus = FETCH_STATUS.ERROR;
    });

    // Get picklist values
    builder.addCase(getFieldPicklistValues.pending, (state, action) => {
      state.getFieldPicklistValuesStatus = FETCH_STATUS.LOADING;
    });
    builder.addCase(getFieldPicklistValues.fulfilled, (state, action) => {
      state.getFieldPicklistValuesStatus = FETCH_STATUS.SUCCESS;
      state.fieldPipelinePicklistValues[action.meta.arg.nodeId] = action.payload.data;
    });
    builder.addCase(getFieldPicklistValues.rejected, (state, action) => {
      state.getFieldPicklistValuesStatus = FETCH_STATUS.ERROR;
    });

    // Save field pipeline test
    builder.addCase(saveFieldPipelineTest.pending, (state, action) => {
      state.saveFieldPipelineTestStatus = FETCH_STATUS.LOADING;
      state.saveTestErrorMessage = '';
    });
    builder.addCase(saveFieldPipelineTest.fulfilled, (state, action) => {
      state.saveFieldPipelineTestStatus = FETCH_STATUS.SUCCESS;
    });
    builder.addCase(saveFieldPipelineTest.rejected, (state, action) => {
      state.saveFieldPipelineTestStatus = FETCH_STATUS.ERROR;
      // TODO: type error
      // @ts-ignore
      state.saveTestErrorMessage = action.payload.message;
    });

    builder.addCase(selectTestRunTestId, (state, action) => {
      state.selectedTestRunTestId = action.payload.testRunTestId;
    });

    builder.addCase(resetSimulatedTestRun, (state) => {
      state.createTestVisible = false;
      state.getFieldPipelineTestsStatus = FETCH_STATUS.IDLE;
      state.fieldPipelineTests = [];
    });

    builder.addCase(showCreateTest, (state, action) => {
      state.createTestVisible = action.payload.visible;
      if (action.payload.testId) {
        state.editTestId = action.payload.testId;
      }
    });

    builder.addCase(resetCreateTest, (state, action) => {
      state.saveFieldPipelineTestStatus = FETCH_STATUS.IDLE;
      state.saveTestErrorMessage = '';
      state.getFieldPicklistValuesStatus = FETCH_STATUS.IDLE;
      state.fieldPipelinePicklistValues = {};
      state.editTestId = undefined;
      state.pipelineTest = undefined;
    });

    builder.addCase(setTestPanelView, (state, action) => {
      // TODO: This error message should be displayed outside a panel at least
      // for live tests
      state.runFieldTestsErrorMessage = '';
      state.testPanelView = action.payload.panelView;

      // When the primary panel closes also close the create test panel
      if (action.payload.panelView === null) {
        state.createTestVisible = false;
      }
    });

    builder.addCase(resetTestResult, (state, action) => {
      state.getFieldTestRunStatus = FETCH_STATUS.IDLE;
      state.getLiveTestRunStatus = FETCH_STATUS.IDLE;
      state.fieldPipelineTestRun = undefined;
      state.liveTestRun = undefined;
      state.getFieldTestRunsStatus = FETCH_STATUS.IDLE;
      state.getLiveTestRunsStatus = FETCH_STATUS.IDLE;
      state.fieldTestRuns = [];
      state.liveTestRuns = [];
      delete state.selectedTestRunTestId;
      delete state.selectedTestNodeId;
    });

    // Get Field Test Run
    builder.addCase(getFieldTestRun.pending, (state, action) => {
      state.getFieldTestRunStatus = FETCH_STATUS.LOADING;
    });
    builder.addCase(getFieldTestRun.fulfilled, (state, action) => {
      state.getFieldTestRunStatus = FETCH_STATUS.SUCCESS;
      state.fieldPipelineTestRun = action.payload.data;
    });
    builder.addCase(getFieldTestRun.rejected, (state, action) => {
      state.getFieldTestRunStatus = FETCH_STATUS.ERROR;
    });

    // Get Field Test Runs
    builder.addCase(getFieldTestRuns.pending, (state, action) => {
      state.getFieldTestRunsStatus = FETCH_STATUS.LOADING;
    });
    builder.addCase(getFieldTestRuns.fulfilled, (state, action) => {
      state.getFieldTestRunsStatus = FETCH_STATUS.SUCCESS;
      state.fieldTestRuns = action.payload.data;
    });
    builder.addCase(getFieldTestRuns.rejected, (state, action) => {
      state.getFieldTestRunsStatus = FETCH_STATUS.ERROR;
    });

    // Get Field Test Run
    builder.addCase(getLiveTestRun.pending, (state, action) => {
      state.getLiveTestRunStatus = FETCH_STATUS.LOADING;
    });
    builder.addCase(getLiveTestRun.fulfilled, (state, action) => {
      state.getLiveTestRunStatus = FETCH_STATUS.SUCCESS;
      state.liveTestRun = action.payload.data;
      state.liveTestRunRecordId = action.payload.data.resultDetails[0]?.id;

      // Update the recordsProcessed when a live test completes and we fetch the details
      const liveTestRunItem = find(state.liveTestRuns, { id: action.payload.data.id });
      if (liveTestRunItem) {
        liveTestRunItem.recordsProcessed = action.payload.data.resultDetails.length;
      }
    });
    builder.addCase(getLiveTestRun.rejected, (state, action) => {
      state.getLiveTestRunStatus = FETCH_STATUS.ERROR;
    });

    builder.addCase(setLiveTestRunRecordId, (state, action) => {
      state.liveTestRunRecordId = action.payload.recordId;
    });

    // Get Live Test Runs
    builder.addCase(getLiveTestRuns.pending, (state, action) => {
      state.getLiveTestRunsStatus = FETCH_STATUS.LOADING;
    });
    builder.addCase(getLiveTestRuns.fulfilled, (state, action) => {
      state.getLiveTestRunsStatus = FETCH_STATUS.SUCCESS;
      state.liveTestRuns = action.payload.data;
    });
    builder.addCase(getLiveTestRuns.rejected, (state, action) => {
      state.getLiveTestRunsStatus = FETCH_STATUS.ERROR;
    });

    builder.addCase(SERVER_EVENTS.SIMULATE_PIPELINE_COMPLETED, (state, action) => {
      // TODO: type
      // @ts-ignore
      state.updatedTestRunId = action.payload.simulationRunId;
    });
  },
});

export const { reducer } = testSlice;
export const thunks = {
  getFieldPipelineTests,
};
