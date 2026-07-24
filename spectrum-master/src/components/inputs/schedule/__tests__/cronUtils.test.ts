import { DefaultSchedule } from '../constants';
import { cronToSimple, makeIntervalString } from '../cronUtils';
import { ScheduleUnit } from '../types';

describe('cronToSimple parsing', () => {
  test('default schedule', () => {
    expect({
      unit: ScheduleUnit.MINUTES,
      interval: 1,
    }).toEqual(cronToSimple(DefaultSchedule));
  });

  test('every x minutes', () => {
    expect({
      unit: ScheduleUnit.MINUTES,
      interval: 15,
    }).toEqual(cronToSimple('* */15 * * * *'));
    expect({
      unit: ScheduleUnit.MINUTES,
      interval: 2,
    }).toEqual(cronToSimple('* */2 * * * *'));
  });

  test('every x hours', () => {
    expect({
      unit: ScheduleUnit.HOURS,
      interval: 2,
      minutes: 15,
    }).toEqual(cronToSimple('* 15 */2 * * *'));
    expect({
      unit: ScheduleUnit.HOURS,
      interval: 6,
      minutes: 0,
    }).toEqual(cronToSimple('* 0 */6 * * *'));
  });

  test('every x days', () => {
    expect({
      unit: ScheduleUnit.DAYS,
      interval: 5,
      hours: 0,
      minutes: 15,
    }).toEqual(cronToSimple('* 15 0 */5 * *'));
  });

  test('every x months', () => {
    expect({
      unit: ScheduleUnit.MONTHS,
      interval: 3,
      dayOfMonth: 1,
      hours: 12,
      minutes: 30,
    }).toEqual(cronToSimple('* 30 12 1 */3 *'));
  });

  test('every monday (weekdays not suppported)', () => {
    expect(cronToSimple('0 0 16 * * 1')).toBeUndefined();
  });

  test('handle missing value', () => {
    expect(cronToSimple('')).toBeUndefined();
    expect(cronToSimple(undefined)).toBeUndefined();
  });
});

test('makeIntervalString', () => {
  expect(makeIntervalString(1)).toBe('*');
  expect(makeIntervalString(2)).toBe('*/2');
});
