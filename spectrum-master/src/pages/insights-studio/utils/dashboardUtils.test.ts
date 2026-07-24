import { calculateEndPosition } from './dashboardUtils';

describe('calculateEndPosition', () => {
  test.each([
    [[], { x: 0, y: 0 }],
    [
      [
        { x: 0, y: 0, w: 4 },
        { x: 4, y: 0, w: 4 },
      ],
      { x: 8, y: 0 },
    ],
    [
      [
        { x: 0, y: 0, w: 4 },
        { x: 0, y: 1, w: 6 },
      ],
      { x: 6, y: 1 },
    ],
    [
      [
        { x: 0, y: 0, w: 4 },
        { x: 0, y: 2, w: 8 },
      ],
      { x: 8, y: 2 },
    ],
    [
      [
        { x: 0, y: 0, w: 4 },
        { x: 0, y: 2, w: 9 },
      ],
      { x: 0, y: 3 },
    ],
    [
      [
        { x: 0, y: 4, w: 2 },
        { x: 0, y: 1, w: 4 },
      ],
      { x: 2, y: 4 },
    ],
  ])('calculates end position', (layouts, expected) => {
    const result = calculateEndPosition(layouts);

    expect(result.x).toEqual(expected.x);
    expect(result.y).toEqual(expected.y);
  });
});
