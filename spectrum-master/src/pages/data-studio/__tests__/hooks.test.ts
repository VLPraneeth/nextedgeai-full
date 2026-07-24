import { encodeFactorId } from 'store/datascore';
import { renderHook } from 'tests/helpers';
import AppConstants from 'utils/AppConstants';

import { useFakeFilterFromDataScoreFactor } from '../hooks';

describe('test useFakeFilterFromDataScoreFactor', () => {
  const fakeEntityId = '123123123123';

  const datascoreState = {
    dataScoreByEntity: {
      [fakeEntityId]: {
        status: 'available',
        // @ts-ignore
        data: {},
      },
    },
    dataScoreErrorByEntity: {
      [fakeEntityId]: null,
    },
    dataScoreStatusByEntity: {
      [fakeEntityId]: AppConstants.FETCH_STATUS.SUCCESS,
    },
  };

  test('test without factorId', async () => {
    const result = renderHook(() => useFakeFilterFromDataScoreFactor(fakeEntityId), {
      testState: {
        // @ts-ignore
        datascore: datascoreState,
      },
    });

    expect(result).toBeUndefined();
  });

  // eslint-disable-next-line jest/no-disabled-tests
  xtest('test with factorId and missing data', async () => {
    const fakeFactorId = 'zxcvzxcvzxcv';

    const result = renderHook(() => useFakeFilterFromDataScoreFactor(fakeEntityId, fakeFactorId), {
      testState: {
        // @ts-ignore
        datascore: datascoreState,
      },
    });

    expect(result).toEqual({
      data: undefined,
      status: AppConstants.FETCH_STATUS.SUCCESS,
    });
  });

  // eslint-disable-next-line jest/no-disabled-tests
  xtest('test with data', () => {
    const fieldName = 'firstName';
    const ruleId = '987987987987';

    const fakeFactorId = encodeFactorId(fakeEntityId, fieldName, ruleId);

    const result = renderHook(() => useFakeFilterFromDataScoreFactor(fakeEntityId, fakeFactorId), {
      testState: {},
    });

    expect(result?.status).toBe('success');
    expect(result?.data?.name).toBe('Rule Label');
    expect(Object.keys(result?.data?.criteria || {})).toEqual(['groupPredicateId', 'operator', 'predicates']);
  });
});
