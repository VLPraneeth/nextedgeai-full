import moment from 'moment';

import { PARSABLE_DATE_TIME_FORMAT } from 'utils/DateUtil';

export const mockMomentTime = (dateTime = '2021-01-01:00:00.000Z') => {
  (Date as any).now = jest.fn(() => new Date(dateTime));
  return moment(dateTime).format(PARSABLE_DATE_TIME_FORMAT);
};
