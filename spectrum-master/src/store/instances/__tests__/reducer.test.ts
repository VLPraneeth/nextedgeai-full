// @ts-nocheck
import AppConstants from 'utils/AppConstants';

import { getInstances, InstanceSlice, reducer, showInstanceModal } from '../slice';

describe('instances reducer', () => {
  test('returns initial state', () => {
    const instanceState: InstanceSlice = {
      currentInstanceState: {},
      instanceCreatingStatus: AppConstants.FETCH_STATUS.IDLE,
      instanceUpdatingStatus: AppConstants.FETCH_STATUS.IDLE,
      instanceModalVisible: false,
      instanceCopyModal: {
        visible: false,
      },
      instances: [],
      instancesStatus: AppConstants.FETCH_STATUS.IDLE,
      pendingInstanceUpdates: {},
    };
    expect(reducer(undefined, {})).toEqual(instanceState);
  });

  test('getInstancesPending', () => {
    expect(
      reducer(
        {
          instancesStatus: AppConstants.FETCH_STATUS.IDLE,
          instances: [],
        },
        {
          type: getInstances.pending.type,
        }
      )
    ).toEqual({
      instancesStatus: AppConstants.FETCH_STATUS.LOADING,
      instances: [],
    });
  });

  test('getInstances success', () => {
    const instances = [{ syncariId: '124125152' }, { syncariId: '0870sdf' }];

    expect(
      reducer(
        {
          instancesStatus: AppConstants.FETCH_STATUS.LOADING,
          instances: [],
        },
        {
          type: getInstances.fulfilled.type,
          payload: instances,
        }
      )
    ).toEqual({
      instancesStatus: AppConstants.FETCH_STATUS.SUCCESS,
      instances,
    });
  });

  test('getInstances failure', () => {
    expect(
      reducer(
        {
          instancesStatus: AppConstants.FETCH_STATUS.LOADING,
          instances: [],
        },
        {
          type: getInstances.rejected.type,
        }
      )
    ).toEqual({
      instancesStatus: AppConstants.FETCH_STATUS.ERROR,
      instances: [],
    });
  });

  test('showInstanceModal open', () => {
    expect(
      reducer(
        { instanceModalVisible: false },
        {
          type: showInstanceModal.type,
          payload: true,
        }
      )
    ).toEqual({
      instanceModalVisible: true,
      instanceModalEditInstance: null,
    });
  });

  test('showInstanceModal close', () => {
    expect(
      reducer(
        { instanceModalVisible: false },
        {
          type: showInstanceModal.type,
          payload: false,
        }
      )
    ).toEqual({
      instanceModalVisible: false,
      instanceModalEditInstance: null,
    });
  });
});
