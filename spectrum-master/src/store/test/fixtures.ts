import { merge } from 'lodash';

import { initialTestState } from './slice';
import { TestRunModel, TestState } from './types';

export const getEmptyTestState = (testState?: Partial<TestState>): TestState => merge(initialTestState, testState);

export const getEmptyTestRun = (testRun?: Partial<TestRunModel>): TestRunModel =>
  merge(
    {
      id: '60343b29e9a1fdc03f745564',
      runName: '02/22/2021 05:15:53 PM',
      status: 'COMPLETED',
      description: null,
      startTime: '',
      endTime: '',
      limit: 10,
      recordIds: {},
      createdAt: '02/22/2021 05:15:53 PM',
      updatedAt: '02/22/2021 05:15:53 PM',
      errorMsg: '',
      resultDetails: [
        {
          id: '60343b38fce6fd6f1505eaa6',
          displayName: '02/22/2021 05:15:53 PM',
          description: null,
          syncariRecordId: '603002f922767df58175d206',
          externalRecordId: '0011b00000uyqaDAAQ',
          tags: [],
          testData: {},
          ownerFirstName: 'Syncari',
          ownerLastName: 'Admin',
          ownerEmail: 'admin@syncari.com',
          status: 'success',
          errorMsg: null,
          nodes: [],
        },
      ],
    },
    testRun
  );
