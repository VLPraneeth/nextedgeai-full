import { DatePicker, TimePicker } from 'antd';
import cx from 'classnames';
import moment, { Moment } from 'moment-timezone';
import { ReactElement } from 'react';

import { SHORT_DATE_DISPLAY_FORMAT, SHORT_TIME_12_TZ_FORMAT } from 'utils/DateUtil';
import './DateTimeSelector.less';

export interface DateTimeSelectorProps {
  prefix?: ReactElement | string;
  onChange?: (date: Moment) => void;
  value?: Moment;
  disabledDatePredicate: (date: Moment | null) => boolean;
  hasError: boolean | null;
}
const DateTimeSelector = ({
  prefix,
  onChange,
  value,
  disabledDatePredicate,
  hasError = false,
}: DateTimeSelectorProps) => {
  const handleChange = ({ date: newDate, time: newTime }: Record<string, Moment | null>) => {
    const date = newDate || value || moment();
    const { hours, minutes, seconds } = (newTime || date).toObject();

    onChange?.(moment(date).set({ hours, minutes, seconds }));
  };

  return (
    <div className={cx('date-time-selector', hasError && 'has-error')}>
      {prefix && <span className="date-prefix">{prefix}</span>}
      <DatePicker
        allowClear={false}
        className={cx('date-picker')}
        disabledDate={disabledDatePredicate}
        format={SHORT_DATE_DISPLAY_FORMAT}
        onChange={(date: Moment | null, dateString: string) => {
          handleChange({ date });
        }}
        showTime={false}
        value={value}
      />
      <TimePicker
        allowClear={false}
        className={cx('time-picker')}
        use12Hours
        format={SHORT_TIME_12_TZ_FORMAT}
        onChange={(time: Moment | null) => handleChange({ time })}
        value={value}
      />
    </div>
  );
};

export default DateTimeSelector;
