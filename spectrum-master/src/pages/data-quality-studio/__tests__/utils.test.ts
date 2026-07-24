import { sortLayouts, sortLayoutsAndMakeFullWidth } from '../utils';

const testLayouts = [
  {
    i: '1',
    x: 0,
    y: 0,
    h: 1,
    w: 1,
    minH: 1,
    maxH: 6,
    minW: 1,
    maxW: 6,
  },
  {
    i: '4',
    x: 4,
    y: 5,
    h: 2,
    w: 4,
    minH: 1,
    maxH: 6,
    minW: 4,
    maxW: 6,
  },
  {
    i: '8',
    x: 4,
    y: 0,
    h: 4,
    w: 6,
    minH: 1,
    maxH: 6,
    minW: 4,
    maxW: 6,
  },
  {
    i: '17',
    x: 0,
    y: 5,
    h: 4,
    w: 4,
    minH: 1,
    maxH: 6,
    minW: 4,
    maxW: 6,
  },
];

test('sortLayouts', () => {
  expect(
    testLayouts
      .slice()
      .sort(sortLayouts)
      .map((l) => l.i)
  ).toStrictEqual(['1', '8', '17', '4']);
});

test('sortLayoutsAndMakeFullWidth', () => {
  const expectedLayouts = [
    {
      ...testLayouts[0],
      x: 0,
      y: 0,
      w: 8,
      maxW: 8,
    },
    {
      ...testLayouts[2],
      x: 0,
      y: 1,
      w: 8,
      maxW: 8,
    },
    {
      ...testLayouts[3],
      x: 0,
      y: 5,
      w: 8,
      maxW: 8,
    },
    {
      ...testLayouts[1],
      x: 0,
      y: 9,
      w: 8,
      maxW: 8,
    },
  ];
  expect(sortLayoutsAndMakeFullWidth(testLayouts, 8)).toStrictEqual(expectedLayouts);
});
