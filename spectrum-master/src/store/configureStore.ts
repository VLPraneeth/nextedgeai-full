//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
// Generates a Redux stores.
//
import { configureStore } from '@reduxjs/toolkit';
import promise from 'redux-promise-middleware';

import { middleware as apiMiddleware } from 'store/api';

import rootReducer from '../reducers';
import createCrashReporter from './middleware/crashReporter';
import messageStream from './middleware/messageStream';

const crashReporter = createCrashReporter({ sendState: false });

const immutableCheckEnabled = window.localStorage.getItem('__REDUX_IMMUTABLE_CHECK') === 'true';
const serializableCheckEnabled = window.localStorage.getItem('__REDUX_SERIALIZABLE_CHECK') === 'true';

export default function configureAppStore<T extends object = any>(preloadedState?: T) {
  const store = configureStore({
    reducer: rootReducer,
    preloadedState,
    middleware: (getDefaultMiddleware) =>
      getDefaultMiddleware({
        // Enable these checks in localStorage to enable it
        immutableCheck: immutableCheckEnabled,
        serializableCheck: serializableCheckEnabled,
      })
        .prepend(crashReporter, promise, messageStream)
        .concat(apiMiddleware),
  });

  if (process.env.NODE_ENV !== 'production' && module.hot) {
    module.hot.accept('../reducers', () => store.replaceReducer(rootReducer));
  }

  return store;
}
