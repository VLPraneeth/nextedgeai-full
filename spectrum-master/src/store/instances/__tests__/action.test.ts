// @ts-nocheck
import { createAppTestStore } from 'tests/helpers/StoreHelper';
import * as AjaxUtils from 'utils/AjaxUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { t } from 'utils/i18nUtil';

import { createInstance, getInstances, InstanceType, showInstanceModal } from '../slice';

jest.mock('utils/AjaxUtil');
const mockedAjaxUtils = AjaxUtils as jest.Mocked<typeof AjaxUtils>;
const mockGet = mockedAjaxUtils.get;
const mockPost = mockedAjaxUtils.post;

// this plucks off the thunk metadata so we can assert on the action itself
const ignoreThunkMeta = <T extends Record<string, any>>({ meta, ...action }: T) => action;

// constant used for actions where we've used `rejectWithValue`. You will need to assert the paylaod is your
// error message, and then pop this into the action to match what redux-toolkit will place there
const rejectedWithValueError = {
  message: 'Rejected',
};

describe('getInstances', () => {
  test('success', async () => {
    const instances = [
      {
        syncariId: '12312412515',
      },
    ];

    mockGet.mockImplementation((url) => {
      expect(url).toBe(DataUrlConstants.INSTANCE);
      return Promise.resolve({ data: instances });
    });

    const store = createAppTestStore();
    const expectedActions = [
      { type: getInstances.pending.type },
      { type: getInstances.fulfilled.type, payload: instances },
    ];

    await store.dispatch(getInstances());

    // trim off the thunk meta information
    return expect(store.getActions().map(ignoreThunkMeta)).toEqual(expectedActions);
  });

  test('failure', async () => {
    mockGet.mockImplementation((url) => {
      expect(url).toBe(DataUrlConstants.INSTANCE);
      return Promise.reject(new Error('Test Network Error'));
    });

    const store = createAppTestStore();
    const expectedActions = [
      { payload: undefined, type: getInstances.pending.type },
      { type: getInstances.rejected.type, error: rejectedWithValueError, payload: 'Test Network Error' },
    ];

    await store.dispatch(getInstances());

    return expect(store.getActions().map(ignoreThunkMeta)).toEqual(expectedActions);
  });

  describe('showInstanceModal', () => {
    const runInstanceModalTest = (flag?: boolean) => {
      const store = createAppTestStore();
      const expectedFlag = typeof flag === 'boolean' ? flag : true;

      const expectedActions = [{ type: showInstanceModal.type, payload: expectedFlag }];

      store.dispatch(showInstanceModal(flag));
      return expect(store.getActions()).toEqual(expectedActions);
    };

    test('no argument defaults to open', () => {
      return runInstanceModalTest();
    });

    test('true argument', () => {
      return runInstanceModalTest(true);
    });

    test('false argument', () => {
      return runInstanceModalTest(false);
    });
  });
});

describe('createInstances', () => {
  const instanceParams = {
    displayName: 'test instance',
    instanceName: 'test instance',
    orgId: '',
    planName: 'default',
    type: 'sandbox' as InstanceType,
  };

  test('success', async () => {
    mockPost.mockImplementation((url: string, params: Record<string, any>) => {
      expect(url).toBe(DataUrlConstants.INSTANCE);
      expect(params).toEqual(instanceParams);
      return Promise.resolve(instanceParams);
    });

    const store = createAppTestStore();
    const expectedActions = [{ type: createInstance.pending.type }, { type: createInstance.fulfilled.type }];

    await store.dispatch(createInstance(instanceParams));

    return expect(store.getActions().map(ignoreThunkMeta)).toEqual(expectedActions);
  });

  test('failure', async () => {
    const networkError = new Error('Test Network Error');

    mockPost.mockImplementation((url, params) => {
      expect(url).toBe(DataUrlConstants.INSTANCE);
      expect(params).toEqual(instanceParams);

      return Promise.reject(networkError);
    });

    const store = createAppTestStore();
    const expectedActions = [
      { payload: undefined, type: createInstance.pending.type },
      {
        type: createInstance.rejected.type,
        error: rejectedWithValueError,
        payload: t('Settings.Instances.fallback_create_instance_error'),
      },
    ];

    await store.dispatch(createInstance(instanceParams));

    return expect(store.getActions().map(ignoreThunkMeta)).toEqual(expectedActions);
  });
});
