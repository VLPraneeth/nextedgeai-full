//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import MainHeader from 'pages/MainHeader';
import { hideBenignTestWarnings, mockedAjaxUtils, renderWithRouter, screen, userEvent, within } from 'tests/helpers';

jest.mock('components/icons/InlineSvg');
jest.mock('utils/AjaxUtil');
const ajaxMock = mockedAjaxUtils();
ajaxMock.get.mockImplementation(jest.fn(() => Promise.resolve()));

hideBenignTestWarnings();

const testInstances = [
  {
    syncariId: '111',
    name: 'Production',
    displayName: 'Production',
    type: 'production',
    orgId: '9876bb',
    orgName: 'Test Organization',
  },
  {
    syncariId: '222',
    name: 'Staging',
    displayName: 'Staging',
    type: 'sandbox',
    orgId: '9876bb',
    orgName: 'Test Organization',
  },
  {
    syncariId: '333',
    name: 'Third',
    displayName: 'Third',
    type: 'sandbox',
    orgId: '9876bb',
    orgName: 'Test Organization',
  },
];

const renderMainHeader = (overrides?: any) => {
  return renderWithRouter(<MainHeader />, {
    testState: {
      user: {
        id: '12345667',
        firstName: 'FirstName',
        lastName: 'LastName',
        email: 'user@test.com',
        currentInstanceNextEdgeId: testInstances[0].syncariId,
        currentInstanceName: testInstances[0].displayName,
        currentInstanceType: testInstances[0].type,
        instances: testInstances,
        orgId: testInstances[0].orgId,
        orgName: testInstances[0].orgName,
        versionMetadata: {
          arcadeTarget: 'https://localhost:3000',
        },
        ...overrides,
      },
      schema: { connectorSchemas: {} },
    },
  });
};

describe('MainHeader', () => {
  it('renders username, instance info, and instance dropdown menu', async () => {
    renderMainHeader();

    // open instance switching menu
    await userEvent.click(screen.getByText('Test Organization'));
    // instance menu is opened in a tooltip/dropdown
    const instancesMenu = within(screen.getByRole('tooltip'));

    // make sure we have the org name in the dropdown
    expect(instancesMenu.getByText(testInstances[0].orgName)).toBeInTheDocument();

    // for each instance, make sure the entry is in the dropdown and it's marked as sandbox if appropriate
    testInstances.forEach((instance) => {
      // find the line item for this instance in the menu
      const instanceNode = within(instancesMenu.getByTestId(`instance-${instance.syncariId}`));

      if (instance.type === 'sandbox') {
        expect(instanceNode.getByText('Sandbox')).toBeVisible();
      } else {
        expect(instanceNode.queryByText('Sandbox')).toBeNull();
      }
    });
  });

  it('Does not render Instance Dropdown when only one instance', () => {
    renderMainHeader({ instances: [testInstances[0]] });

    // find instance switcher, ensure it has Org/Instance/Badge
    expect(screen.getByText('Production')).toBeVisible();

    // We expect that there won't be a dropdown
    expect(screen.queryByRole('tooltip')).toBeNull();
  });

  it('Renders profile menu items', async () => {
    renderMainHeader();

    await userEvent.click(screen.getByText('FirstName'));

    expect(screen.getByText('Profile')).toBeVisible();
    expect(screen.getByText('About')).toBeVisible();
    expect(screen.getByText('Logout')).toBeVisible();
  });
});
