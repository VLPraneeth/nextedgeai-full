//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { PIPELINE_CONTEXT } from 'pages/sync-studio/types';
import * as TestActions from 'store/test/thunks';
import { render, fireEvent } from 'tests/helpers';
import { tc, tNamespaced } from 'utils/i18nUtil';

import TestRunSimulatedTitleModal from '../TestRunSimulatedTitleModal';

const tn = tNamespaced('TestRunSimulatedTitleModal');

describe('TestRunSimulatedTitleModal', () => {
  test('TestRunSimulatedTitleModal render without any problem', async () => {
    const { findByText } = render(
      <TestRunSimulatedTitleModal pipelineId={'1234'} pipelineContext={PIPELINE_CONTEXT.FIELD} />,
      {
        testState: { test: { testRunVisible: true } },
      }
    );

    expect(await findByText(tn('title'))).toBeInTheDocument();
    expect(await findByText(tn('run'))).toBeInTheDocument();
  });

  test('TestRunSimulatedTitleModal with default value and validate blank run name', async () => {
    const { findByText } = render(
      <TestRunSimulatedTitleModal pipelineId={'1234'} pipelineContext={PIPELINE_CONTEXT.FIELD} />,
      {
        testState: { test: { testRunVisible: true } },
      }
    );

    const runName = document.querySelector<HTMLInputElement>('input[name="runName"]');
    expect(runName?.value?.length).toBeGreaterThan(1);
    runName && fireEvent.change(runName, { target: { value: 'testrunname' } });

    expect(runName?.value).toBe('testrunname');

    // @ts-expect-error: if runname is null, that's OK because it will crash the test
    fireEvent.change(runName, { target: { value: '' } });
    fireEvent.click(await findByText(tn('run')));
    expect(await findByText(tc('cannot_be_empty', { name: tn('test_run_name') }))).toBeInTheDocument();
  });

  test('TestRunSimulatedTitleModal to save the run name value', async () => {
    const runSpy = jest.spyOn(TestActions, 'runFieldTests');
    const { findByText } = render(
      <TestRunSimulatedTitleModal pipelineId={'1234'} pipelineContext={PIPELINE_CONTEXT.FIELD} />,
      {
        testState: { test: { testRunVisible: true, testRunTestIds: ['1', '2'] } },
      }
    );

    const runName = document.querySelector<HTMLInputElement>('input[name="runName"]');
    // @ts-expect-error: if runname is null, that's OK because it will crash the test
    fireEvent.change(runName, { target: { value: 'my run name' } });
    fireEvent.click(await findByText(tn('run')));
    expect(runSpy).toHaveBeenCalledWith({
      fieldPipelineId: '1234',
      name: 'my run name',
      pipelineContext: 'field',
      testIds: ['1', '2'],
    });
  });
});
