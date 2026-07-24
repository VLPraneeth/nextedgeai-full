import moment, { Moment } from 'moment-timezone';

import { compose } from 'utils/Fp';
import { toLowerCase } from 'utils/Fp';

type Predicate = (value: any) => boolean;
type CurriedPredicate = (...args: any[]) => Predicate;

const lazyTrue = () => true;

const isEmpty = (item: any) => (Array.isArray(item) ? !!item.length : Boolean(item));

// wrapper to skip the predicate if the predicate arguments are falsey.
// This lets us use all predicates without conditions
// and values will fall through predicates that aren't in use
//
// safePredicate :: (a -> Boolean) -> b -> a -> Boolean
export const safePredicate = <T extends CurriedPredicate, V = Parameters<ReturnType<T>>[0]>(predicateFn: T) => (
  ...funcArgs: (any | null)[]
) => (value: V): boolean => (funcArgs.every(isEmpty) ? predicateFn(...funcArgs)(value) : lazyTrue());

// returns true if ALL predicates pass
export const all = <T>(...predicateFns: Predicate[]) => (x: T) => predicateFns.every((predicate) => predicate(x));

// returns true if ANY predicates pass
export const some = <T>(...predicateFns: Predicate[]) => (x: T) => predicateFns.some((predicate) => predicate(x));

// access prop from object
// prop :: string -> a -> b
export const prop = (key: string) => (haystack: Record<string, any>) => haystack[key];

// case-insensitive includes
// includes :: [String] -> String -> Boolean
export const includes = (haystack: string[]) => (needle: string) =>
  haystack.map(toLowerCase).includes(toLowerCase(needle));

// case-insensitive startsWith
// startsWith :: String -> String -> Boolean
export const startsWith = (needle: string) => (haystack: string) =>
  haystack.toLowerCase().startsWith(needle.toLowerCase());

// case-insensitive contains
// contains :: String -> String -> Boolean
export const contains = (needle: string) => (haystack: string) => haystack.toLowerCase().includes(needle.toLowerCase());

export const isBetween = (startDate: Moment, endDate: Moment) => (date: Moment | string | null): boolean => {
  const _date = typeof date === 'string' ? moment(date, moment.ISO_8601) : date;
  return _date ? _date.isBetween(startDate, endDate) : false;
};

// safe wrappers on predicates
export const safeIsBetween = safePredicate(isBetween);
export const safeStartsWith = safePredicate(startsWith);
export const safeContains = safePredicate(contains);
export const safeIncludes = safePredicate(includes);

export const stringPropContains = (propName: string) => (needle?: string) =>
  compose(safeContains(needle), prop(propName));

// Like stringPropContains but with multiple prop options. Return true if any
// prop matches
export const stringPropsContains = (propNames: string[]) => (needle?: string) =>
  some(...propNames.map((propName) => compose(safeContains(needle), prop(propName))));

export const datePropIsBetween = (propName: string) => (startDate: Moment, endDate: Moment) =>
  compose(safeIsBetween(startDate, endDate), prop(propName));

export const stringPropIsIn = (propName: string) => (haystack: string[]) =>
  compose(safeIncludes(haystack), prop(propName));
