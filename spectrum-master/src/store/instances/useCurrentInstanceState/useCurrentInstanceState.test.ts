import { mockedAjaxUtils, renderHook } from 'tests/helpers';

import { useCurrentInstanceState } from '../useCurrentInstanceState';
import { mockTrialInstanceState } from './mockInstanceState';

const mockedAxios = mockedAjaxUtils();
jest.mock('utils/AjaxUtil');

describe('useCurrentInstanceState', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('when not in a trial instance, returns enhanced instance state with default values, no API call', () => {
    const result = renderHook(useCurrentInstanceState);
    expect(result).toEqual({
      expiryDate: '',
      id: '',
      isTrial: false,
      numberOfRecordsLeft: 0,
      pipelineCount: 0,
      publishLimitExpired: false,
      recordLimit: 0,
      recordLimitExpired: false,
      refresh: expect.any(Function),
      synapseCount: 0,
      trialDaysLeft: 0,
      trialExpired: false,
    });
    expect(mockedAxios.get).not.toHaveBeenCalled();
  });

  it('triggers an API request if no instance state is present and the user is in a trial instance', async () => {
    mockedAxios.get.mockImplementationOnce(() => Promise.resolve({ data: mockTrialInstanceState }));

    renderHook(() => useCurrentInstanceState(), {
      testState: { user: { currentInstanceType: 'trial', currentInstanceNextEdgeId: '1' } },
    });

    expect(mockedAxios.get).toHaveBeenCalledWith('/arcade/api/v1/organization/instanceState/1');
  });
  it('returns an enhanced Instance State from Redux store', () => {
    const result = renderHook(() => useCurrentInstanceState(), {
      testState: {
        user: { currentInstanceType: 'trial', currentInstanceNextEdgeId: '1' },
        instance: { currentInstanceState: mockTrialInstanceState },
      },
    });

    expect(result.isTrial).toEqual(true);
    expect(result.numberOfRecordsLeft).toEqual(mockTrialInstanceState.numberOfRecordsLeft);
    expect(result.recordLimit).toEqual(10000);
    expect(result.recordLimitExpired).toEqual(mockTrialInstanceState.recordLimitExpired);
    expect(result.trialDaysLeft).toEqual(mockTrialInstanceState.trialDaysLeft);
    expect(result.expiryDate).toEqual(mockTrialInstanceState.expiryDate);
    expect(result.trialExpired).toEqual(mockTrialInstanceState.trialExpired);
  });

  it('includes a function to refresh the state', () => {
    mockedAxios.get.mockImplementationOnce(() => Promise.resolve({ data: mockTrialInstanceState }));

    const { refresh } = renderHook(() => useCurrentInstanceState(), {
      testState: { user: { currentInstanceType: 'trial', currentInstanceNextEdgeId: '1' } },
    });

    expect(mockedAxios.get).toHaveBeenCalledTimes(1);
    refresh!();
    expect(mockedAxios.get).toHaveBeenCalledTimes(2);
  });
});
