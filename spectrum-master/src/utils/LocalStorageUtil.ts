//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

/**
 * Get the reducer default values
 * @param {String} reducerName - name of the reducer default value
 */
export function getReducerDefaultValues(reducerName: string) {
  return JSON.parse(atob(window.localStorage[reducerName] || btoa('{}')));
}
