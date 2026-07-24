//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { createAsyncThunk } from '@reduxjs/toolkit';

import { PipelineContextTypes } from 'store/test/types';
import { post, get, put, deleteRequest } from 'utils/AjaxUtil';
import AppConstants from 'utils/AppConstants';
import DataUrlConstants from 'utils/DataUrlConstants';
import { replaceToken } from 'utils/StringUtil';

import { TestModels, MutiValueFieldPickListValues, TestRunModel, TestRunsModel, PipelineTest } from './types';

const isEntityPipelineContext = (context: string) => context === AppConstants.PIPELINE_CONTEXT.ENTITY;

export interface getFieldPipelineTestsArgs {
  pipelineContext: PipelineContextTypes;
  fieldPipelineId: string;
}

export const getFieldPipelineTests = createAsyncThunk(
  'test/fetchTests',
  ({ pipelineContext, fieldPipelineId }: getFieldPipelineTestsArgs) => {
    return get<TestModels>(
      replaceToken(
        isEntityPipelineContext(pipelineContext)
          ? DataUrlConstants.ENTITY_PIPELINE_TESTS
          : DataUrlConstants.FIELD_PIPELINE_TESTS,
        { fieldPipelineId }
      )
    );
  }
);

export interface getFieldPipelineTestArgs {
  pipelineContext: PipelineContextTypes;
  fieldPipelineId: string;
  testId: string;
}

export const getFieldPipelineTest = createAsyncThunk(
  'test/fetchTest',
  ({ pipelineContext, fieldPipelineId, testId }: getFieldPipelineTestArgs) => {
    return get<PipelineTest>(
      replaceToken(
        isEntityPipelineContext(pipelineContext)
          ? DataUrlConstants.ENTITY_PIPELINE_TEST
          : DataUrlConstants.FIELD_PIPELINE_TEST,
        { fieldPipelineId, testId }
      )
    );
  }
);

export interface runFieldTestsArgs {
  pipelineContext: PipelineContextTypes;
  fieldPipelineId: string;
  testIds: string[];
  name: string;
}

export const runFieldTests = createAsyncThunk(
  'test/runTests',
  ({ pipelineContext, fieldPipelineId, testIds, name }: runFieldTestsArgs, { rejectWithValue }) => {
    return post<TestRunModel>(
      replaceToken(
        isEntityPipelineContext(pipelineContext)
          ? DataUrlConstants.ENTITY_PIPELINE_RUN_TESTS
          : DataUrlConstants.FIELD_PIPELINE_RUN_TESTS,
        { fieldPipelineId }
      ),
      { testIds, name }
    ).catch((error) => {
      return rejectWithValue(error?.response?.data);
    });
  }
);

export interface deleteFieldTestArgs {
  pipelineContext: PipelineContextTypes;
  fieldPipelineId: string;
  testId: string;
}

export const deleteFieldTest = createAsyncThunk(
  'test/deleteTest',
  ({ pipelineContext, fieldPipelineId, testId }: deleteFieldTestArgs, { dispatch }) => {
    return deleteRequest<null>(
      replaceToken(
        isEntityPipelineContext(pipelineContext)
          ? DataUrlConstants.ENTITY_PIPELINE_TEST
          : DataUrlConstants.FIELD_PIPELINE_TEST,
        { fieldPipelineId, testId }
      )
    ).then(() => {
      dispatch(getFieldPipelineTests({ pipelineContext, fieldPipelineId }));
    });
  }
);

export interface getFieldPicklistValuesArgs {
  pipelineContext: PipelineContextTypes;
  fieldPipelineId: string;
  nodeId: string;
}

export const getFieldPicklistValues = createAsyncThunk(
  'test/getPicklistValues',
  ({ pipelineContext, fieldPipelineId, nodeId }: getFieldPicklistValuesArgs) => {
    return get<MutiValueFieldPickListValues>(
      replaceToken(
        isEntityPipelineContext(pipelineContext)
          ? DataUrlConstants.ENTITY_PIPELINE_PICKLIST_VALUES
          : DataUrlConstants.FIELD_PIPELINE_PICKLIST_VALUES,
        { fieldPipelineId, nodeId }
      )
    );
  }
);

export interface SaveFieldPipelineTestArgs {
  pipelineContext: PipelineContextTypes;
  fieldPipelineId: string;
  test: any; // TODO: Type this
  testId?: string;
}

export interface saveFieldPipelineTestErrorResponse {
  message: string;
}

export const saveFieldPipelineTest = createAsyncThunk<
  void,
  SaveFieldPipelineTestArgs,
  { rejectValue: { error: string } }
>('test/saveFieldPipelineTest', ({ pipelineContext, fieldPipelineId, test, testId }, { dispatch, rejectWithValue }) => {
  const entityUrl = testId ? DataUrlConstants.ENTITY_PIPELINE_TEST : DataUrlConstants.ENTITY_PIPELINE_TESTS;
  const fieldUrl = testId ? DataUrlConstants.FIELD_PIPELINE_TEST : DataUrlConstants.FIELD_PIPELINE_TESTS;
  return (testId ? put : post)<PipelineTest>(
    replaceToken(isEntityPipelineContext(pipelineContext) ? entityUrl : fieldUrl, { fieldPipelineId, testId }),
    test
  )
    .then(() => {
      dispatch(
        getFieldPipelineTests({
          pipelineContext,
          fieldPipelineId,
        })
      );
    })
    .catch((error) => {
      return rejectWithValue(error?.response?.data);
    });
});

export interface getFieldTestRunArgs {
  pipelineContext: PipelineContextTypes;
  fieldPipelineId: string;
  runId: string;
}

export const getFieldTestRun = createAsyncThunk(
  'test/getFieldTestRun',
  ({ pipelineContext, fieldPipelineId, runId }: getFieldTestRunArgs) => {
    return get<TestRunModel>(
      replaceToken(
        isEntityPipelineContext(pipelineContext)
          ? DataUrlConstants.ENTITY_PIPELINE_RUN_TEST
          : DataUrlConstants.FIELD_PIPELINE_RUN_TEST,
        { fieldPipelineId, runId }
      )
    );
  }
);

export interface getFieldTestRunsArgs {
  pipelineContext: PipelineContextTypes;
  fieldPipelineId: string;
}
export const getFieldTestRuns = createAsyncThunk(
  'test/getFieldTestRuns',
  ({ pipelineContext, fieldPipelineId }: getFieldTestRunsArgs) => {
    return get<TestRunsModel[]>(
      replaceToken(
        isEntityPipelineContext(pipelineContext)
          ? DataUrlConstants.ENTITY_PIPELINE_RUN_TESTS
          : DataUrlConstants.FIELD_PIPELINE_RUN_TESTS,
        { fieldPipelineId }
      )
    );
  }
);

export interface getLiveTestRunsArgs {
  graphId: string;
}
export const getLiveTestRuns = createAsyncThunk('test/getLiveTestRuns', ({ graphId }: getLiveTestRunsArgs) => {
  return get<TestRunsModel[]>(replaceToken(DataUrlConstants.LIVE_TEST_RUNS, { graphId }));
});

export interface getLiveTestRunArgs {
  graphId: string;
  runId: string;
}
export const getLiveTestRun = createAsyncThunk('test/getLiveTestRun', ({ graphId, runId }: getLiveTestRunArgs) => {
  return get<TestRunModel>(replaceToken(DataUrlConstants.LIVE_TEST_RUN, { graphId, runId }));
});
