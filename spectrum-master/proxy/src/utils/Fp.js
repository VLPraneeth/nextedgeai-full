// TODO: Share common utilities from proxy <> spectrum.
//

// compose :: ((a -> b), (b -> c),  ..., (y -> z)) -> a -> z
const compose = (...fns) => (...args) => fns.reduceRight((res, fn) => [fn.call(null, ...res)], args)[0];

// prepend :: String -> String -> String`
const prepend = (start) => (str) => `${start}${str}`;

// append :: String -> String -> String`
const append = (end) => (str) => `${str}${end}`;

// head :: [a] -> a
const head = (xs) => xs[0];

// prop :: String -> Object -> a
const prop = (key) => (o) => o[key];

// match :: Regex -> String -> String | null
const match = (regex) => (str) => (str ? str.match(regex) : str);

// trim :: String -> String`
const trim = (str) => str.trim();

// split :: String -> String -> [String]
const split = (delimiter) => (str) => str.split(delimiter);

// filter :: Predicate -> [a] -> [a]`
const filter = (predicate) => (xs) => xs.filter(predicate);

// join :: String -> [String] -> String
const join = (delimiter) => (xs) => xs.join(delimiter);

// map :: (fa -> fb) -> fa -> fb
const map = (fn) => (xs) => xs.map(fn);

// getMatchResult :: [String, String, ...] -> String
const getMatchResult = (match) => match[1];

// trace :: String -> a -> a
const trace = (tag) => (x) => console.log(tag, x) || x;

// unique :: [a] -> [a]`
const unique = (xs) => Array.from(new Set(xs));

export { append, compose, filter, getMatchResult, head, join, map, match, prepend, prop, split, trace, trim, unique };
