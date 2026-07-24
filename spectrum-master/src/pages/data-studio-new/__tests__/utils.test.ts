import { processPredicates } from '../utils';

describe('processPredicates.filterValid', () => {
  it('should exclude predicates with only operator (no left.value)', () => {
    const predicates = [
      {
        left: { value: 'field1', label: 'Field 1' },
        operator: 'equals',
        right: { value: 'value1' },
        predicateId: 'pred1',
      },
      {
        operator: 'contains',
        right: { value: 'value2' },
        predicateId: 'pred2',
      },
    ];

    const result = processPredicates.filterValid(predicates);

    // Only pred1 should be included - pred2 is missing left.value
    expect(result).toHaveLength(1);
    expect(result[0].predicateId).toBe('pred1');
  });

  it('should exclude predicates with only left (no operator)', () => {
    const predicates = [
      {
        left: { value: 'field1', label: 'Field 1' },
        operator: 'equals',
        right: { value: 'value1' },
        predicateId: 'pred1',
      },
      {
        left: { value: 'field2', label: 'Field 2' },
        right: { value: 'value2' },
        predicateId: 'pred2',
      },
    ];

    const result = processPredicates.filterValid(predicates);

    // Only pred1 should be included - pred2 is missing operator
    expect(result).toHaveLength(1);
    expect(result[0].predicateId).toBe('pred1');
  });

  it('should exclude predicates without both left.value and operator', () => {
    const predicates = [
      {
        left: { value: 'field1', label: 'Field 1' },
        operator: 'equals',
        right: { value: 'value1' },
        predicateId: 'pred1',
      },
      {
        right: { value: 'value2' },
        predicateId: 'pred2',
      },
    ];

    const result = processPredicates.filterValid(predicates);

    // Only pred1 should be included - pred2 has neither left.value nor operator
    expect(result).toHaveLength(1);
    expect(result[0].predicateId).toBe('pred1');
  });

  it('should keep all valid predicates', () => {
    const predicates = [
      {
        left: { value: 'field1', label: 'Field 1' },
        operator: 'equals',
        right: { value: 'value1' },
        predicateId: 'pred1',
      },
      {
        left: { value: 'field2', label: 'Field 2' },
        operator: 'contains',
        right: { value: 'value2' },
        predicateId: 'pred2',
      },
    ];

    const result = processPredicates.filterValid(predicates);

    expect(result).toHaveLength(2);
    expect(result[0].predicateId).toBe('pred1');
    expect(result[1].predicateId).toBe('pred2');
  });

  it('should handle nested predicates (groups)', () => {
    const predicates = [
      {
        predicates: [
          {
            left: { value: 'field1', label: 'Field 1' },
            operator: 'equals',
            right: { value: 'value1' },
            predicateId: 'pred1',
          },
          {
            left: { value: 'field2', label: 'Field 2' },
            predicateId: 'pred2',
          },
        ],
        groupPredicateId: 'group1',
        operator: 'AND',
      },
    ];

    const result = processPredicates.filterValid(predicates);

    expect(result).toHaveLength(1);
    expect(result[0].groupPredicateId).toBe('group1');
    // Only pred1 should be included - pred2 is missing operator
    expect(result[0].predicates).toHaveLength(1);
    expect(result[0].predicates[0].predicateId).toBe('pred1');
  });

  it('should remove empty groups after filtering', () => {
    const predicates = [
      {
        left: { value: 'field1', label: 'Field 1' },
        operator: 'equals',
        right: { value: 'value1' },
        predicateId: 'pred1',
      },
      {
        predicates: [
          {
            // No left.value, no operator - should be excluded
            right: { value: 'value2' },
            predicateId: 'pred2',
          },
          {
            // No left.value, no operator - should be excluded
            predicateId: 'pred3',
          },
        ],
        groupPredicateId: 'group1',
        operator: 'OR',
      },
    ];

    const result = processPredicates.filterValid(predicates);

    // Only pred1 should be included, group1 should be removed (has no valid predicates)
    expect(result).toHaveLength(1);
    expect(result[0].predicateId).toBe('pred1');
  });

  it('should handle deeply nested predicates', () => {
    const predicates = [
      {
        predicates: [
          {
            predicates: [
              {
                left: { value: 'field1', label: 'Field 1' },
                operator: 'equals',
                right: { value: 'value1' },
                predicateId: 'pred1',
              },
              {
                operator: 'contains',
                right: { value: 'value2' },
                predicateId: 'pred2',
              },
            ],
            groupPredicateId: 'group2',
            operator: 'AND',
          },
          {
            left: { value: 'field3', label: 'Field 3' },
            operator: 'not_equals',
            right: { value: 'value3' },
            predicateId: 'pred3',
          },
        ],
        groupPredicateId: 'group1',
        operator: 'OR',
      },
    ];

    const result = processPredicates.filterValid(predicates);

    expect(result).toHaveLength(1);
    expect(result[0].groupPredicateId).toBe('group1');
    expect(result[0].predicates).toHaveLength(2);
    expect(result[0].predicates[0].groupPredicateId).toBe('group2');
    // Only pred1 should be in group2 - pred2 is missing left.value
    expect(result[0].predicates[0].predicates).toHaveLength(1);
    expect(result[0].predicates[0].predicates[0].predicateId).toBe('pred1');
    expect(result[0].predicates[1].predicateId).toBe('pred3');
  });

  it('should return empty array when all predicates are invalid', () => {
    const predicates = [
      {
        // No left.value, no operator - excluded
        right: { value: 'value1' },
        predicateId: 'pred1',
      },
      {
        // No operator - excluded
        left: { value: 'field2', label: 'Field 2' },
        predicateId: 'pred2',
      },
    ];

    const result = processPredicates.filterValid(predicates);

    expect(result).toHaveLength(0);
  });

  it('should handle empty array', () => {
    const predicates: any[] = [];

    const result = processPredicates.filterValid(predicates);

    expect(result).toHaveLength(0);
  });

  it('should exclude predicates with only left (no operator)', () => {
    const predicates = [
      {
        left: { value: 'field1', label: 'Field 1' },
        operator: 'equals',
        right: { value: 'value1' },
        predicateId: 'pred1',
        name: 'name1',
      },
      {
        left: { value: 'field2', label: 'Field 2' },
        predicateId: 'pred2',
        name: 'name2',
      },
    ];

    const result = processPredicates.filterValid(predicates);

    // Only pred1 should be included - pred2 is missing operator
    expect(result).toHaveLength(1);
    expect(result[0].predicateId).toBe('pred1');
  });

  it('should exclude predicates with missing operator', () => {
    const predicates = [
      {
        left: {
          dataType: 'string',
          label: 'About Us',
          picklistGroup: '',
          type: 'variable',
          value: '631fc4a33c733e7677d0fe0b',
        },
        operator: 'contains',
        right: {
          value: 'asd',
          type: 'literal',
        },
        predicateId: '6940269761d9953232f561c1',
        name: '631fc4a33c733e7677d0fe02',
      },
      {
        left: {
          dataType: 'string',
          label: 'About Us',
          picklistGroup: '',
          type: 'variable',
          value: '631fc4a33c733e7677d0fe0b',
        },
        predicateId: '694026ca61d9953232f561c3',
        name: '631fc4a33c733e7677d0fe02',
      },
    ];

    const result = processPredicates.filterValid(predicates);

    // Only first should be included - second is missing operator
    expect(result).toHaveLength(1);
    expect(result[0].predicateId).toBe('6940269761d9953232f561c1');
    expect(result[0].left).toBeDefined();
    expect(result[0].operator).toBe('contains');
    expect(result[0].right).toBeDefined();
  });

  it('should keep predicates with both left and operator and add default right if missing', () => {
    const predicates = [
      {
        left: { value: 'field1', label: 'Field 1' },
        operator: 'equals',
        right: { value: 'value1' },
        predicateId: 'pred1',
      },
      {
        left: { value: 'field2', label: 'Field 2' },
        operator: 'is_empty',
        // right is intentionally missing
        predicateId: 'pred2',
      },
      {
        left: { value: 'field3', label: 'Field 3' },
        operator: 'is_not_empty',
        right: undefined, // right is explicitly undefined
        predicateId: 'pred3',
      },
    ];

    const result = processPredicates.filterValid(predicates);

    // Should include all three predicates since they all have both left and operator
    expect(result).toHaveLength(3);
    expect(result[0].predicateId).toBe('pred1');
    expect(result[0].right).toEqual({ value: 'value1' });

    // Verify pred2 has default right added
    expect(result[1].predicateId).toBe('pred2');
    expect(result[1].left).toBeDefined();
    expect(result[1].operator).toBe('is_empty');
    expect(result[1].right).toEqual({ type: 'literal', value: '' });

    // Verify pred3 has default right added
    expect(result[2].predicateId).toBe('pred3');
    expect(result[2].left).toBeDefined();
    expect(result[2].operator).toBe('is_not_empty');
    expect(result[2].right).toEqual({ type: 'literal', value: '' });
  });

  it('should preserve all original keys including predicateId, name, and add default right', () => {
    const predicates = [
      {
        left: {
          dataType: 'string',
          label: 'About Us',
          picklistGroup: '',
          type: 'variable',
          value: '631fc4a33c733e7677d0fe0b',
        },
        operator: 'contains',
        right: {
          value: 'jen',
          type: 'literal',
        },
        predicateId: '6940312bab1ad93e0022b549',
        name: '631fc4a33c733e7677d0fe02',
      },
      {
        left: {
          dataType: 'string',
          label: 'About Us',
          picklistGroup: '',
          type: 'variable',
          value: '631fc4a33c733e7677d0fe0b',
        },
        operator: 'eq',
        predicateId: '6940313bab1ad93e0022b54c',
        name: '631fc4a33c733e7677d0fe02',
      },
    ];

    const result = processPredicates.filterValid(predicates);

    // Both predicates should be included (both have left and operator)
    expect(result).toHaveLength(2);

    // First predicate should preserve all keys including right
    expect(result[0]).toEqual({
      left: {
        dataType: 'string',
        label: 'About Us',
        picklistGroup: '',
        type: 'variable',
        value: '631fc4a33c733e7677d0fe0b',
      },
      operator: 'contains',
      right: {
        value: 'jen',
        type: 'literal',
      },
      predicateId: '6940312bab1ad93e0022b549',
      name: '631fc4a33c733e7677d0fe02',
    });

    // Second predicate should preserve all keys and add default right
    expect(result[1]).toEqual({
      left: {
        dataType: 'string',
        label: 'About Us',
        picklistGroup: '',
        type: 'variable',
        value: '631fc4a33c733e7677d0fe0b',
      },
      operator: 'eq',
      predicateId: '6940313bab1ad93e0022b54c',
      name: '631fc4a33c733e7677d0fe02',
      right: { type: 'literal', value: '' },
    });

    // Verify the second one has a right key with default value
    expect(result[1]).toHaveProperty('right');
    expect(result[1].right).toEqual({ type: 'literal', value: '' });
  });

  it('should preserve groupPredicateId and operator in nested groups and add default right', () => {
    const payload = {
      predicates: [
        {
          left: {
            dataType: 'string',
            label: 'About Us',
            picklistGroup: '',
            type: 'variable',
            value: '631fc4a33c733e7677d0fe0b',
          },
          operator: 'contains',
          right: {
            value: 'jen',
            type: 'literal',
          },
          predicateId: '6940312bab1ad93e0022b549',
          name: '631fc4a33c733e7677d0fe02',
        },
        {
          left: {
            dataType: 'string',
            label: 'About Us',
            picklistGroup: '',
            type: 'variable',
            value: '631fc4a33c733e7677d0fe0b',
          },
          operator: 'eq',
          predicateId: '6940313bab1ad93e0022b54c',
          name: '631fc4a33c733e7677d0fe02',
        },
      ],
      groupPredicateId: '6940312bab1ad93e0022b54a',
      operator: 'AND',
    };

    const result = processPredicates.filterValid([payload]);

    // Should preserve the group structure
    expect(result).toHaveLength(1);
    expect(result[0].groupPredicateId).toBe('6940312bab1ad93e0022b54a');
    expect(result[0].operator).toBe('AND');
    expect(result[0].predicates).toHaveLength(2);

    // Verify all predicates inside the group are preserved with their keys
    expect(result[0].predicates[0].predicateId).toBe('6940312bab1ad93e0022b549');
    expect(result[0].predicates[0].name).toBe('631fc4a33c733e7677d0fe02');
    expect(result[0].predicates[0].right).toBeDefined();

    expect(result[0].predicates[1].predicateId).toBe('6940313bab1ad93e0022b54c');
    expect(result[0].predicates[1].name).toBe('631fc4a33c733e7677d0fe02');
    expect(result[0].predicates[1].right).toEqual({ type: 'literal', value: '' });
  });

  it('should match the exact structure from user requirement', () => {
    const input = {
      predicates: [
        {
          left: {
            dataType: 'string',
            label: 'About Us',
            picklistGroup: '',
            type: 'variable',
            value: '631fc4a33c733e7677d0fe0b',
          },
          operator: 'empty',
          predicateId: '69403456090d1d1c111793d3',
          name: '631fc4a33c733e7677d0fe02',
        },
      ],
      groupPredicateId: '69403456090d1d1c111793d4',
      operator: 'AND',
    };

    const result = processPredicates.filterValid([input]);

    const expected = {
      predicates: [
        {
          left: {
            dataType: 'string',
            label: 'About Us',
            picklistGroup: '',
            type: 'variable',
            value: '631fc4a33c733e7677d0fe0b',
          },
          operator: 'empty',
          right: {
            type: 'literal',
            value: '',
          },
          predicateId: '69403456090d1d1c111793d3',
          name: '631fc4a33c733e7677d0fe02',
        },
      ],
      groupPredicateId: '69403456090d1d1c111793d4',
      operator: 'AND',
    };

    expect(result).toHaveLength(1);
    expect(result[0]).toEqual(expected);
  });

  it('should only include predicates with both left.value and operator', () => {
    const predicates = [
      {
        // Only has left - should be excluded (no operator)
        left: { value: 'field1' },
        predicateId: 'pred1',
      },
      {
        // Only has operator - should be excluded (no left.value)
        operator: 'equals',
        predicateId: 'pred2',
      },
      {
        // Has both left.value and operator - should be included
        left: { value: 'field3' },
        operator: 'contains',
        predicateId: 'pred3',
      },
    ];

    const result = processPredicates.filterValid(predicates);

    // Only pred3 should be included (has both left.value and operator)
    expect(result).toHaveLength(1);
    expect(result[0].predicateId).toBe('pred3');
    expect(result[0].left).toEqual({ value: 'field3' });
    expect(result[0].operator).toBe('contains');
    expect(result[0].right).toEqual({ type: 'literal', value: '' });
  });

  it('should serialize to JSON with right key always present', () => {
    const predicates = [
      {
        left: { value: 'field1' },
        operator: 'equals',
        predicateId: 'pred1',
      },
      {
        left: { value: 'field2' },
        operator: 'contains',
        right: { value: 'test' },
        predicateId: 'pred2',
      },
    ];

    const result = processPredicates.filterValid(predicates);
    const jsonString = JSON.stringify(result);
    const parsed = JSON.parse(jsonString);

    // Both predicates should be included and have right key
    expect(parsed).toHaveLength(2);

    // First predicate should have default right
    expect(parsed[0]).toHaveProperty('left');
    expect(parsed[0]).toHaveProperty('operator');
    expect(parsed[0]).toHaveProperty('right');
    expect(parsed[0].right).toEqual({ type: 'literal', value: '' });

    // Second predicate should have original right
    expect(parsed[1]).toHaveProperty('left');
    expect(parsed[1]).toHaveProperty('operator');
    expect(parsed[1]).toHaveProperty('right');
    expect(parsed[1].right).toEqual({ value: 'test' });
  });
});
