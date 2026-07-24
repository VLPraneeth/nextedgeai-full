import { waitFor } from '@testing-library/react';

import { ActionTypes, validate } from 'actions/entityPipelineActions';
import { mockedAjaxUtils } from 'tests/helpers';

const AjaxUtil = mockedAjaxUtils();
jest.mock('utils/AjaxUtil');

const entityId = '62d8675017595e00019221e8';

const validationErrors = [
  {
    level: 'ENTITY',
    type: 'ERROR',
    nodeId: '62fa9b66b095aced15de6a2a',
    targetId: '62d8675017595e00019221e8',
    message: 'Missing Filter Conditions from Average in graph Account',
  },
  {
    level: 'ATTRIBUTE',
    type: 'ERROR',
    nodeId: '62fa35c00086e247fd66d789',
    targetId: '62d8675017595e00019221f4',
    message: 'Node Strip Tags should be connected to other nodes in graph Account Name',
  },
];

const customServerErrorValidation = [
  {
    level: 'GLOBAL',
    type: 'ERROR',
    message:
      'The server encountered a temporary error and could not complete your request. Please try again in 30 seconds.',
  },
];

describe('entityPipelineActions', () => {
  test('validate dispatches validationErrors when server returns 500 with errors', async () => {
    const mockedDispatch = jest.fn();
    AjaxUtil.post.mockImplementation(
      jest.fn(() =>
        Promise.reject({
          response: {
            status: 500,
            data: {
              status: '500',
              validationErrors,
            },
          },
        })
      )
    );

    validate(entityId, {})(mockedDispatch);

    expect(mockedDispatch).toHaveBeenCalledWith({
      type: ActionTypes.VALIDATE_ENTITY_PIPELINE_PENDING,
    });

    await waitFor(() => undefined, { interval: 1000 });

    expect(mockedDispatch).toHaveBeenLastCalledWith({
      type: ActionTypes.VALIDATE_ENTITY_PIPELINE_FAILED,
      error: {
        status: '500',
        validationErrors,
      },
    });
  });

  test('validate dispatches custom error message when server returns a 502', async () => {
    const mockedDispatch = jest.fn();
    AjaxUtil.post.mockImplementation(
      jest.fn(() =>
        Promise.reject({
          response: {
            status: 502,
            data: {
              status: '502',
            },
          },
        })
      )
    );

    validate(entityId, {})(mockedDispatch);

    expect(mockedDispatch).toHaveBeenCalledWith({
      type: ActionTypes.VALIDATE_ENTITY_PIPELINE_PENDING,
    });

    await waitFor(() => undefined, { interval: 1000 });

    expect(mockedDispatch).toHaveBeenLastCalledWith({
      type: ActionTypes.VALIDATE_ENTITY_PIPELINE_FAILED,
      error: {
        status: '502',
        validationErrors: customServerErrorValidation,
      },
    });
  });

  test('validate dispatches a transformed error response when server returns a flat validation error', async () => {
    const mockedDispatch = jest.fn();
    AjaxUtil.post.mockImplementation(
      jest.fn(() =>
        Promise.reject({
          response: {
            status: 500,
            data: {
              status: '500',
              message: 'This is an error message.',
            },
          },
        })
      )
    );

    validate(entityId, {})(mockedDispatch);

    expect(mockedDispatch).toHaveBeenCalledWith({
      type: ActionTypes.VALIDATE_ENTITY_PIPELINE_PENDING,
    });

    await waitFor(() => undefined, { interval: 1000 });

    expect(mockedDispatch).toHaveBeenLastCalledWith({
      type: ActionTypes.VALIDATE_ENTITY_PIPELINE_FAILED,
      error: {
        status: '500',
        validationErrors: [
          {
            level: 'GLOBAL',
            type: 'ERROR',
            message: 'This is an error message.',
          },
        ],
      },
    });
  });
});
