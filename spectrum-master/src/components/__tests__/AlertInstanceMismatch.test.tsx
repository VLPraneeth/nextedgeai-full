import { fireEvent } from '@testing-library/react';

import AlertInstanceMismatch from 'components/AlertInstanceMismatch';
import { LOCAL_STORAGE_INSTANCE_ID } from 'store/instances/slice';
import { render } from 'tests/helpers';

const testState = {
  testState: {
    user: {
      currentInstanceNextEdgeId: 'instance_one',
      instances: [
        {
          name: 'Instance One',
          displayName: 'Instance One',
          syncariId: 'instance_one',
          type: 'production',
          status: 'ACTIVE',
          planName: null,
          orgId: '5e0d22b47df51d37e546172e',
          orgName: 'Syncari Master',
          features: [],
        },
        {
          name: 'Instance Two',
          displayName: 'Instance Two',
          syncariId: 'instance_two',
          type: 'production',
          status: 'ACTIVE',
          planName: 'default',
          orgId: '5fcfc537583f8300016eb3f1',
          orgName: 'Dan, Incorp',
          features: [],
        },
      ],
    },
  },
};
const mockedWindow = global as any;

describe('<AlertInstanceMismatch />', () => {
  it('should render without errors', () => {
    render(<AlertInstanceMismatch />);
  });

  it('should show modal with active instance display name when localStorage changes the instanceId', async () => {
    const { findByText } = render(<AlertInstanceMismatch />, testState as any);

    localStorage.setItem(LOCAL_STORAGE_INSTANCE_ID, 'instance_two');
    fireEvent(mockedWindow, new Event('storage'));

    const element = await findByText('Your active instance is "Instance Two"');

    expect(element).toBeInTheDocument();
  });
});
