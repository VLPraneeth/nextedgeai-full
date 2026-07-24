//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { thottlePromiseThunk } from '../StoreUtil';

describe('StoreUtil', () => {
  test('throttle action', () => {
    let counter = 0;
    const func = () => {
      counter += 1;
      return new Promise<void>(() => {});
    };
    thottlePromiseThunk('key1', func);
    thottlePromiseThunk('key1', func);
    thottlePromiseThunk('key1', func);
    expect(counter).toBe(1);
  });

  test('can call multiple times after each call is resolved', async () => {
    let counter = 0;

    const func = () => {
      counter += 1;
      return Promise.resolve('test');
    };

    thottlePromiseThunk('key3', func);

    // Sleep to allow first promise to resolve
    await new Promise((resolve) => setTimeout(resolve));

    thottlePromiseThunk('key3', func);

    expect(counter).toBe(2);
  });
});
