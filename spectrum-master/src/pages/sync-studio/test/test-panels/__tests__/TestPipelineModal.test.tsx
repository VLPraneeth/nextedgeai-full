//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { act } from '@testing-library/react';
import MockDate from 'mockdate';

import * as EpActions from 'actions/entityPipelineActions';
import { getEmptyTestState } from 'store/test';
import { TestPanelView } from 'store/test/types';
import { fireEvent, render, screen } from 'tests/helpers';
import { tNamespaced } from 'utils/i18nUtil';

import TestRunLivePanel from '../TestRunLivePanel';

const JULY_10_2020 = new Date(2020, 6, 10, 12);
MockDate.set(JULY_10_2020);

const tn = tNamespaced('TestRunLivePanel');

describe('TestRunLivePanel', () => {
  test('TestRunLivePanel renders', async () => {
    render(<TestRunLivePanel onSaveChanges={() => Promise.resolve()} />, {
      testState: {
        test: getEmptyTestState({
          testPanelView: TestPanelView.LIVE_RUN,
        }),
        pipeline: {
          pipelineId: '123456',
        },
      },
    });

    await expect(screen.findByText('Run Live Test')).resolves.toBeInTheDocument();
  });

  test('validation message appears if no dates entered', async () => {
    const startTestSpy = jest.spyOn(EpActions, 'testPipeline');
    const pipelineId = '123456';

    render(<TestRunLivePanel onSaveChanges={() => Promise.resolve()} />, {
      testState: {
        test: getEmptyTestState({
          testPanelView: TestPanelView.LIVE_RUN,
        }),
        pipeline: { pipelineId },
      },
    });

    fireEvent.click(await screen.findByRole('button', { name: 'Start Test' }));

    expect(startTestSpy).not.toHaveBeenCalled();

    expect(await screen.findByText(tn('select_start_end_times'))).toBeInTheDocument();
  });

  test('validation message appears if no ids entered', async () => {
    const startTestSpy = jest.spyOn(EpActions, 'testPipeline');
    const pipelineId = '123456';

    render(<TestRunLivePanel onSaveChanges={() => Promise.resolve()} />, {
      testState: {
        test: getEmptyTestState({
          testPanelView: TestPanelView.LIVE_RUN,
        }),
        pipeline: { pipelineId },
      },
    });

    // change radio to external IDs
    const externalIdRadio = await screen.findByLabelText('By External ID');
    fireEvent.click(externalIdRadio);
    await screen.findByText('External IDs');

    fireEvent.click(await screen.findByRole('button', { name: 'Start Test' }));

    expect(startTestSpy).not.toHaveBeenCalled();

    expect(await screen.findByText(tn('missing_external_id_value'))).toBeInTheDocument();
  });

  test('TestRunLivePanel dates can be selected', async () => {
    const startTestSpy = jest.spyOn(EpActions, 'testPipeline');
    const pipelineId = '123456';

    const onSavePromise = Promise.resolve();

    render(<TestRunLivePanel onSaveChanges={() => onSavePromise} />, {
      testState: {
        test: getEmptyTestState({
          testPanelView: TestPanelView.LIVE_RUN,
        }),
        entityPipeline: {
          entityPipeline: {
            draft: { id: 'draft_id' },
          },
        },
        pipeline: { pipelineId },
      },
    });

    const startTimeField = await screen.findByPlaceholderText('Select Start Time');
    const endTimeField = await screen.findByPlaceholderText('Select End Time');

    fireEvent.click(startTimeField);
    await screen.findByText('Su');
    fireEvent.click(await screen.findByText('10'));
    fireEvent.click(await screen.findByRole('button', { name: 'Ok' }));

    fireEvent.click(endTimeField);
    await screen.findByText('Su');
    fireEvent.click(await screen.findByText('11'));
    fireEvent.click(await screen.findByRole('button', { name: 'Ok' }));

    fireEvent.click(await screen.findByRole('button', { name: 'Start Test' }));

    await act(() => onSavePromise);

    expect(startTestSpy).toHaveBeenCalledWith(
      {
        start: '2020-07-10T12:00:00',
        end: '2020-07-11T12:00:00',
        limit: 10,
      },
      pipelineId,
      'draft_id'
    );
  });
});
