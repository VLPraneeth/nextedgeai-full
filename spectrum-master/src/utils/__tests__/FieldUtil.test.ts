//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { createUniqueEntityTitle } from '../FieldUtil';

describe('createUniqueEntityTitle', () => {
  it('should format field display name and api name', () => {
    const displayName = 'Test Entity';
    const apiName = 'test_entity';
    expect(createUniqueEntityTitle(displayName, apiName)).toBe(`${displayName} (${apiName})`);
  });
});
