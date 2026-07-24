import { EMPTY_ARRAY } from 'store/constants';

import { isNotNullOrUndefined } from './TypeUtils';

/** immutably moves an item around in an array */
export const moveItem = <T extends any = string>(xs: T[], sourceIdx: number, destinationIdx: number) => {
  const clone = [...xs];
  clone.splice(destinationIdx, 0, ...clone.splice(sourceIdx, 1));
  return clone;
};

/** immutable replacement */
export const replaceItem = <T extends any = string>(xs: T[], index: number, item: T) => {
  const firstSlice = xs.slice(0, index);
  const lastSlice = xs.length > index + 1 ? xs.slice(index + 1) : [];

  return [...firstSlice, item, ...lastSlice];
};

/** immutably sorts an array */
type CompareFn<T extends any> = (firstEl: T, secondEl: T) => number;

export const sort = <T extends any = any>(xs: T[], compareFn?: CompareFn<T>): T[] => {
  return [...xs].sort(compareFn);
};

type Iterableify<T> = { [K in keyof T]: Iterable<T[K]> };

/**
 * generator function that zips togther variadic arrays. This will exhaust all arrays
 * to their termination and any shorter arrays will be filled with `undefined`.
 *
 * @example
 * // all arrays are the same length
 * const ids = getIdsFromServer(); // [1, 2, …, n]
 * const metadata = getMetadataForIds(ids); // [{ id: 1, name: "First" }, { id: 2, name: "Second" }, …, { id: n, name: "N" }]
 * const blobs = getBlobsForIds(ids); // [ Blob1, Blob2, …, BlobN]
 *
 * for(const [i, meta, file] of zipper(ids, metadata, blobs)) {
 *   console.log(i, meta, file);
 * }
 *
 * // Loop 1 => (1, { id: 1, name: "First" }, Blob1)
 * // Loop 2 => (2, { id: 2, name: "Second" }, Blob2)
 * // …
 * // Loop N => (N, { id: N, name: "N" }, BlobN)
 *
 * @example
 * // with different length arrays
 * const ids = getIdsFromServer(); // [1, 2, …, n]
 * const metadata = getMetadataForIds(ids); // [{ id: 1, name: "First" }, { id: 2, name: "Second" }, …, { id: n, name: "N" }]
 * const blobs = getBlobsForIds(ids); // [ Blob1, Blob2, …, BlobN-1]
 *
 * for(const [i, meta, file] of zipper(ids, metadata, blobs)) {
 *   console.log(i, meta, file);
 *   // Loop 1 same as above. => (1, { id: 1, name: "First" }, Blob1)
 *   // Loop 2 same as above. => (2, { id: 2, name: "Second" }, Blob2)
 *   // …
 *   // Loop N                => (N, { id: N, name: "N" }, undefined)
 * }
 */
export function* zipper<T extends unknown[]>(...toZip: Iterableify<T>[]) {
  const iterators = toZip.map((i) => i[Symbol.iterator]());

  while (true) {
    const results = iterators.map((i) => i.next());

    if (results.every(({ done }) => done)) {
      break;
    }

    yield results.map(({ value }) => value || undefined);
  }
}

// exhausts an iterable, filing a new Array. Useful for converting a generator into an Array
export const exhaustIterable = <T extends unknown>(iterable: Iterable<T>) => {
  return Array.from(iterable);
};

// Remove null or undefined in the array with type workaround.
// https:github.com/microsoft/TypeScript/issues/16069#issuecomment-730710964
export const removeNullOrUndefined = (arr: any[]) =>
  arr.flatMap((item) => (isNotNullOrUndefined(item) ? item : EMPTY_ARRAY));
