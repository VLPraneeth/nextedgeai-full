import DatePicker from 'antd/lib/date-picker';
import { DatePickerProps } from 'antd/lib/date-picker/interface';
import { TimePickerProps } from 'antd/lib/time-picker';
import { Moment } from 'moment';
import { useMemo } from 'react';

import { SHORT_DATE_DISPLAY_FORMAT } from 'utils/DateUtil';
import { tc } from 'utils/i18nUtil';

import './DateRangePicker.less';

export interface DateRangePickerProps extends Omit<DatePickerProps, 'onChange' | 'showTime'> {
  startDate?: Moment;
  endDate?: Moment;
  dateFormat?: string;
  onChange: (startDate: Moment | undefined, endDate: Moment | undefined) => void;
  commonTimeConfig?: Omit<TimePickerProps, 'defaultValue'>;
  startTimeConfig?: TimePickerProps;
  endTimeConfig?: TimePickerProps;

  /* this overrides the antd interface which allows boolean | config.
   * we have broken this out to be more explicit. This flag is will now tell our
   * date pickers to toggle on the time picker and is required if you want
   * to show the time picker
   */
  showTime?: boolean;
}

const notBefore = (baseDate?: Moment) => (date: Moment | null) => (baseDate && date ? date.isBefore(baseDate) : false);
const notAfter = (baseDate?: Moment) => (date: Moment | null) => (baseDate && date ? date.isAfter(baseDate) : false);

const DateRangePicker = ({
  dateFormat = SHORT_DATE_DISPLAY_FORMAT,
  startDate,
  endDate,
  onChange,
  showTime,
  commonTimeConfig,
  startTimeConfig,
  endTimeConfig,
  ...pickerProps
}: DateRangePickerProps) => {
  const handleDateChange = (fieldKey: string) => (value: Moment | null) => {
    const _value = value === null ? undefined : value;

    if (fieldKey === 'startDate') {
      onChange(_value, endDate);
    } else {
      onChange(startDate, _value);
    }
  };

  const startShowTime = useMemo(() => {
    // don't show time picker if flag isn't set
    if (!showTime) {
      return undefined;
    }

    // if there's no config, just use the boolean flag for antd default
    if (!(commonTimeConfig || startTimeConfig)) {
      return true;
    }

    return {
      ...commonTimeConfig,
      ...startTimeConfig,
    };
  }, [showTime, commonTimeConfig, startTimeConfig]);

  const endShowTime = useMemo(() => {
    // don't show time picker if flag isn't set
    if (!showTime) {
      return undefined;
    }

    // if there's no config, just use the boolean flag for antd default
    if (!(commonTimeConfig || endTimeConfig)) {
      return true;
    }

    return {
      ...commonTimeConfig,
      ...endTimeConfig,
    };
  }, [showTime, commonTimeConfig, endTimeConfig]);

  return (
    <div className="synri-date-range-picker">
      <DatePicker
        className="synri-date-range-start"
        format={dateFormat}
        value={startDate}
        onChange={handleDateChange('startDate')}
        disabledDate={notAfter(endDate)}
        showTime={startShowTime}
        {...pickerProps}
      />
      <span className="synri-date-range-token">{tc('to')}</span>
      <DatePicker
        className="synri-date-range-end"
        format={dateFormat}
        value={endDate}
        onChange={handleDateChange('endDate')}
        disabledDate={notBefore(startDate)}
        showTime={endShowTime}
        {...pickerProps}
      />
    </div>
  );
};

export default DateRangePicker;
