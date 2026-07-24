import { edgeIsInvalid } from '../PipelineEditor.utils';

describe('edgeIsInvalid', () => {
  test('edgeIsInvalid returns true when edge is missing source or target', async () => {
    expect(edgeIsInvalid([], { source: { id: 'source' } })).toBe(true);
    expect(edgeIsInvalid([], { target: { id: 'target' } })).toBe(true);
  });

  test('edgeIsInvalid returns true when source matches target', async () => {
    expect(edgeIsInvalid([], { source: { id: 'match' }, target: { id: 'match' } })).toBe(true);
  });

  test('edgeIsInvalid returns true when edge duplicates another edge', async () => {
    const edge = { source: { id: 'source' }, target: { id: 'target' } };
    expect(edgeIsInvalid([{ id: 'edge1', ...edge }], { id: 'edge2', ...edge })).toBe(true);
  });

  test('edgeIsInvalid returns false when edge is valid', async () => {
    const edge1 = { id: 'edge1', source: { id: 'source1' }, target: { id: 'target1' } };
    const edge2 = { id: 'edge2', source: { id: 'source2' }, target: { id: 'target2' } };
    const edge3 = { id: 'edge3', source: { id: 'source3' }, target: { id: 'target3' } };
    expect(edgeIsInvalid([edge1, edge2, edge3], edge3)).toBe(false);
  });
});
