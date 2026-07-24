//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { fieldPipelineTestRun, pipelineTests } from 'store/test/__tests__/selectors.test';
import { TestPanelView } from 'store/test/types';
import { renderWithRouter, screen } from 'tests/helpers';
import AppConstants from 'utils/AppConstants';

import TestResultCollapse from '../TestResultCollapse';

describe('TestResultCollapse', () => {
  test('TestResultCollapse render without any problem', async () => {
    renderWithRouter(<TestResultCollapse />, {
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

    expect(await screen.findByText('Node: Sync to Account Name')).toBeInTheDocument();
    expect(await screen.findByText('Node: Account Name')).toBeInTheDocument();
    expect(await screen.findByText('Node: Capitalize')).toBeInTheDocument();
    expect(screen.queryAllByText('Completed')).toHaveLength(3);
    expect(screen.queryAllByText('Success')).toHaveLength(2);
  });
});

const fieldTestRuns = [
  {
    id: '5ffc14dba7c74581c12023ca',
    runName: 'run name',
    testNames: ['Test1'],
  },
];
