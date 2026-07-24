import { act } from '@testing-library/react';
import * as antd from 'antd';

import useDisplayApplicationError from 'hooks/useDisplayApplicationError';
import { APPLICATION_ERROR, ErrorMessage, getApplicationErrorMessage } from 'store/app/app.types';
import configureAppStore from 'store/configureStore';
import { renderHook } from 'tests/helpers';
import { RESPONSE_CODE } from 'utils/AppUtil';

jest.mock('antd');
const mockedNotification = antd.notification as jest.Mocked<typeof antd['notification']>;

describe('useDisplayApplicationError', () => {
  it('should display an error notification when an application failed action is fired', async () => {
    const watcher = jest.fn();
    mockedNotification.error.mockImplementation(watcher);

    const store = configureAppStore();
    renderHook(useDisplayApplicationError, { store });

    const description = 'Test error message.';

    act(() => {
      store.dispatch({
        type: APPLICATION_ERROR,
        applicationError: {
          message: description,
          status: RESPONSE_CODE.APPLICATION_ERROR,
        },
      });
    });

    expect(watcher).toHaveBeenCalledWith({
      message: getApplicationErrorMessage(ErrorMessage.applicationError),
      description,
    });
  });
});
