//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { format, PARSABLE_DATE_TIME_FORMAT, SHORT_DATE_TIME_DISPLAY_FORMAT } from 'utils/DateUtil';

// See https://syncari.atlassian.net/wiki/spaces/UX/pages/1081245742/Data+Formats
// for more information
describe('dateUtil format()', () => {
  it('SHORT_DATE_TIME_DISPLAY_FORMAT', () => {
    const result = format('2021-09-03T04:08:06', SHORT_DATE_TIME_DISPLAY_FORMAT);
    expect(result).toBe('9/3/2021 4:08:06 AM');
  });
  it('PARSABLE_DATE_TIME_FORMAT', () => {
    const result = format('09/03/2021 04:08:06 AM', PARSABLE_DATE_TIME_FORMAT);
    expect(result).toBe('2021-09-03T04:08:06');
  });
});
