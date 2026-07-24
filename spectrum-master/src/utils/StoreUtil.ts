//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { PromiseThunkAction } from 'store/types';

const ongoingThunkAction = new Map<string, boolean>();

/**
 * Skips promise thunk action if its already running.
 * @param key {String} unique key identifier
 * @param action {Function} a function promise action
 */
export function thottlePromiseThunk<T = PromiseThunkAction>(key: string, action: () => Promise<T>) {
  if (!ongoingThunkAction.has(key)) {
    ongoingThunkAction.set(key, true);
    return action().finally(() => {
      ongoingThunkAction.delete(key);
    });
  }
}
