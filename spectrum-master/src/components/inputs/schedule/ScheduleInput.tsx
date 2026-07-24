import Form from 'antd/lib/form';
import Input from 'antd/lib/input';
import Modal from 'antd/lib/modal';
import Switch from 'antd/lib/switch';
import moment, { Moment } from 'moment-timezone';
import { useCallback, useEffect, useState } from 'react';

import Button from 'components/Button';
import { HStack } from 'components/layout';
import SelectInput from 'components/SelectInput';
import useUserLocalMoment from 'hooks/moment';
import useInterval from 'hooks/useInterval';
import usePreviousValue from 'hooks/usePreviousValue';
import { SHORT_DATE_TIME_TZ_DISPLAY_FORMAT } from 'utils/DateUtil';
import { tNamespaced } from 'utils/i18nUtil';

import { DefaultSchedule } from './constants';
import { cronToSimple, cronToSummaryString, isValidCron, makeIntervalString, parseExpression } from './cronUtils';
import { CronScheduleInput, UnitInputs } from './inputs';
import { ScheduleUnit, SimpleScheduleComponents } from './types';

import './ScheduleInput.less';

const tn = tNamespaced('ScheduleInput');
const tenSeconds = 10 * 1000;

const CRON_EVERY = '*';

const LAST_DAY_MONTH = 31;

export enum ScheduleMode {
  SIMPLE,
  ADVANCED,
}

export interface SimpleScheduleInputProps {
  initialUnit?: ScheduleUnit;
  initialInterval?: number;
  nextExecution: Moment | undefined;
  onChange: (cronString: string) => void;
  readOnly: boolean;
}

const SimpleScheduleInput = ({
  nextExecution,
  onChange,
  readOnly,
  initialInterval = 1,
  initialUnit = ScheduleUnit.MINUTES,
}: SimpleScheduleInputProps) => {
  const [interval, setInterval] = useState(initialInterval);
  const [unit, setUnit] = useState(initialUnit);
  const [scheduleComponents, setScheduleComponents] = useState<SimpleScheduleComponents>(() => ({}));

  useEffect(() => {
    const { dayOfMonth = CRON_EVERY } = scheduleComponents;
    let { minutes, hours } = scheduleComponents;

    minutes = minutes || CRON_EVERY;
    hours = hours || CRON_EVERY;

    switch (unit) {
      case ScheduleUnit.MINUTES:
        onChange(`0 ${makeIntervalString(interval)} ${CRON_EVERY} ${CRON_EVERY} ${CRON_EVERY} ${CRON_EVERY}`);
        break;
      case ScheduleUnit.HOURS:
        onChange(`0 ${minutes} ${makeIntervalString(interval)} ${CRON_EVERY} ${CRON_EVERY} ${CRON_EVERY}`);
        break;
      case ScheduleUnit.DAYS:
        onChange(`0 ${minutes} ${hours} ${makeIntervalString(interval)} ${CRON_EVERY} ${CRON_EVERY}`);
        break;
      case ScheduleUnit.MONTHS:
        onChange(`0 ${minutes} ${hours} ${dayOfMonth} ${makeIntervalString(interval)} ${CRON_EVERY}`);
        break;
    }
  }, [interval, unit, scheduleComponents, onChange]);

  return (
    <HStack>
      <span>{tn('every')}</span>
      <Input
        type="number"
        name="interval"
        style={{ width: '5rem' }}
        min="1"
        onChange={(evt) => {
          const value = +(evt.target.value || 1);
          if (value < 1) {
            setInterval(1);
          } else if (unit === ScheduleUnit.DAYS && value > LAST_DAY_MONTH) {
            setInterval(31);
          } else {
            setInterval(value);
          }
        }}
        value={interval}
        readOnly={readOnly}
      />
      <SelectInput
        id="unit"
        options={Object.values(ScheduleUnit).map((value) => ({
          label: tn(`unit.${value}`, { count: interval }),
          value,
        }))}
        onChange={(val) => setUnit(val as ScheduleUnit)}
        value={unit}
        disabled={readOnly}
      />
      <UnitInputs unit={unit} onChange={setScheduleComponents} nextExecution={nextExecution} readOnly={readOnly} />
    </HStack>
  );
};

export interface ScheduleInputProps {
  id?: string;
  name?: string;
  displayMode?: string;
  readOnly?: boolean;
  hideSummary?: boolean;
  value?: string;
  onChange: (evt: React.ChangeEvent<HTMLInputElement>) => void;
  setHasError?: (hasError: boolean) => void;
}

