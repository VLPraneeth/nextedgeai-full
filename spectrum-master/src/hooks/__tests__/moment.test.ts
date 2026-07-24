import { useUtcTimeInUsersTimezone } from 'hooks/moment';
import * as userSelectorHooks from 'store/user/selector.hooks';
import { renderHook } from 'tests/helpers';

jest.spyOn(userSelectorHooks, 'useUserTimezone').mockImplementation(() => 'US/Central');

describe('useUtcTimeInUsersTimezone', () => {
  test('Converts UTC provided time to local timezone (CDT)', () => {
    const converter = renderHook(() => useUtcTimeInUsersTimezone());

    const result = converter('04/05/2023 04:54:58 PM', 'MM/DD/YYYY hh:mm:ss A');
    expect(result).toBe('4/5/2023 11:54:58 AM CDT');
  });
});
