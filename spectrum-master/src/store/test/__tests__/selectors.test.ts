//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import produce from 'immer';

import { OVERVIEW_ID } from 'pages/sync-studio/test/Test.util';
import AppConstants from 'utils/AppConstants';

import {
  selectCreateTestVisible,
  selectedGetFieldPipelineTestsStatus,
  selectEditTestId,
  selectFieldPiepelineTestNode,
  selectFieldPipelineSelectedTestRunTestId,
  selectFieldPipelineTestRun,
  selectFieldPipelineTestRuns,
  selectFieldPipelineTestRunTest,
  selectGetFieldTestRunsStatus,
  selectGetFieldTestRunStatus,
  selectPicklistValues,
  selectRunFieldTestsErrorMessage,
  selectRunFieldTestsStatus,
  selectSaveTestErrorMessage,
  selectSaveTestStatus,
  selectTest,
  selectTestRun,
  selectTestRunTestIds,
  selectTestRunVisible,
  selectTests,
  selectUpdatedTestRunId,
} from '../selectors';
import { TestPanelView, TestRunModel } from '../types';

const { FETCH_STATUS } = AppConstants;
describe('test selectors', () => {
  test('selectFieldPipelineSelectedTestRunTestId returns selectedTestRunTestId', () => {
    const state: any = {
      test: {
        selectedTestRunTestId: 'RunTestId',
      },
    };
    expect(selectFieldPipelineSelectedTestRunTestId(state)).toBe('RunTestId');
  });

  test('selectFieldPipelineTestRuns returns fieldTestRuns', () => {
    const state: any = {
      test: {
        fieldTestRuns: [{ runName: 'runName' }],
      },
    };
    expect(selectFieldPipelineTestRuns(state)[0].runName).toBe('runName');
  });

  test('selectTests returns tests', () => {
    const state: any = {
      test: {
        fieldPipelineTests: [{ displayName: 'Test1' }],
      },
    };
    expect(selectTests(state)[0].displayName).toBe('Test1');
  });

  test('selectPicklistValues returns picklist values', () => {
    const state: any = {
      test: {
        fieldPipelinePicklistValues: {
          nodeId: [{ label: 'Test1', datatype: 'string' }],
        },
      },
    };
    expect(selectPicklistValues(state)['nodeId'][0].label).toBe('Test1');
    expect(selectPicklistValues(state)['nodeId'][0].datatype).toBe('string');
  });

  test('selectCreateTestVisible returns create test modal visible', () => {
    const state: any = {
      test: {
        createTestVisible: true,
      },
    };
    expect(selectCreateTestVisible(state)).toBe(true);
  });

  test('selectSaveTestErrorMessage returns save error message', () => {
    const state: any = {
      test: {
        saveTestErrorMessage: 'error message',
      },
    };
    expect(selectSaveTestErrorMessage(state)).toBe('error message');
  });

  test('selectSaveTestStatus returns save test status', () => {
    const state: any = {
      test: {
        saveFieldPipelineTestStatus: FETCH_STATUS.IDLE,
      },
    };
    expect(selectSaveTestStatus(state)).toBe(FETCH_STATUS.IDLE);
  });

  test('selectRunFieldTestsStatus returns run tests status', () => {
    const state: any = {
      test: {
        runFieldTestsStatus: FETCH_STATUS.IDLE,
      },
    };
    expect(selectRunFieldTestsStatus(state)).toBe(FETCH_STATUS.IDLE);
  });

  test('selectRunFieldTestsErrorMessage returns run tests error message', () => {
    const state: any = {
      test: {
        runFieldTestsErrorMessage: 'error message',
      },
    };
    expect(selectRunFieldTestsErrorMessage(state)).toBe('error message');
  });

  test('selectUpdatedTestRunId returns updated test run id', () => {
    const state: any = {
      test: {
        updatedTestRunId: 'test run id',
      },
    };
    expect(selectUpdatedTestRunId(state)).toBe('test run id');
  });

  test('selectedGetFieldPipelineTestsStatus returns get pipeline tests status', () => {
    const state: any = {
      test: {
        getFieldPipelineTestsStatus: FETCH_STATUS.IDLE,
      },
    };
    expect(selectedGetFieldPipelineTestsStatus(state)).toBe(FETCH_STATUS.IDLE);
  });

  test('selectGetFieldTestRunStatus returns get pipeline tests status', () => {
    const state: any = {
      test: {
        getFieldTestRunStatus: FETCH_STATUS.IDLE,
      },
    };
    expect(selectGetFieldTestRunStatus(state)).toBe(FETCH_STATUS.IDLE);
  });

  test('selectGetFieldTestRunsStatus returns get pipeline test runs status', () => {
    const state: any = {
      test: {
        getFieldTestRunsStatus: FETCH_STATUS.IDLE,
      },
    };
    expect(selectGetFieldTestRunsStatus(state)).toBe(FETCH_STATUS.IDLE);
  });

  test('selectEditTestId returns the selected id of the pipeline test to edit', () => {
    const state: any = {
      test: {
        editTestId: 'testId',
      },
    };
    expect(selectEditTestId(state)).toBe('testId');
  });

  test('selectTest returns the selected id of the pipeline test to edit', () => {
    const pipelineTest = {
      id: 'testid',
      description: 'testIdDescription',
      ownerEmail: 'admin@syncari.com',
    };
    const state: any = {
      test: {
        pipelineTest,
      },
    };
    expect(selectTest(state)).toEqual(pipelineTest);
  });

  test('selectFieldPipelineTestRun returns status test run', () => {
    const testRun = {
      id: '5ffc14dba7c74581c12023ca',
      runName: '2021-01-11 01:05:25',
      status: 'COMPLETED',
      resultDetails: '',
    };
    const state: any = {
      test: {
        fieldPipelineTestRun: testRun,
      },
    };
    expect(selectFieldPipelineTestRun(state)).toEqual(testRun);
  });

  test('selectTestRunVisible returns visibility of the test run modal', () => {
    const state: any = {
      test: {
        testRunVisible: true,
      },
    };
    expect(selectTestRunVisible(state)).toEqual(true);
  });

  test('selectTestRunTestIds returns visibility of the test run modal', () => {
    const testRunTestIds = ['id1', 'id2'];
    const state: any = {
      test: {
        testRunTestIds,
      },
    };
    expect(selectTestRunTestIds(state)).toEqual(testRunTestIds);
  });

  test('selectTestRun returns a test run with the overview node injected', () => {
    const state: any = {
      test: {
        testPanelView: TestPanelView.SIMULATED_RESULTS,
        fieldPipelineTestRun,
      },
    };
    expect(selectTestRun(state)?.resultDetails[0]?.nodes[0].testData).toEqual(
      fieldPipelineTestRun.resultDetails[0].testData
    );
    expect(selectTestRun(state)?.resultDetails[0]?.nodes[0].nodeId).toEqual(OVERVIEW_ID);
  });

  test('selectTestRun returns a test run with failed node and expected value populated', () => {
    const localFieldPipelineTestRun = produce(fieldPipelineTestRun, (draft) => {
      draft.resultDetails[0].testData.actualResult[0].failed = true;
      const { expectedResult } = draft.resultDetails[0].testData;
      if (expectedResult) {
        expectedResult[0].value = 'failed value';
      }
    });
    const state: any = {
      test: {
        testPanelView: TestPanelView.SIMULATED_RESULTS,
        fieldPipelineTestRun: localFieldPipelineTestRun,
      },
    };
    expect(selectTestRun(state)?.resultDetails[0]?.nodes[0].nodeId).toEqual(OVERVIEW_ID);
    expect(selectTestRun(state)?.resultDetails[0]?.nodes[0].testData.actualResult[0].expectedValue).toEqual(
      'failed value'
    );
  });

  test('selectFieldPipelineTestRunTest returns selectedTestId', () => {
    const state: any = {
      test: {
        testPanelView: TestPanelView.SIMULATED_RESULTS,
        fieldPipelineTestRun,
        selectedTestRunTestId: '5ffbd0d0a7c7456cf07fc7d3',
      },
    };
    expect(selectFieldPipelineTestRunTest(state)?.id).toEqual('5ffbd0d0a7c7456cf07fc7d3');
  });

  test('selectFieldPipelineTestRunTest returns blank', () => {
    const state: any = {
      test: {
        fieldPipelineTestRun,
      },
    };
    expect(selectFieldPipelineTestRunTest(state)).toEqual(undefined);
  });

  test('selectFieldPiepelineTestNode returns the selected test node', () => {
    const state: any = {
      test: {
        testPanelView: TestPanelView.SIMULATED_RESULTS,
        fieldPipelineTestRun,
        selectedTestRunTestId: '5ffbd0d0a7c7456cf07fc7d3',
        selectedTestNodeId: '5ffbcd02a7c7456cf07fc3b3',
      },
    };
    expect(selectFieldPiepelineTestNode(state, '5ffbcd02a7c7456cf07fc3b3')?.nodeId).toEqual('5ffbcd02a7c7456cf07fc3b3');
  });

  test('selectFieldPiepelineTestNode returns the overview test node if no test node id is selected', () => {
    const state: any = {
      test: {
        testPanelView: TestPanelView.SIMULATED_RESULTS,
        fieldPipelineTestRun,
        selectedTestRunTestId: '5ffbd0d0a7c7456cf07fc7d3',
      },
    };
    expect(selectFieldPiepelineTestNode(state, OVERVIEW_ID)?.nodeId).toEqual(OVERVIEW_ID);
  });
});