const ScheduleInput = ({
  name,
  id,
  displayMode,
  readOnly = false,
  hideSummary = false,
  onChange,
  value: initialValue,
  setHasError,
}: ScheduleInputProps) => {
  // try convert cron string to simple schedule
  const simpleSchedule = cronToSimple(initialValue);

  const [enabled, setEnabled] = useState(() => Boolean(initialValue));
  const [currentDate, setCurrentDate] = useState(() => new Date());

  // if we don't have any cronstring, or it's the default string, or if
  // we were able to convert to simple, set to SIMPLE
  const [mode, setMode] = useState(() =>
    !initialValue || initialValue === DefaultSchedule || simpleSchedule ? ScheduleMode.SIMPLE : ScheduleMode.ADVANCED
  );
  const [value, setValue] = useState(() => initialValue || DefaultSchedule);
  const previousValue = usePreviousValue(value);

  // this clock will update our "next execution time" label to be up-to-date while the
  // user is working on the schedule
  useInterval(() => {
    setCurrentDate(new Date());
  }, tenSeconds);

  const onChangeHandler = useCallback(
    (cronString: string) => {
      const fakeEvent = {
        target: {
          name,
          id,
          value: cronString,
        },
      } as React.ChangeEvent<HTMLInputElement>;

      onChange?.(fakeEvent);
    },
    [id, name, onChange]
  );

  useEffect(() => {
    const isFirstRender = typeof previousValue === 'undefined';
    // If we call the onChangeHandler on the initial render we'll incorrectly
    // set the schedule value to the DefaultSchedule
    if (!isFirstRender && previousValue !== value) {
      onChangeHandler(value);
    }
  }, [onChangeHandler, previousValue, value]);

  const scheduleIsValid = isValidCron(value, { seconds: true });

  useEffect(() => {
    setHasError?.(!isValidCron(value, { seconds: true }));
  }, [setHasError, value]);

  let iterator;
  try {
    iterator = parseExpression(value, { currentDate, utc: true });
  } catch (error) {}
  const next = iterator?.next();
  const nextExecution = moment.utc(next?.toString());
  // Create separate moment for local time so we retain UTC time in 'nextExecution'
  const nextExecutionLocal = useUserLocalMoment()(next?.toString());

  const getScheduleSummary = () => (
    <div className="synri-help-text">
      {tn('cron_summary', { summary: cronToSummaryString(value) })}
      <div>
        {tn('next_sync_utc')} {nextExecution.format(SHORT_DATE_TIME_TZ_DISPLAY_FORMAT)}
      </div>
      <div>
        {tn('next_sync_local')} {nextExecutionLocal.format(SHORT_DATE_TIME_TZ_DISPLAY_FORMAT)}
      </div>
    </div>
  );

  if (displayMode === 'readonly' || (readOnly && !hideSummary)) {
    return getScheduleSummary();
  }

  return (
    <>
      <Form.Item
        key={enabled ? 1 : 0}
        validateStatus={scheduleIsValid ? 'success' : 'error'}
        help={
          !enabled ? null : scheduleIsValid && !hideSummary ? (
            getScheduleSummary()
          ) : (
            <div className="synri-error-text">{tn('invalid_schedule')}</div>
          )
        }>
        <label className="scheduler-input">
          <HStack>
            <div>{tn('enabled')}</div>
            <Switch
              checked={enabled}
              size="small"
              onChange={(flag) => {
                setEnabled((prev) => !prev);

                // disabling, clear value
                onChangeHandler(!flag ? '' : value);
              }}
            />
          </HStack>
        </label>
        {enabled && (
          <>
            {mode === ScheduleMode.ADVANCED ? (
              <CronScheduleInput readOnly={readOnly} value={value} onChange={setValue} />
            ) : (
              <SimpleScheduleInput
                initialUnit={simpleSchedule?.unit}
                initialInterval={simpleSchedule?.interval}
                readOnly={readOnly}
                nextExecution={nextExecution}
                onChange={setValue}
              />
            )}
          </>
        )}
      </Form.Item>
      {enabled && !readOnly && (
        <Button
          type="link"
          style={{
            padding: 0,
            margin: 0,
          }}
          onClick={() => {
            // currently advanced, switching to basic
            if (mode === ScheduleMode.ADVANCED) {
              Modal.confirm({
                title: tn('switch_to_simple_title'),
                content: tn('switch_to_simple_body'),
                onOk: () => setMode(ScheduleMode.SIMPLE),
                onCancel: () => Promise.resolve(),
              });
            } else {
              Modal.confirm({
                title: tn('switch_to_cron_title'),
                content: tn('switch_to_cron_body'),
                onOk: () => setMode(ScheduleMode.ADVANCED),
                onCancel: () => Promise.resolve(),
              });
            }
          }}>
          {mode === ScheduleMode.SIMPLE ? tn('switch_to_cron_btn') : tn('switch_to_simple_btn')}
        </Button>
      )}
    </>
  );
};

export default ScheduleInput;
