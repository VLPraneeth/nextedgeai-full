import Input from 'antd/lib/input';
import TimePicker from 'antd/lib/time-picker';
import { Moment } from 'moment';
import { useEffect } from 'react';

import { HStack } from 'components/layout';
import SelectInput from 'components/SelectInput';
import { tNamespaced } from 'utils/i18nUtil';

import { TimeFormat } from './constants';
import { SimpleScheduleComponents, ScheduleUnit } from './types';

const tn = tNamespaced('ScheduleInput');

export interface UnitInputProps {
  nextExecution: Moment | undefined;
  onChange: (components: SimpleScheduleComponents) => void;
  readOnly: boolean;
}

export interface CronScheduleInputProps {
  onChange: (cronString: string) => void;
  value: string;
  readOnly?: boolean;
}

export const CronScheduleInput = ({ onChange, readOnly, value }: CronScheduleInputProps) => {
  return (
    <Input
      onChange={(evt) => {
        if (evt?.target?.value) {
          onChange(evt.target.value);
        }
      }}
      value={value}
      readOnly={readOnly}
    />
  );
};

export const HoursInput = ({ nextExecution, onChange, readOnly }: UnitInputProps) => {
  const minutes = nextExecution?.minutes().toString() || '1';

  const handleChange = (m: string = minutes) => {
    onChange({
      minutes: m,
    });
  };

  useEffect(() => {
    handleChange(minutes);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <HStack>
      <span className="inline-label">{tn('at')}</span>
      <SelectInput
        options={Array.from({ length: 60 }, (_, i) => ({
          label: i.toString(),
          value: i.toString(),
        }))}
        value={minutes}
        onChange={handleChange}
        disabled={readOnly}
        data-testid="hour-minutes-select"
      />
      <span className="inline-label lower">{tn('unit.minutes', { count: Number(minutes) })}</span>
    </HStack>
  );
};

export const DaysInput = ({ nextExecution, onChange, readOnly }: UnitInputProps) => {
  const handleChange = (moment?: Moment) => {
    if (!moment) {
      return;
    }
    const newHours = moment.hours().toString();
    const newMinutes = moment.minutes().toString();
    onChange({ hours: newHours, minutes: newMinutes });
  };

  useEffect(() => {
    onChange({
      hours: nextExecution?.hours().toString() ?? '12',
      minutes: nextExecution?.minutes().toString() ?? '0',
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <HStack>
      <span className="inline-label">{tn('at')}</span>
      <TimePicker
        use12Hours
        format={TimeFormat}
        value={nextExecution}
        onChange={handleChange}
        disabled={readOnly}
        allowClear={false}
      />
      <span>UTC</span>
    </HStack>
  );
};

export const MonthsInput = ({ nextExecution, onChange, readOnly }: UnitInputProps) => {
  const dayOfMonth = nextExecution?.date().toString() || '1';
  const hours = nextExecution?.hours().toString() ?? '12';
  const minutes = nextExecution?.minutes().toString() ?? '0';

  const setDayOfMonth = (day: string) => {
    onChange({
      dayOfMonth: day,
      hours,
      minutes,
    });
  };

  const setTime = (moment?: Moment) => {
    if (!moment) {
      return;
    }
    const newHours = moment.hours().toString();
    const newMinutes = moment.minutes().toString();
    onChange({ hours: newHours, minutes: newMinutes, dayOfMonth });
  };

  useEffect(() => {
    onChange({ hours, minutes, dayOfMonth });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <HStack>
      <span className="inline-label">{tn('on')}</span>
      <SelectInput
        onChange={setDayOfMonth}
        options={Array.from({ length: 31 }, (_, i) => ({
          label: (i + 1).toString(),
          value: (i + 1).toString(),
        }))}
        value={dayOfMonth}
        disabled={readOnly}
        style={{ minWidth: '5rem' }}
        data-testid="day-select"
      />
      <span className="inline-label">{tn('day') + ' ' + tn('at')}</span>
      <TimePicker
        use12Hours
        format={TimeFormat}
        onChange={setTime}
        value={nextExecution}
        disabled={readOnly}
        allowClear={false}
      />
      <span>UTC</span>
    </HStack>
  );
};

export const UnitInputs = ({ unit, ...props }: UnitInputProps & { unit: ScheduleUnit }) => {
  switch (unit) {
    case ScheduleUnit.MONTHS:
      return <MonthsInput {...props} />;
    case ScheduleUnit.DAYS:
      return <DaysInput {...props} />;
    case ScheduleUnit.HOURS:
      return <HoursInput {...props} />;
    case ScheduleUnit.MINUTES:
    default:
      return null;
  }
};
