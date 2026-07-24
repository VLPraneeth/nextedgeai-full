//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { useCallback } from 'react';

import DateRangePicker, { DateRangePickerProps } from 'components/DateRangePicker';
import { useUtcTimeInUsersTimezone } from 'hooks/moment';
import useUserLocalMoment from 'hooks/moment';
import { FULL_DATE_TIME, LONG_DATETIME_FORMAT_WITH_TZ } from 'utils/DateUtil';

export interface DateRangePickerProxyValueProps {
  start: string;
  end: string;
}
export interface DateRangePickerProxyProps
  extends Omit<DateRangePickerProps, 'startDate' | 'endDate' | 'value' | 'onChange'> {
  value: DateRangePickerProxyValueProps;
  onChange: (dates: DateRangePickerProxyValueProps) => void;
}

const DateRangePickerProxy = ({ value, onChange, ...rest }: DateRangePickerProxyProps) => {
  const { start, end } = value || {};
  const utcToLocal = useUtcTimeInUsersTimezone();

  const userMoment = useUserLocalMoment();
  const handleChange = useCallback(
    (start: any, end: any) => {
      return onChange({
        start: userMoment(start).utc().format(FULL_DATE_TIME),
        end: userMoment(end).utc().format(FULL_DATE_TIME),
      });
    },
    [onChange, userMoment]
  );

  return (
    <DateRangePicker
      {...rest}
      showTime
      dateFormat={LONG_DATETIME_FORMAT_WITH_TZ}
      startDate={start ? userMoment(utcToLocal(start)) : undefined}
      endDate={end ? userMoment(utcToLocal(end)) : undefined}
      onChange={handleChange}
    />
  );
};

export default DateRangePickerProxy;
