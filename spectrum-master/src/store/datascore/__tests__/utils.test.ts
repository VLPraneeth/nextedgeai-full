import { gaugeSegments } from '../constants';
import { encodeFactorId, decodeFactorId, getSegmentForValue } from '../utils';

test.each([
  ['QUJDOmZpcnN0TmFtZToxMjM=', 'ABC', 'firstName', '123'],
  ['REVGOmxhc3ROYW1lOjY1NA==', 'DEF', 'lastName', '654'],
  ['QVNERjplbWFpbDo5ODc=', 'ASDF', 'email', '987'],
])(`encode/decode factorId %s for (%s, %s, %s)`, (factorId, ...keys) => {
  expect(encodeFactorId(...keys)).toBe(factorId);
  expect(decodeFactorId(factorId)).toStrictEqual(keys);
});

describe('getSegmentForValue', () => {
  test('should return Poor when provided 0', () => {
    const result = getSegmentForValue(gaugeSegments, 0);

    expect(result.label).toBe('Poor');
  });

  test('should return "No score" when provided null', () => {
    const result = getSegmentForValue(gaugeSegments, null);

    expect(result.label).toBe('No score');
  });
});
