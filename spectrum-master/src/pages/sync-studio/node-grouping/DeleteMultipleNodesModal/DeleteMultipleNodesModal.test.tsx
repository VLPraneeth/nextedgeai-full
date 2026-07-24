import { renderWithRouter, screen } from 'tests/helpers';

import { DeleteMultipleNodesModal, DeleteMultipleNodesModalProps } from './DeleteMultipleNodesModal';

const props: DeleteMultipleNodesModalProps = {
  editor: undefined,
  close: jest.fn(),
};

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
  pipeline: {
    deleteMultipleNodesModalVisible: true,
  },
};

describe('DeleteMultipleNodesModal', () => {
  it('should not render children when not visible', async () => {
    const { container } = renderWithRouter(<DeleteMultipleNodesModal {...props} />, {
      testState: {
        ...testState,
        pipeline: {
          deleteMultipleNodesModalVisible: false,
        },
      },
    });

    expect(container.firstChild).toBeNull();
  });

  it('should render its header when visible', async () => {
    renderWithRouter(<DeleteMultipleNodesModal {...props} />, { testState });

    expect(await screen.findByText('Delete nodes?')).toBeVisible();
  });

  it('should render its body when visible', async () => {
    renderWithRouter(<DeleteMultipleNodesModal {...props} />, { testState });

    expect(await screen.findByText("This action is permanant and can't be undone.")).toBeVisible();
  });

  it.todo('should display the correct number of nodes selected');

  it.todo('should display the correct list of nodes');

  it('should render its footer when visible', async () => {
    renderWithRouter(<DeleteMultipleNodesModal {...props} />, { testState });

    expect(await screen.findByText('Cancel')).toBeVisible();
    expect(await screen.findByText('Delete nodes')).toBeVisible();
  });
});
