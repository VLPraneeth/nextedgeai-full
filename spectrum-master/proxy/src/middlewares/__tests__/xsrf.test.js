//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { shouldEnforceXsrf } from '../xsrf';

describe('xsrf test', () => {
  test('Check photo is whitelisted', () => {
    expect(shouldEnforceXsrf('/arcade/api/v1/organization/photo')).toBe(false);
  });

  test('Check okta is whitelisted', () => {
    expect(shouldEnforceXsrf('/sso/asdfgdsaf/assertion')).toBe(false);
  });

  test('Check okta with trailing slash is whitelisted', () => {
    expect(shouldEnforceXsrf('/sso/asdfgdsaf/assertion/')).toBe(false);
  });

  test('Should check for xsrf', () => {
    expect(shouldEnforceXsrf('/arcade/api/v1/connector/describe')).toBe(true);
  });

  test('Should not enforce xsrf for connectormeta download requests', () => {
    expect(shouldEnforceXsrf('/arcade/api/v1/connectormeta/62589d4d04c7bf19a4fb4ee9/downloadErrorLog')).toBe(false);
    expect(shouldEnforceXsrf('/arcade/api/v1/connectormeta/62744ace3cb88b6e80138a6d/downloadFiles')).toBe(false);
    expect(shouldEnforceXsrf('/arcade/api/v1/connectormeta/624db2bd7a889f9cc833562e/icon')).toBe(false);
  });
});
