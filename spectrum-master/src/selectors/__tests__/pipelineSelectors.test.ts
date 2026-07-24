// @ts-nocheck
import { selectPipelineChange } from '../pipelineSelectors';

describe('selectPipelineChange', () => {
  test('returns pipeline changes', () => {
    const changed = true;
    const changedId = '8987asdfsafd0980';
    const changedScope = 'entity';

    const state = {
      pipeline: {
        changed,
        changedId,
        changedScope,
      },
    };

    const selectedChange = selectPipelineChange(state);

    expect(selectedChange.changed).toBe(true);
    expect(selectedChange.changedId).toBe(changedId);
    expect(selectedChange.changedScope).toBe(changedScope);
  });
});
