//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { render } from 'tests/helpers';
import AppConstants from 'utils/AppConstants';

import TestRunSimulatedPanel from '../TestRunSimulatedPanel';

describe('TestRunSimulatedPanel', () => {
  test('TestRunSimulatedPanel render a test', async () => {
    const { findByText } = render(
      <TestRunSimulatedPanel pipelineId="1234" pipelineContext={AppConstants.PIPELINE_CONTEXT.FIELD} />,
      {
        testState: {
          test: {
            fieldPipelineTests: [
              {
                id: '5ff3d46ce939e584368e894b',
                displayName: 'Test1',
                description: '',
                tags: [],
                testData: {},
                ownerFirstName: 'Syncari',
                ownerLastName: 'Admin',
                ownerEmail: 'admin@syncari.com',
              },
            ],
          },
        },
      }
    );
    expect(await findByText('Run Simulated Test')).toBeInTheDocument();
    expect(await findByText('Test1')).toBeInTheDocument();
    expect(await findByText('Select all tests')).toBeInTheDocument();
  });

  test('TestRunSimulatedPanel renders empty list', async () => {
    const { findByText } = render(
      <TestRunSimulatedPanel pipelineId="1234" pipelineContext={AppConstants.PIPELINE_CONTEXT.FIELD} />,
      {
        testState: {
          test: {
            fieldPipelineTests: [],
          },
        },
      }
    );
    // expect(await findByText('Node tests allow you to test the')).toBeInTheDocument();
    expect(await findByText('New Test')).toBeInTheDocument();
  });
});
