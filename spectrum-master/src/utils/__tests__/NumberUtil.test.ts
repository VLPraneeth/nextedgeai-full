import { clamp } from '../NumberUtil';

describe('clamp', () => {
  test.each([
    [0, 100, 120, 100],
    [0, 50, 120, 50],
    [0, 100, 80, 80],
    [0, 100, -10, 0],
    [10, 100, -10, 10],
  ])('should restrict to min %s and max %s', (min, max, num, expected) => {
    const result = clamp(num, min, max);
    expect(result).toBe(expected);
  });
});
