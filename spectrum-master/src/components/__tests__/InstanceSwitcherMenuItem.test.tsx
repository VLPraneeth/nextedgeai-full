import { render, screen, userEvent, within } from 'tests/helpers';

import InstanceSwitcherMenuItem from '../InstanceSwitcherMenuItem';

const currentInstanceName = 'Instance 1';

describe('InstanceSwitcherMenuItem', () => {
  test('should show current instance name in menu', () => {
    render(<InstanceSwitcherMenuItem />, {
      testState: {
        user: {
          currentInstanceName,
          currentInstanceNextEdgeId: 'syncari_admin',
          currentInstanceType: 'production',
          instances: [
            {
              name: 'Instance 1',
              displayName: 'Instance 1',
              syncariId: 'syncari_admin',
              type: 'production',
              status: 'ACTIVE',
              planName: 'default',
              orgId: 'org_id',
              orgName: 'Syncari Master',
              features: [],
            },
          ],
        },
      },
    });

    expect(screen.getByText(currentInstanceName)).toBeInTheDocument();
  });

  test('should show a dropdown if multiple instances provided', async () => {
    const instance2Name = 'Instance 2';

    render(<InstanceSwitcherMenuItem />, {
      testState: {
        user: {
          currentInstanceName,
          currentInstanceNextEdgeId: 'syncari_admin',
          currentInstanceType: 'production',
          instances: [
            {
              name: 'Instance 1',
              displayName: 'Instance 1',
              syncariId: '773ZMS',
              type: 'production',
              status: 'ACTIVE',
              planName: 'default',
              orgId: '5e0d22b47df51d37e546172e',
              orgName: 'Syncari Master',
              features: [],
            },
            {
              name: instance2Name,
              displayName: instance2Name,
              syncariId: 'INQHLH',
              type: 'production',
              status: 'ACTIVE',
              planName: 'default',
              orgId: '5e0d22b47df51d37e546172e',
              orgName: 'Syncari Master',
              features: [],
            },
          ],
        },
      },
    });

    expect(screen.queryByText(instance2Name)).not.toBeInTheDocument();

    await userEvent.click(screen.getByText(currentInstanceName));

    expect(await screen.findByText(instance2Name)).toBeVisible();
  });

  test("should filter out instances that don't match search string", async () => {
    const instance2Name = 'Instance 2';

    render(<InstanceSwitcherMenuItem />, {
      testState: {
        user: {
          currentInstanceName,
          currentInstanceNextEdgeId: 'syncari_admin',
          currentInstanceType: 'production',
          instances: [
            {
              name: 'Instance 1',
              displayName: 'Instance 1',
              syncariId: '773ZMS',
              type: 'production',
              status: 'ACTIVE',
              planName: 'default',
              orgId: '5e0d22b47df51d37e546172e',
              orgName: 'Syncari Master',
              features: [],
            },
            {
              name: instance2Name,
              displayName: instance2Name,
              syncariId: 'INQHLH',
              type: 'production',
              status: 'ACTIVE',
              planName: 'default',
              orgId: '5e0d22b47df51d37e546172e',
              orgName: 'Syncari Master',
              features: [],
            },
          ],
        },
      },
    });

    expect(screen.queryByText(instance2Name)).not.toBeInTheDocument();

    await userEvent.click(await screen.findByText(currentInstanceName));

    expect(await screen.findAllByText(currentInstanceName)).toHaveLength(2);

    // Search for instance 2
    await userEvent.type(await screen.findByPlaceholderText('Filter…'), instance2Name);

    // Instance 2 should be the only one in the list
    const instanceButtons = within(await screen.findByTestId('instance-menu')).queryAllByRole('button');
    expect(instanceButtons).toHaveLength(1);
    expect(instanceButtons[0]).toHaveTextContent(instance2Name);
  });
});
