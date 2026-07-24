//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import moment, { Moment } from 'moment';
import * as momentTz from 'moment-timezone';

import { ValuesOf } from './TypeUtils';

// TODO: Rename these variables. The designations as 'short' or 'long'
// don't have consistent meaning. Not all names include same info about
// their stored value (e.g. 12 vs 24 hour time). Lot of variation in time
// formats across the app (24, 12, single or double digit, tz or no tz)
// that could be standardized.
export const SHORT_TIME_12_TZ_FORMAT = 'h:mm A zz';
export const SHORT_DATE_TIME_FORMAT = 'MM/DD/YYYY hh:mm:ss A';
export const SHORT_DATE_24_TIME_FORMAT = 'MM/DD/YYYY HH:mm:ss';
export const SHORT_DATE_24_TIME_TZ_FORMAT = 'MM/DD/YYYY HH:mm:ss zz';
export const SHORT_DATE_DISPLAY_FORMAT = 'M/D/YYYY';
export const SHORT_DATE_TIME_DISPLAY_FORMAT = 'M/D/YYYY h:mm:ss A';
export const SHORT_DATE_TIME_TZ_DISPLAY_FORMAT = 'M/D/YYYY h:mm:ss A z';
export const SHORT_DATE_FORMAT = 'YYYY-MM-DD';
export const SHORT_TIME_FORMAT = 'HH:mm';
export const SHORT_DATE_TIME_FORMAT_NO_SEC = `${SHORT_DATE_FORMAT} ${SHORT_TIME_FORMAT}`;
export const SHORT_DATE_FORMAT_WITH_TIME = SHORT_DATE_FORMAT + ' ' + SHORT_TIME_FORMAT;

export const LONG_DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss';
export const LONG_TIME_FORMAT = 'HH:mm:ss';
export const LONG_DATETIME_FORMAT_WITH_TZ = 'YYYY-MM-DD HH:mm:ss zz';
export const FULL_DATE_TIME = 'YYYY-MM-DDTHH:mm:ss.SSS';
export const SHORT_DATE_TIME_FORMAT_WITH_SEC = `${SHORT_DATE_FORMAT} ${LONG_TIME_FORMAT}`;

export const PARSABLE_DATE_TIME_FORMAT = SHORT_DATE_FORMAT + 'T' + LONG_TIME_FORMAT;

export const START_OF_DAY_SHORT_TIME = '00:00';
export const END_OF_DAY_SHORT_TIME = '23:59';

export const SHORT_DATE_TIME_REGEXP = /[0-9][0-9]\/[0-9][0-9]\/20[0-9][0-9] [0-1][0-9]:[0-9][0-9]:[0-9][0-9] (A|P)M/gm;

// TODO: Retype this. The type definition below computes to
// just 'string' rather than the actual values of the variables
export type DateFormatString =
  | typeof SHORT_TIME_12_TZ_FORMAT
  | typeof SHORT_DATE_TIME_FORMAT
  | typeof SHORT_DATE_24_TIME_FORMAT
  | typeof SHORT_DATE_DISPLAY_FORMAT
  | typeof SHORT_DATE_TIME_DISPLAY_FORMAT
  | typeof SHORT_DATE_TIME_TZ_DISPLAY_FORMAT
  | typeof SHORT_DATE_FORMAT
  | typeof SHORT_TIME_FORMAT
  | typeof SHORT_DATE_FORMAT_WITH_TIME
  | typeof LONG_DATE_FORMAT
  | typeof LONG_TIME_FORMAT
  | typeof LONG_DATETIME_FORMAT_WITH_TZ
  | typeof FULL_DATE_TIME
  | typeof PARSABLE_DATE_TIME_FORMAT
  | typeof START_OF_DAY_SHORT_TIME
  | typeof END_OF_DAY_SHORT_TIME;

/**
 * Different supported format for the functions
 */
export const FORMAT = {
  STRING: 'string',
  MOMENT: 'moment',
  WITH_TIME: 'with-time',
} as const;

export interface MomentDateRange {
  startDate: Moment;
  endDate: Moment;
}

export interface FormattedDateRange {
  startDate: string;
  endDate: string;
}

/**
 * Get the range date starting from today.
 *
 * @param {Number} numberOfDays number of days to substract from the current date. Default 7 days
 * @param {String} format, it could be one of FORMAT.*. Default format is string.
 */