export const fieldTestRuns = [
  {
    id: '5ffc14dba7c74581c12023ca',
    runName: 'run name',
    testNames: ['Test1'],
  },
];

export const fieldPipelineTestRun: TestRunModel = {
  id: '5ffc14dba7c74581c12023ca',
  runName: 'run name',
  status: 'COMPLETED',
  resultDetails: [
    // @ts-ignore: Partial data for test
    {
      id: '5ffbd0d0a7c7456cf07fc7d3',
      displayName: 'Test1',
      description: 'Test 1 Description',
      externalRecordId: 'externalRecordId',
      syncariRecordId: 'syncariRecordId',
      tags: [],
      testData: {
        input: [
          {
            nodeId: '5ffbcd02a7c7456cf07fc3b3',
            apiName: 'Name',
            displayName: 'Account Name',
            dataType: 'string',
            value: 'marketo',
            failed: false,
          },
        ],
        expectedResult: [
          {
            nodeId: '5ffbcd02a7c7456cf07fc3b4',
            apiName: 'Name',
            displayName: 'Account Name',
            dataType: 'string',
            value: 'Marketo',
            failed: false,
          },
        ],
        actualResult: [
          {
            nodeId: '5ffbcd02a7c7456cf07fc3b4',
            apiName: 'Name',
            displayName: 'Account Name',
            dataType: 'string',
            value: 'Marketo',
            failed: false,
          },
        ],
      },
      ownerFirstName: 'Syncari',
      ownerLastName: 'Admin',
      ownerEmail: 'admin@syncari.com',
      status: 'success',
      errorMsg: null,
      nodes: [
        {
          nodeId: '5ffbcd02a7c7456cf07fc3b3',
          displayName: 'Sync from Account Name',
          status: 'COMPLETED',
          testData: {
            input: [
              {
                nodeId: '5ffbcd02a7c7456cf07fc3b3',
                apiName: 'Name',
                displayName: 'Account Name',
                dataType: 'string',
                value: 'marketo',
                failed: false,
              },
            ],
            expectedResult: null,
            actualResult: [
              {
                nodeId: '5ffbcd02a7c7456cf07fc3b3',
                apiName: 'Name',
                displayName: 'Account Name',
                dataType: 'string',
                value: 'marketo',
                failed: false,
              },
            ],
          },
        },
        {
          nodeId: '5ffbcd1551676e665d8e1ed3',
          displayName: 'Capitalize',
          status: 'COMPLETED',
          testData: {
            input: [
              {
                nodeId: '5ffbcd1551676e665d8e1ed3',
                apiName: 'Name',
                displayName: 'Account Name',
                dataType: 'string',
                value: 'marketo',
                failed: false,
              },
            ],
            expectedResult: null,
            actualResult: [
              {
                nodeId: '5ffbcd1551676e665d8e1ed3',
                apiName: 'Name',
                displayName: 'Account Name',
                dataType: 'string',
                value: 'Marketo',
                failed: false,
              },
            ],
          },
        },
        {
          nodeId: '5ffbcd02a7c7456cf07fc3b1',
          displayName: 'Account Name',
          status: 'COMPLETED',
          testData: {
            input: [
              {
                nodeId: '5ffbcd02a7c7456cf07fc3b1',
                apiName: 'Name',
                displayName: 'Account Name',
                dataType: 'string',
                value: 'Marketo',
                failed: false,
              },
            ],
            expectedResult: null,
            actualResult: [
              {
                nodeId: '5ffbcd02a7c7456cf07fc3b1',
                apiName: 'Name',
                displayName: 'Account Name',
                dataType: 'string',
                value: 'Marketo',
                failed: false,
              },
            ],
          },
        },
        {
          nodeId: '5ffbcd02a7c7456cf07fc3b4',
          displayName: 'Sync to Account Name',
          status: 'SUCCESS',
          testData: {
            input: [
              {
                nodeId: '5ffbcd02a7c7456cf07fc3b4',
                apiName: 'Name',
                displayName: 'Account Name',
                dataType: 'string',
                value: 'Marketo',
                failed: false,
              },
            ],
            expectedResult: null,
            actualResult: [
              {
                nodeId: '5ffbcd02a7c7456cf07fc3b4',
                apiName: 'Name',
                displayName: 'Account Name',
                dataType: 'string',
                value: 'Marketo',
                failed: false,
              },
            ],
          },
        },
      ],
    },
  ],
};

