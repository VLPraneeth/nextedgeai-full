//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import AppConstants from 'utils/AppConstants';

import { doesNeedConfiguration, makePicklistKey, shouldShowField } from '../NodeConfigUtil';

describe('NodeConfigUtil', () => {
  test('doesNeedConfiguration with blank config', () => {
    expect(doesNeedConfiguration()).toBe(false);
  });

  test('doesNeedConfiguration with config engine type', () => {
    expect(doesNeedConfiguration({ engineType: AppConstants.NODE_TYPE.ACTION })).toBe(true);
    expect(doesNeedConfiguration({ engineType: AppConstants.NODE_TYPE.FUNCTION })).toBe(true);
  });

  test('doesNeedConfiguration with implicit config', () => {
    expect(
      doesNeedConfiguration({
        configuration: [
          {
            implicit: true,
          },
        ],
      })
    ).toBeFalsy();
  });

  test('doesNeedConfiguration with undefined implicit config', () => {
    expect(
      doesNeedConfiguration({
        configuration: [{}],
      })
    ).toBeTruthy();
  });

  test('makePicklistKey should use overrideId when provided', () => {
    const dependantType = 'SelectionType';
    const overrideId = 'alternate_id';
    const entityDefinition = 'entity_definition';

    const picklistKey = `${dependantType}${overrideId}`;

    const valueWithoutDependantValue = makePicklistKey(
      {
        dependantField: 'configuration.entityDefinition',
        dependantType,
        params: {} as any,
      },
      {},
      overrideId
    );
    expect(valueWithoutDependantValue).toBe(picklistKey);

    const valueWithDependantValue = makePicklistKey(
      {
        dependantField: 'configuration.entityDefinition',
        dependantType,
        params: {} as any,
      },
      { configuration: { entityDefinition } },
      overrideId
    );
    expect(valueWithDependantValue).toBe(picklistKey);
  });
});

test.each([
  // Check the main flag
  [{}, { dependsOnFieldValue: false }, true],
  [
    // Test equal value
    { testField: 'testValue' },
    {
      dependsOnFieldValue: true,
      visibilityDependsOnFieldValue: [{ fieldName: 'testField', fieldValue: 'testValue' }],
    },
    true,
  ],
  [
    // Test regex partial matching
    { testField: 'testValue' },
    {
      dependsOnFieldValue: true,
      visibilityDependsOnFieldValue: [{ fieldName: 'testField', fieldValue: '^test.*' }],
    },
    true,
  ],
  [
    // Test invalid regex and should fall back to exact match
    { testField: '(test' },
    {
      dependsOnFieldValue: true,
      visibilityDependsOnFieldValue: [{ fieldName: 'testField', fieldValue: '(test' }],
    },
    true,
  ],
  [
    // Test single value field value
    { testField: 'testValue' },
    {
      dependsOnFieldValue: true,
      visibilityDependsOnFieldValue: { fieldName: 'testField', fieldValue: 'testValue' },
    },
    true,
  ],
  [
    // Test mismatch
    { testField: 'testValue' },
    {
      dependsOnFieldValue: true,
      visibilityDependsOnFieldValue: { fieldName: 'testField', fieldValue: 'somevalue' },
    },
    false,
  ],
  [
    // Test if theres a value
    { testField: 'asdf' },
    {
      dependsOnFieldValue: true,
      visibilityDependsOnFieldValue: { fieldName: 'testField', fieldValue: '^(?!s*$).+' },
    },
    true,
  ],
  [
    // Test for empty
    { testField: null },
    {
      dependsOnFieldValue: true,
      visibilityDependsOnFieldValue: { fieldName: 'testField', fieldValue: '^(?!s*$).+' },
    },
    false,
  ],
])('shouldShowField should only return true if the value matches the expected value', (value, match, result) => {
  expect(shouldShowField(value, match)).toBe(result);
});
