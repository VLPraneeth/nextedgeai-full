//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { TestPanelView } from 'store/test/types';
import { render, screen } from 'tests/helpers';
import { tNamespaced } from 'utils/i18nUtil';

import TestRunLivePanel from '../TestRunLivePanel';

const tn = tNamespaced('TestRunLivePanel');

const testState = {
  test: {
    testPanelView: TestPanelView.LIVE_RUN,
  },
};

describe('TestRunLivePanel', () => {
  test('TestRunLivePanel renders the form when no test is running', async () => {
    render(<TestRunLivePanel onSaveChanges={jest.fn()} validate={jest.fn()} />, { testState });

    expect(await screen.findByText(tn('title'))).toBeVisible();
    expect(await screen.findByText(tn('by_date_time'))).toBeVisible();
  });

  test("TestRunLivePanel's action button is enabled when there is no validation error", async () => {
    render(<TestRunLivePanel onSaveChanges={jest.fn()} validate={jest.fn()} />, {
      testState: {
        ...testState,
        validation: {
          errors: [],
        },
      },
    });

    expect(await screen.getByText(tn('start_test')).closest('button')).toBeEnabled();
  });

  test("TestRunLivePanel's action button is diabled when there is a validation error", async () => {
    render(<TestRunLivePanel onSaveChanges={jest.fn()} validate={jest.fn()} pipelineValidationError="I AM ERROR." />, {
      testState: {
        ...testState,
        validation: {
          errors: [{}],
        },
      },
    });

    expect(await screen.getByText(tn('start_test')).closest('button')).toBeDisabled();
  });
});
