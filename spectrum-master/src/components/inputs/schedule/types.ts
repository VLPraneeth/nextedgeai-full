export enum DayOfWeek {
  MONDAY = 'monday',
  TUESDAY = 'tuesday',
  WEDNESDAY = 'wednesday',
  THURSDAY = 'thursday',
  FRIDAY = 'friday',
  SATURDAY = 'saturday',
  SUNDAY = 'sunday',
}

export enum ScheduleUnit {
  MINUTES = 'minutes',
  HOURS = 'hours',
  DAYS = 'days',
  // WEEKS = 'weeks',
  MONTHS = 'months',
}

export type SimpleScheduleComponents = {
  minutes?: string;
  hours?: string;
  dayOfMonth?: string;
  months?: string;
  dayOfWeek?: string;
};
