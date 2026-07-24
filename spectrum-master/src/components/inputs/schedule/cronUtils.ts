import { parseExpression } from 'cron-parser';
import { isValidCron } from 'cron-validator';
import cronstrue from 'cronstrue';
import moment from 'moment-timezone';

import { ScheduleUnit } from './types';

export type CronExpression = ReturnType<typeof parseExpression>;
export type CronDate = ReturnType<CronExpression['next']>;

export const cronToSummaryString = (cronString: string) => {
  return cronstrue.toString(cronString, { verbose: true });
};

export const cronToSimple = (cronString: string | undefined) => {
  if (!cronString) {
    return;
  }

  if (cronString.includes(',')) {
    console.log('Complex cron, unsupported.');
    return;
  }

  if (cronString.includes('@')) {
    console.log('Cron tokens are currently unsupported.');
    // TODO: implement @daily, @weekly, @monthly tokens -> simple
    return;
  }

  const [, minutes, hours, dayOfMonth, months, weekday] = cronString.split(' ');

  if (weekday !== '*') {
    console.log('Weekdays are not supported in simple view.');
    return;
  }

  if (minutes?.includes('*')) {
    // Every X minutes schedule
    const [, interval] = minutes.split('/');

    return {
      unit: ScheduleUnit.MINUTES,
      interval: +interval || 1,
    };
  } else if (hours?.includes('*')) {
    // Every X hours at Y minutes
    const [, interval] = hours.split('/');

    return {
      unit: ScheduleUnit.HOURS,
      interval: +interval || 1,
      minutes: +minutes,
    };
  } else if (dayOfMonth?.includes('*')) {
    const [, interval] = dayOfMonth.split('/');

    return {
      unit: ScheduleUnit.DAYS,
      interval: +interval || 1,
      hours: +hours,
      minutes: +minutes,
    };
  } else if (months?.includes('*')) {
    const [, interval] = months.split('/');

    return {
      unit: ScheduleUnit.MONTHS,
      interval: +interval || 1,
      dayOfMonth: +dayOfMonth,
      hours: +hours,
      minutes: +minutes,
    };
  }
};

// returns * or */N for a given interval number for use in cron format
export const makeIntervalString = (interval: number) => (interval === 1 ? '*' : `*/${interval}`);

/**
 * Make the nextExecution time in UTC. Note: Next execution's
 * timezone is ignored since internally it uses the browser's timezone
 * and the cron string doesn't have a timezone information.
 *
 * @param nextExecution CronDate of the next execution
 * @returns Moment in UTC
 */
export const getNextExecutionUtc = (nextExecution: CronDate) => {
  const utc = moment().utc();
  utc.hours(nextExecution.getHours());
  utc.minutes(nextExecution.getMinutes());
  utc.seconds(nextExecution.getSeconds());
  utc.day(nextExecution.getDay());
  utc.month(nextExecution.getMonth());
  utc.year(nextExecution.getFullYear());
  return utc;
};

export { isValidCron, parseExpression };
