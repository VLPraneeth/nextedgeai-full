//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { getSaveValue } from '../InputUtil';

describe('getSaveValue support value object and raw value', () => {
  test.each([
    [
      {
        value: 'valueobject',
      },
      'valueobject',
    ],
    [
      {
        value: false,
      },
      false,
    ],
    [
      {
        value: true,
      },
      true,
    ],
    ['test', 'test'],
    [true, true],
    [false, false],
  ])(`getSaveValue with input returns %s`, (savedValue, expected) => {
    expect(getSaveValue(savedValue)).toBe(expected);
  });
});
