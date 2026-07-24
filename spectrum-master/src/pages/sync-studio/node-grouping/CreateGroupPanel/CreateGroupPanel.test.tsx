import { renderWithRouter, screen } from 'tests/helpers';
import AppConstants from 'utils/AppConstants';

import { CreateGroupPanel, CreateGroupPanelProps } from './CreateGroupPanel';

const props: CreateGroupPanelProps = {
  editor: {},
};

const testState = {
  pipeline: {
    createGroupPanel: {
      visible: true,
      selectedGroup: {
        label: 'Test group',
        description: 'Test description',
        color: AppConstants.GROUP_COLORS.BLUE,
        tags: ['Test tag 1', 'Test tag 2'],
      },
    },
  },
};

describe('CreateGroupPanel', () => {
  it('should display "New group" when creating a new group', async () => {
    renderWithRouter(<CreateGroupPanel {...props} />, {
      testState: {
        pipeline: {
          createGroupPanel: {
            ...testState.pipeline.createGroupPanel,
            selectedGroup: undefined,
          },
        },
      },
    });

    expect(await screen.findByText('New group')).toBeVisible();
  });

  it('should have its submit button disabled while form is in an invalid state', async () => {
    renderWithRouter(<CreateGroupPanel {...props} />, {
      testState: {
        pipeline: {
          createGroupPanel: {
            ...testState.pipeline.createGroupPanel,
            selectedGroup: undefined,
          },
        },
      },
    });

    expect((await screen.findByText('Create group')).closest('button')).toBeDisabled();
  });
});
