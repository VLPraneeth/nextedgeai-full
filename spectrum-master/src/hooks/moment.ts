import moment from 'moment-timezone';
import { useCallback, useMemo } from 'react';

import { useUserTimezone } from 'store/user/selector.hooks';
import { SHORT_DATE_TIME_TZ_DISPLAY_FORMAT } from 'utils/DateUtil';

/**
 * Utility hook to for a moment constructor with default timezone set to
 * the user's timezone from profile, or guessed from browser info when unset.
 *
 * @returns moment constructor function
 */
const useUserLocalMoment = () => {
  let userTimezone = useUserTimezone();

  return useMemo(() => {
    if (userTimezone) {
      // Assign the timezone from user's profile
      moment.tz.setDefault(userTimezone);
    } else {
      // If user has no timezone set, guess the user's timezone
      moment.tz.setDefault(moment.tz.guess());
    }

    return moment;
  }, [userTimezone]);
};

export const useUtcTimeInUsersTimezone = (format: string = SHORT_DATE_TIME_TZ_DISPLAY_FORMAT) => {
  const userTimezone = useUserTimezone();

  return useCallback(
    (utcTime?: string | null, stringFormat?: string) => {
      if (!utcTime) {
        return '-';
      }
      if (userTimezone) {
        return moment.utc(utcTime, stringFormat).tz(userTimezone).format(format);
      }
      return moment.utc(utcTime, stringFormat).format(format);
    },
    [format, userTimezone]
  );
};

export default useUserLocalMoment;
export { ISO_8601, Moment } from 'moment-timezone';
