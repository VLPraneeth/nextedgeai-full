// @ts-nocheck
// FP utilities

// curry :: ((a, b, ...) -> c) -> a -> b -> ... -> c
export function curry(fn) {
  const arity = fn.length;

  return function _curry(...args) {
    if (args.length < arity) {
      return _curry.bind(null, ...args);
    }

    return fn.call(null, ...args);
  };
}

// compose :: ((a -> b), (b -> c),  ..., (y -> z)) -> a -> z
export const compose = (...fns) => (...args) => fns.reduceRight((res, fn) => [fn.call(null, ...res)], args)[0];

// chain :: Monad m => (a -> m b) -> m a -> m b
export const chain = curry((fn, m) => m.chain(fn));

// map :: (a -> b) -> f a -> f b
export const map = curry((fn, xs) => xs.map(fn));

// mapObj :: Func -> Object -> Object'
export const mapObj = curry((fn, x) =>
  Object.entries(x).reduce((acc, [key, val]) => {
    acc[key] = fn(val);
    return acc;
  }, {})
);

// filter :: Predicate -> [a] -> [a]'
export const filter = (predicate) => (xs) => xs.filter(predicate);

// toString :: a -> String
export const toString = (obj) => obj.toString('utf-8');

// capitalize :: String -> String'
export const capitalize = (str) => `${str[0].toUpperCase()}${str.substring(1)}`;

// toUpperCase :: String -> String'
export const toUpperCase = (str) => str.toUpperCase();

// toLowerCase :: String -> String'
export const toLowerCase = (str) => str.toLowerCase();

// replace :: String -> String
export const replace = (find, replace) => (str) => str.replace(find, replace);

// prop :: string -> Object -> a
export const prop = curry((key, obj) => obj[key]);

// propOr :: String -> a -> Object -> b | a
export const propOr = curry((key, defaultVal, obj) => (obj.hasOwnProperty(key) ? prop(key, obj) : defaultVal));

// matchAll :: Regex -> String -> [Match]
export const matchAll = (regex) => (str) => Array.from(str.matchAll);

// toPairs :: Object -> [[String]]
export const toPairs = (obj) => Object.entries(obj);

// fromPairs :: [[String]] -> Object
export const fromPairs = (pairs) =>
  pairs.reduce((obj, [key, val]) => {
    obj[key] = val;
    return obj;
  }, {});

// keyBy :: String -> List -> Object
export const keyBy = <T>(propKey: string, items: T[] = []): Record<string, T> =>
  items.reduce((keyedData, item) => {
    keyedData[prop(propKey, item)] = item;
    return keyedData;
  }, {} as Record<string, T>);

// freeze :: Object -> Object
export const freeze = (obj) => Object.freeze(obj);

// every :: Func -> List x -> Boolean
export const every = (predicate) => (xs) => xs?.every(predicate);

// some :: Func -> List x -> Boolean
export const some = (predicate) => (xs) => xs?.some(predicate);

// min :: [Number] -> Number
export const min = (xs) => Math.min(...xs);

// min :: [Number] -> Number
export const max = (xs) => Math.max(...xs);

// trace :: String -> a -> a
// log the given arg and then return it. useful for
// logging items in a pipe or chain without interuption
export const trace = (tag) => (x) => console.log(tag, x) || x;

// filter items from an object by applying predicate to each value
// filterObj :: Pred -> Object -> Object'
export const filterObj = curry((predicate, data) =>
  compose(
    (pairs) => pairs.reduce((acc, [key, value]) => (predicate(value) ? { ...acc, [key]: value } : acc), {}),
    toPairs
  )(data)
);
