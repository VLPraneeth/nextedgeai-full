//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { init, t, tc, tCommon, tNamespaced, numberFormat } from '../i18nUtil';

describe('i18nUtil test', () => {
  beforeAll(() => init());

  test('simple string i18n common alias', () => {
    const str = tc('ok');
    expect(str).toEqual('Ok');
  });

  test('with token common alias', () => {
    const str = tc('cannot_be_empty', { name: 'test' });
    expect(str).toEqual('test cannot be empty');
  });

  test('simple string long name common alias', () => {
    const str = tCommon('ok');
    expect(str).toEqual('Ok');
  });

  test('namespaced simple i18n', () => {
    const tn = tNamespaced('Common');
    const str = tn('ok');
    expect(str).toEqual('Ok');
  });

  test('namespaced with token', () => {
    const tn = tNamespaced('Common');
    const str = tn('cannot_be_empty', { name: 'test' });
    expect(str).toEqual('test cannot be empty');
  });

  test('test t function', () => {
    const str = t('Common.ok');
    expect(str).toEqual('Ok');
  });

  // prettier-ignore
  test.each([
    [undefined     , '0'],
    ['abcd1'       , 'abcd1'],
    ['100'         , '100'],
    ['1000'        , '1,000'],
    ['1,000'       , '1,000'],
    ['10000.12'    , '10,000.12'],
    ['10000.00'    , '10,000'],
    ['1000000.01'  , '1,000,000.01'],
    ['1,000,000.01', '1,000,000.01'],
  ])(`format number %s with result %s`, (number, expectedResult) => {
    expect(numberFormat(number)).toBe(expectedResult);
  });
});