export const pipelineTests = [
  {
    id: '5ffbd0d0a7c7456cf07fc7d3',
    displayName: 'Test1',
    description: 'Test 1 Description',
    tags: ['test1tag'],
    testData: null,
    ownerFirstName: 'Syncari',
    ownerLastName: 'Admin',
    ownerEmail: 'admin@syncari.com',
  },
];

export const pipeline = {
  nodes: [
    {
      id: '5ffbcd02a7c7456cf07fc3b1',
      name: 'Account Name',
      apiName: 'Name',
      label: 'Account Name',
      subLabel: 'Syncari',
      nodeType: 'CORE_ATTRIBUTE',
    },
    {
      id: '5ffbcd02a7c7456cf07fc3b3',
      name: 'Account Name',
      apiName: 'Name',
      label: 'Sync from Account Name',
      subLabel: 'sfdcone Account',

      nodeType: 'ATTRIBUTE_SOURCE',
    },
    {
      id: '5ffbcd02a7c7456cf07fc3b4',
      name: 'Account Name',
      apiName: 'Name',
      label: 'Sync to Account Name',
      subLabel: 'sfdcone Account',

      nodeType: 'ATTRIBUTE_SINK',
    },
    {
      id: '5ffbcd1551676e665d8e1ed3',
      name: 'Capitalize',
      apiName: 'capitalize',
      label: 'Capitalize',
      subLabel: '',

      nodeType: 'FUNCTION',
    },
  ],
  edges: [
    {
      id: '5ffbcd02a7c7456cf07fc3b6',
      source: {
        nodeId: '5ffbcd02a7c7456cf07fc3b1',

        anchor: '1',
      },
      destination: {
        nodeId: '5ffbcd02a7c7456cf07fc3b4',

        anchor: '3',
      },
    },
    {
      id: '0ad2c0c4',
      source: {
        nodeId: '5ffbcd02a7c7456cf07fc3b3',

        anchor: '0',
      },
      destination: {
        nodeId: '5ffbcd1551676e665d8e1ed3',

        anchor: '3',
      },
    },
    {
      id: '039d70dd',
      source: {
        nodeId: '5ffbcd1551676e665d8e1ed3',

        anchor: '2',
      },
      destination: {
        nodeId: '5ffbcd02a7c7456cf07fc3b1',

        anchor: '0',
      },
    },
  ],
  id: '5ffbcd02a7c7456cf07fc3b0',
  targetId: '5ffbca83a7c7456be9550e76',
  parentId: null,
  scope: 'ATTRIBUTE',
  name: 'Account Name',
  createdBy: null,
  updatedBy: '5ffbca24a7c7456bb2c8cca6',
  createdAt: null,
  updatedAt: '2021-01-11T03:59:42.055+0000',
  lastSyncedTime: null,
  syncStatus: null,
  ready: false,
  draftStatus: 'NEW',
  readOnly: false,
  readOnlyReason: '',
  draft: null,
};

export const fieldPipelinePicklistValues = {
  '5ffbcd02a7c7456cf07fc3b4': [
    {
      datatype: 'string',
      label: 'Account Name',
      id: '5ffbccf9a7c7456cf07f9dab',
      value: 'Name',
    },
    {
      datatype: 'textarea',
      label: 'Account Description',
      id: '5ffbccf9a7c7456cf07f9dc3',
      value: 'Description',
    },
    {
      datatype: 'picklist',
      label: 'Billing Geocode Accuracy',
      id: '5ffbccf9a7c7456cf07f9db5',
      value: 'BillingGeocodeAccuracy',
    },
  ],
  '5ffbcd02a7c7456cf07fc3b3': [
    {
      datatype: 'string',
      label: 'Account Name',
      id: '5ffbccf9a7c7456cf07f9dab',
      value: 'Name',
    },
    {
      datatype: 'textarea',
      label: 'Account Description',
      id: '5ffbccf9a7c7456cf07f9dc3',
      value: 'Description',
    },
    {
      datatype: 'picklist',
      label: 'Billing Geocode Accuracy',
      id: '5ffbccf9a7c7456cf07f9db5',
      value: 'BillingGeocodeAccuracy',
    },
  ],
};
