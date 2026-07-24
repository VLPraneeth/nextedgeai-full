import { Middleware } from 'redux';

import { RootState } from '../types';

class ReduxError<State = any, Actions = any[]> extends Error {
  allState: State | null;
  lastActions: Actions | null;

  static from(error: Error, state: any, actions: any[]) {
    const e = new ReduxError(error.message);
    e.stack = error.stack;
    e.allState = state;
    e.lastActions = actions;
    return e;
  }

  constructor(message: string) {
    super(message);
    this.name = 'ReduxError';
    this.allState = null;
    this.lastActions = null;
  }
}

const crashReportDefaults = {
  sendState: false,
};

const createCrashReporter = (config = {}): Middleware<{}, RootState> => {
  const options = { ...crashReportDefaults, ...config };

  return (store) => (next) => (action) => {
    try {
      // catch errors during dispatch
      return next(action);
    } catch (err) {
      if (process?.env?.NODE_ENV !== 'production') {
        console.group('REDUX ERROR');
        console.error('ERROR', err);
        console.info('Last Action', action);
        console.groupEnd();
      }

      const currentState = store.getState();

      const lastActions = currentState?.app?.lastActions?.slice();
      if (lastActions?.length) {
        lastActions.reverse();
      }

      const serializedState = options.sendState ? currentState : {};

      // rethrow decorated ReduxError
      throw ReduxError.from(
        err instanceof Error ? err : new Error('Redux crash encountered'),
        serializedState,
        lastActions
      );
    }
  };
};

export default createCrashReporter;
export { ReduxError };
