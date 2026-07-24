import { initialState } from 'store/logs/reducer';
import { mockedAjaxUtils, render } from 'tests/helpers';
import { mockMomentTime } from 'tests/helpers/moment.helper';

import SyncErrors from './SyncErrors';

const AjaxUtil = mockedAjaxUtils();
jest.mock('utils/AjaxUtil');

const nowTimeUtc = '2021-01-01:00:00.000Z';
mockMomentTime(nowTimeUtc);

describe('SyncErrors', () => {
  it('should use the current time when fetching initial errors list', async () => {
    const handler = jest.fn((url) => {
      return Promise.resolve({});
    });

    AjaxUtil.get.mockImplementation(handler);

    render(<SyncErrors />, {
      testState: {
        logs: initialState,
      },
    });

    expect(handler).toHaveBeenCalledWith(expect.stringContaining('2020-12-25T00:00:00/2021-01-01T23:59:59'));
  });
});
