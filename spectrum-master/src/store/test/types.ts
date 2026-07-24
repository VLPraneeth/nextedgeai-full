//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { FieldDataType } from 'components/types';
import { TestStatus } from 'pages/sync-studio/test/Test.util';
import { AuthTypes } from 'store/credential/types';
import { FetchStatus } from 'store/types';
import AppConstants from 'utils/AppConstants';
import { ValuesOf } from 'utils/TypeUtils';

export interface TestModel {
  id: string;
  description: string;
  displayName: string;
  ownerEmail: string;
  ownerFirstName?: string;
  shared?: boolean;
  ownerLastName?: string;
  tags: string[];
  testData: any;
}

export type TestModels = TestModel[];

export interface PipelineTestModel {
  id?: string;
  shared: boolean;
  ownerFirstName?: string;
  ownerLastName?: string;
  displayName?: string;
  description?: string;
}

export interface MutiValueFieldPickListValue {
  datatype: FieldDataType;
  id: string;
  label: string;
  value: string;
  isMultiValueField: boolean;
}

export type MutiValueFieldPickListValues = MutiValueFieldPickListValue[];

export interface NodeDetailsResult {
  apiName: string;
  dataType: FieldDataType; // TODO: use the field badge datatype
  displayName: string;
  failed: boolean;
  nodeId: string;
  nodeName?: string;
  value?: string;
  expectedValue?: string; // Note: This is view injected value
}

export interface TestDataModel {
  actualResult: NodeDetailsResult[];
  expectedResult: NodeDetailsResult[] | null;
  input: NodeDetailsResult[];
}

export interface IndividualNodeResult {
  displayName?: string;
  nodeId: string;
  status: TestStatus;
  testData: TestDataModel;
  errorMsg?: string;
}

export interface TestResultDetail {
  id: string;
  externalRecordId: string;
  displayName: string;
  description: string | null;
  errorMsg: string | null;
  ownerEmail: string;
  ownerFirstName?: string;
  ownerLastName?: string;
  status: TestStatus;
  tags: string[];
  nodes: IndividualNodeResult[];
  testData: TestDataModel;
  connectorName: string;
  syncariRecordId: string | null;
  entityId?: string;
}

export interface TestRunModel {
  id: string;
  runName: string;
  status: TestStatus;
  resultDetails: TestResultDetail[];
  createdAt: string;
  description: string | null;
  endTime: string;
  errorMsg: string | null;
  limit: number;
  recordIds: Record<string, string[]>;
  startTime: string;
  updatedAt: string;
  webhook?: RunLiveTestPayloadWebhookPayload['webhook'];
}

export interface TestRunsModel {
  id: string;
  createdAt: string;
  description: string | null;
  endTime: string;
  errorMsg: string;
  recordIds: Record<string, string[]>;
  startTime: string;
  status: TestStatus;
  updatedAt: string;
  runName: string;
  testNames: string[];
  recordsProcessed: number;
}

export type PipelineTestTestDataModel = Omit<TestDataModel, 'actualResult'>;
export interface PipelineTest extends Omit<TestResultDetail, 'nodes' | 'testData'> {
  testData: PipelineTestTestDataModel;
}

export enum TestPanelView {
  CLOSED = 'CLOSED',
  SIMULATED_RUN = 'SIMULATED_RUN',
  SIMULATED_RESULTS = 'SIMULATED_RESULTS',
  LIVE_RUN = 'LIVE_RUN',
  LIVE_RESULTS = 'LIVE_RESULTS',
}

export interface TestState {
  getFieldPipelineTestsStatus: FetchStatus;
  fieldPipelineTests: TestModels;
  getFieldPipelinePicklistValuesStatus: FetchStatus;
  fieldPipelinePicklistValues: Record<string, MutiValueFieldPickListValues>;
  getFieldPipelineTestRunStatus: FetchStatus;
  fieldPipelineTestRun?: TestRunModel;
  liveTestRun?: TestRunModel;
  selectedTestNodeId?: string;
  selectedTestRunTestId?: string;
  fieldTestRuns: TestRunsModel[];
  liveTestRuns: TestRunsModel[];
  saveTestErrorMessage?: string;
  runFieldTestsStatus: FetchStatus;
  runFieldTestsErrorMessage?: string;
  deleteFieldTestStatus: FetchStatus;
  getFieldPicklistValuesStatus: FetchStatus;
  saveFieldPipelineTestStatus: FetchStatus;
  createTestVisible: boolean;
  testPanelView: TestPanelView;
  liveTestRunRecordId?: string;
  getFieldTestRunStatus: FetchStatus;
  getFieldTestRunsStatus: FetchStatus;
  getLiveTestRunStatus: FetchStatus;
  getLiveTestRunsStatus: FetchStatus;
  updatedTestRunId?: string;
  editTestId?: string;
  getFieldPipelineTestStatus: FetchStatus;
  pipelineTest?: PipelineTest;
  testRunTestIds: string[];
  testRunVisible: boolean;
}

export type PipelineContextTypes = ValuesOf<typeof AppConstants.PIPELINE_CONTEXT>;

export interface RunLiveTestPayloadExternalIds {
  recordIds: Record<string, string[]>;
  limit: null; // Limit is required but not used for external IDs
}

export interface RunLiveTestPayloadDateRange {
  start: string;
  end: string;
  limit: number;
}
export interface RunLiveTestPayloadWebhookPayload {
  webhook: {
    [id: string]: {
      payload: string;
      headers: Record<string, string>;
      authConfig?: Record<string, string>;
      authType?: AuthTypes;
    };
  };
}

export type RunLiveTestPayload =
  | RunLiveTestPayloadExternalIds
  | RunLiveTestPayloadDateRange
  | RunLiveTestPayloadWebhookPayload;
