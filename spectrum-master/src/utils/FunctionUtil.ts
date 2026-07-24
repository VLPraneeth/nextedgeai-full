//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

export function bindAppend(func: Function, context: object) {
  var args = [].slice.call(arguments).slice(1);
  return function () {
    return func.apply(context, [].slice.call(arguments).concat(args));
  };
}

/**
 *
 * @param {Object} obj of the function it will override
 * @param {String} functionName name of the function
 * @param {Function} newFunction new function that will be invoked and can optionally
 * call the original function through the first parameter
 * @returns {Function} returns the orignal function
 */
export function functionOverride(obj: Record<string, Function>, functionName: string, newFunction: Function) {
  const func = obj[functionName];
  obj[functionName] = (...params: object[]) => {
    newFunction.apply(obj, [func, ...params]);
  };
  return func;
}
