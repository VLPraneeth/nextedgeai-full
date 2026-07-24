import { DeepPartial as ReduxDeepPartial } from 'redux';

// turns an object's keys into a union type
export type KeysOf<T> = keyof T;

// turns an object's values into a union type
export type ValuesOf<T> = T[keyof T];

/*
 * turns a constant array of strings into a type union,
 *
 * @example
 * const httpMethods = ["GET", "POST", "PUT", "PATCH", "OPTIONS", "HEAD", "DELETE"] as const; // note: `as const` is required
 * type HttpMethod = ArrayToUnion<typeof httpMethods>;
 * // => "GET" | "POST" | "PUT" | "PATCH" | "OPTIONS" | "HEAD" | "DELETE"
 *
 */
export type ArrayToUnion<T extends readonly string[]> = T[number];

// given an array type, return the typeof a single element
export type ArrayElement<A> = A extends readonly (infer T)[] ? T : never;

export type DeepPartial<T> = ReduxDeepPartial<T>;

// guard for undefined
export const isNotUndefined = <T extends unknown>(variableToCheck: T | undefined): variableToCheck is T =>
  typeof variableToCheck !== 'undefined';

// guard for null
export const isNotNull = <T extends unknown>(variableToCheck: T | null): variableToCheck is T =>
  variableToCheck !== null;

// guard for null/undefined
export const isNotNullOrUndefined = <T extends unknown>(variableToCheck: T | null | undefined): variableToCheck is T =>
  isNotNull(variableToCheck) && isNotUndefined(variableToCheck);

// basic guard for some primitive types
export const isPrimitive = (variableToCheck: any): variableToCheck is string | number | boolean => {
  return (
    typeof variableToCheck === 'boolean' || typeof variableToCheck === 'string' || typeof variableToCheck === 'number'
  );
};

// basic guard for React change event
export const isReactChangeEvent = (variableToCheck: any): variableToCheck is React.ChangeEvent => {
  return typeof variableToCheck.target !== 'undefined' && typeof variableToCheck.persist === 'function';
};

// Use this if you want TypeScript to verify that a switch statement
// exhaustively covers all branches of e.g. an enum type
// Lifted from https://stackoverflow.com/a/52913382/74152
export class UnreachableCaseError extends Error {
  constructor(val: never) {
    super(`Unreachable case: ${val}`);
  }
}

// returns property value from object O given property path T, otherwise never
export type GetDictValue<T extends string, O> = T extends `${infer A}.${infer B}`
  ? A extends keyof O
    ? GetDictValue<B, O[A]>
    : never
  : T extends keyof O
  ? O[T]
  : never;

// T is the dictionary, S is the next string part of the object property path
// If S does not match dict shape, return its next expected properties
export type DeepKeys<T, S extends string = string> = T extends object
  ? S extends `${infer I1}.${infer I2}`
    ? I1 extends keyof T
      ? `${I1}.${DeepKeys<T[I1], I2>}`
      : keyof T & string
    : S extends object
    ? `${S}`
    : keyof T & string
  : '';

// helper for joining path components
type Join<K, P> = K extends string | number
  ? P extends string | number
    ? `${K}${'' extends P ? '' : '.'}${P}`
    : never
  : never;

// helper for managing depth
type Prev = [never, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, ...0[]];

// Will Calculate key paths for items that are objects
export type ObjectPaths<T, D extends number = 10> = [D] extends [never]
  ? never
  : T extends object
  ? {
      [K in keyof T]-?: K extends string | number
        ? T[K] extends object
          ? `${K}` | Join<K, ObjectPaths<T[K], Prev[D]>>
          : never
        : never;
    }[keyof T]
  : '';

// Will Calculate key paths for all nested items
export type Paths<T, D extends number = 10> = [D] extends [never]
  ? never
  : T extends object
  ? {
      [K in keyof T]-?: K extends string | number
        ? T[K] extends object
          ? `${K}` | Join<K, Paths<T[K], Prev[D]>>
          : never
        : never;
    }[keyof T]
  : '';

export type Leaves<T, D extends number = 10> = [D] extends [never]
  ? never
  : T extends object
  ? { [K in keyof T]-?: Join<K, Leaves<T[K], Prev[D]>> }[keyof T]
  : '';

/**
 * Extends all properties of a type as optional except for those specified
 */
export type OptionalExceptFor<T, TRequired extends keyof T> = Partial<T> & Pick<T, TRequired>;
