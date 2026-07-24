//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { fieldPipelineTestRun, pipelineTests } from 'store/test/__tests__/selectors.test';
import { TestPanelView } from 'store/test/types';
import { renderWithRouter, screen } from 'tests/helpers';
import AppConstants from 'utils/AppConstants';

import TestResultDetails from '../TestResultDetails';

describe('TestResultDetails', () => {
  test('TestResultDetails render without any problem', async () => {
    renderWithRouter(<TestResultDetails pipelineId="1234" pipelineContext={AppConstants.PIPELINE_CONTEXT.FIELD} />, {
      testState: {
        entityPipeline: {
          pipelineContext: AppConstants.PIPELINE_CONTEXT.FIELD,
        },
        test: {
          testPanelView: TestPanelView.SIMULATED_RESULTS,
          fieldPipelineTestRun,
          fieldTestRuns,
          selectedTestRunTestId: '5ffbd0d0a7c7456cf07fc7d3',
          fieldPipelineTests: pipelineTests,
        },
        fieldPipeline: {
          fieldPipeline: null,
        },
      },
    });

    expect(await screen.findByText('Test1 / Overview')).toBeInTheDocument();
    expect(await screen.findByText('Success')).toBeInTheDocument();
    expect(screen.queryAllByText('Account Name')).toHaveLength(2);
    expect(await screen.findByText('Output')).toBeInTheDocument();
    expect(await screen.findByText('Input')).toBeInTheDocument();
  });
});

const fieldTestRuns = [
  {
    id: '5ffc14dba7c74581c12023ca',
    runName: 'run name',
    testNames: ['Test1'],
  },
];
