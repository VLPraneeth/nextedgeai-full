import { Position } from '@xyflow/react';

import getClosestSide from './getClosestSide';

describe('getClosestSide', () => {
  test('returns right and left for standard pipeline position', async () => {
    const sourceNode = {
      x: 0,
      y: 0,
      width: 50,
      height: 50,
    };
    const targetNode = {
      x: 100,
      y: 0,
      width: 50,
      height: 50,
    };

    const result = getClosestSide(sourceNode, targetNode);

    expect(result.sourcePosition).toBe(Position.Right);
    expect(result.targetPosition).toBe(Position.Left);
  });

  test('returns bottom and top when source is above target node', async () => {
    const sourceNode = {
      x: 0,
      y: 0,
      width: 50,
      height: 50,
    };
    const targetNode = {
      x: 0,
      y: 100,
      width: 50,
      height: 50,
    };

    const result = getClosestSide(sourceNode, targetNode);

    expect(result.sourcePosition).toBe(Position.Bottom);
    expect(result.targetPosition).toBe(Position.Top);
  });
});
