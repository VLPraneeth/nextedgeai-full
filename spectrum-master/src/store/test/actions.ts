//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { createAction } from '@reduxjs/toolkit';

import { TestPanelView } from './types';

export const selectTestRunTestId = createAction('test/selectTestRunTestId', (testRunTestId) => {
  return {
    payload: {
      testRunTestId,
    },
  };
});

export const setLiveTestRunRecordId = createAction('test/setLiveTestRunRecordId', (recordId: string) => {
  return {
    payload: {
      recordId,
    },
  };
});

export const showCreateTest = createAction('test/showCreateTest', (visible, testId = '') => {
  return {
    payload: {
      visible,
      testId,
    },
  };
});

export const setTestPanelView = createAction('test/setTestPanelView', (panelView: TestPanelView) => {
  return {
    payload: {
      panelView,
    },
  };
});

export const showRunTest = createAction('test/showRunTest', (visible, testIds = []) => {
  return {
    payload: {
      visible,
      testIds,
    },
  };
});

export const resetSimulatedTestRun = createAction('test/resetTest');

export const resetCreateTest = createAction('test/resetCreateTest');

export const resetTestResult = createAction('test/resetTestResult');

export * from './thunks';
