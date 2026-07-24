//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { validate } from '../ValidationUtil';

test('validate required string', () => {
  const vMeta = {
    message: 'Invalid',
    required: true,
  };

  const result = validate(vMeta, 'string');
  expect(result).toEqual(true);
});

test('validate required string name field', () => {
  const vMeta = {
    label: 'Name',
    required: true,
  };

  try {
    validate(vMeta);
  } catch (e) {
    expect((e as Error).message).toContain('required');
  }
});

test('validate required custom message', () => {
  const vMeta = {
    message: 'My own custom message',
    required: true,
  };

  try {
    validate(vMeta);
  } catch (e) {
    expect((e as Error).message).toContain('custom message');
  }
});

test('validate with yup ast', () => {
  const vMeta = {
    yup: [['yup.mixed'], ['yup.required', 'validated through passed yup ast']],
  };

  try {
    validate(vMeta);
  } catch (e) {
    expect((e as Error).message).toContain('yup ast');
  }
});