export function getLastNumberOfDays(
  numberOfDays = 7,
  format: ValuesOf<typeof FORMAT> = FORMAT.STRING
): MomentDateRange | FormattedDateRange {
  let startDate = moment().subtract(numberOfDays, 'days');
  let endDate = moment();

  switch (format) {
    case FORMAT.MOMENT:
      return {
        startDate,
        endDate,
      };
    case FORMAT.WITH_TIME:
      return {
        startDate: startDate.startOf('day').format(SHORT_DATE_FORMAT_WITH_TIME),
        endDate: endDate.endOf('day').format(SHORT_DATE_FORMAT_WITH_TIME),
      };
    default:
      return {
        startDate: startDate.format(SHORT_DATE_FORMAT),
        endDate: endDate.format(SHORT_DATE_FORMAT),
      };
  }
}

// These timezones are provided by moment but are not valid on the Java backend.
const INVALID_TIMEZONE_IDS = ['EST', 'HST', 'MST', 'ROC', 'US/Pacific-New'];
export const timeZoneNames = momentTz.tz.names().filter((tz) => !INVALID_TIMEZONE_IDS.includes(tz));

/**
 * Formats the startDate, endDate objects from given params object
 */
export function formatDatesInParams(params: MomentDateRange, format: DateFormatString = PARSABLE_DATE_TIME_FORMAT) {
  const { startDate, endDate } = params;
  const formattedStartDate = moment(startDate).format(format);
  const formattedEndDate = moment(endDate).format(format);

  return {
    ...params,
    startDate: formattedStartDate,
    endDate: formattedEndDate,
  };
}

/**
 * ISODateTimeString
 */
type ISO8601DateTimeString = string;

/**
 * Timestamp in milliseconds
 */
type TimestampMs = number;

/**
 * Helper function to handle some of the usual boilerplate for formatting a date
 *
 * Accepts Moment instance, Date instance, ISO8601 string, or timestamp in Ms
 */
export function format(date: Moment | Date | ISO8601DateTimeString | TimestampMs, formatStr: DateFormatString): string {
  return moment(date).format(formatStr);
}

// Shows the relative date for past 7 days, previous dates show calendar date
export const getRelativeDate = (dateString: string) => {
  if (moment(dateString).isAfter(moment().subtract(7, 'days'))) {
    return moment(dateString).fromNow();
  }
  return moment(dateString).format(SHORT_DATE_DISPLAY_FORMAT);
};

// For Antd date picker
export function disablePastDate(currentDate?: Moment | null) {
  return !!currentDate?.isBefore(moment(), 'day');
}
export function disablePastTime(currentDate?: Moment | null) {
  if (!currentDate) {
    return {
      disabledHours: () => [],
      disabledMinutes: () => [],
      disabledSeconds: () => [],
    };
  }

  const currentDay = currentDate.day();
  let disabledHours: number[] = [];
  if (currentDay === moment().day()) {
    disabledHours = Array.from({ length: moment().hour() }, (_, i) => i);
  }

  const currentHour = currentDate.hour();
  let disabledMinutes: number[] = [];
  if (currentDay === moment().day() && currentHour === moment().hour()) {
    disabledMinutes = Array.from({ length: moment().minute() }, (_, i) => i);
  }

  return {
    disabledHours: () => disabledHours,
    disabledMinutes: () => disabledMinutes,
  };
}

export const serializeMomentFields = <T = Record<string, any>>(record: Partial<MomentDateRange> & T) => {
  const serializedRecord = { ...record } as (FormattedDateRange | MomentDateRange) & T;
  if (record.startDate) {
    if (!record.startDate.toISOString) {
      debugger;
    }
    serializedRecord.startDate = record.startDate.toISOString();
  }
  if (record.endDate) {
    serializedRecord.endDate = record.endDate.toISOString();
  }
  return serializedRecord as FormattedDateRange & T;
};

export const deserializeMomentFields = <T = Record<string, any>>(record: Partial<FormattedDateRange> & T) => {
  const deserializedRecord = { ...record } as (FormattedDateRange | MomentDateRange) & T;
  if (record.startDate) {
    deserializedRecord.startDate = moment(record.startDate);
  }
  if (record.endDate) {
    deserializedRecord.endDate = moment(record.endDate);
  }
  return deserializedRecord as FormattedDateRange & T;
};
