import { renderWithRouter, screen } from 'tests/helpers';

import { MultipleNodesPanel } from './MultipleNodesPanel';

const testState = {
  pipelineFunction: {
    entityPipelineFunctions: [],
    fieldPipelineFunctions: [],
  },
  pipelineAction: {
    entityPipelineActions: [],
    fieldPipelineActions: [],
  },
  fieldPipeline: {
    attributeNodes: [],
  },
};

describe('MultipleNodesPanel', () => {
  it.todo('should display the correct number of selected nodes');

  it('should render the correct selection action text', async () => {
    renderWithRouter(<MultipleNodesPanel />, { testState });

    expect(await screen.findByText('Clear selection')).toBeVisible();
  });

  it('should render the correct action buttons', async () => {
    renderWithRouter(<MultipleNodesPanel />, { testState });

    expect(await screen.findByText('Delete selected nodes')).toBeVisible();
  });
});
