import { Layout } from 'react-grid-layout';

// convert array of keyvalue config into a props object
export const getProps = <T extends any>(c: Partial<T>[]): Partial<T> =>
  Array.isArray(c) ? c.reduce(Object.assign, {} as T) : ({} as T);

/** sort a grid of layouts (ltr ttb) into a ttb only layout, with
 * items in order from row 1, row 2, ...row N
 */
export const sortLayouts = (layout1: Layout, layout2: Layout) => {
  const sameRow = layout1.y === layout2.y;
  return sameRow ? layout1.x - layout2.x : layout1.y - layout2.y;
};

/**
 * this will sort as full width top-to-bottom widgets in the order expected
 * from a LTR TTB grid layout instead of items being intermingled "out of order"
 *
 *
 * Example
 *
 * Lg Layout
 *  -----------------------------
 *  |  --------  -------------  |
 *  |  |  1   |  |     2     |  |
 *  |  |      |  |           |  |
 *  |  --------  -------------  |
 *  |  --------  -------------  |
 *  |  |  3   |  |     4     |  |
 *  |  |      |  |           |  |
 *  |  --------  -------------  |
 *  -----------------------------
 *
 *
 * Sm layout                                 Sm layout
 * (with sortLayouts applied)                (WITHOUT sortLayoutsAndMakeFullWidth applied)
 *  --------------------                      --------------------
 *  | ---------------- |                      | --------         |
 *  | |      1       | |                      | |  1   |         |
 *  | |              | |                      | |      |         |
 *  | ---------------- |                      | --------         |
 *  | ---------------- |                      | --------         |
 *  | |      2       | |                      | |   3  |         |
 *  | |              | |                      | |      |         |
 *  | ---------------- |                      | --------         |
 *  | ---------------- |                      |   -------------- |
 *  | |      3       | |                      |   |    2       | |
 *  | |              | |                      |   |            | |
 *  | ---------------- |                      |   -------------- |
 *  | ---------------- |                      |   -------------- |
 *  | |      4       | |                      |   |    4       | |
 *  | |              | |                      |   |            | |
 *  | ---------------- |                      |   -------------- |
 *  --------------------                      --------------------
 *
 */
export const sortLayoutsAndMakeFullWidth = (layouts: Layout[], fullWidthColumnCount: number) =>
  layouts
    .slice()
    .sort(sortLayouts)
    .reduce((acc, layout) => {
      const [previousLayout] = acc.slice(-1);

      return [
        ...acc,
        {
          ...layout,
          x: 0,
          // our new Y position is based on the previous widget's Y position + it's height
          y: previousLayout ? previousLayout.y + previousLayout.h : 0,
          w: fullWidthColumnCount,
          maxW: fullWidthColumnCount,
        },
      ];
    }, [] as Layout[]);
