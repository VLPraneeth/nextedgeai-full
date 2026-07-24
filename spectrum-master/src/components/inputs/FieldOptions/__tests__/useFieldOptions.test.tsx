import { entityFieldListFixture } from 'store/entity/fixtures';
import { renderHook } from 'tests/helpers';

import useFieldOptions from '../useFieldOptions';

describe('useFieldOptions', () => {
  const { filterOption, options } = renderHook(() => useFieldOptions(entityFieldListFixture));

  test('should return a list of options and a filter function', () => {
    expect(options).toHaveLength(entityFieldListFixture.length);
  });

  test('filterOption should return true for both displayName and apiName', () => {
    const aboutUsOption = options[0];

    // displayName
    let result = filterOption('about us', aboutUsOption);
    expect(result).toBe(true);

    // apiName
    result = filterOption('aboutus', aboutUsOption);
    expect(result).toBe(true);

    // Non-matching string
    result = filterOption('randomstring', aboutUsOption);
    expect(result).toBe(false);
  });
});
