import * as ReachRouter from '@reach/router';

import { RoleName } from 'store/user/types';
import { hideBenignTestWarnings, render, screen, userEvent, waitFor } from 'tests/helpers';
import CapConstants from 'utils/CapConstants';
import { AllPermissions } from 'utils/PermissionsConstants';

import Settings from './Settings';

hideBenignTestWarnings();

const navigate = jest.spyOn(ReachRouter, 'navigate');

const settingsPages = [
  {
    pathname: '/settings/subscription-profile',
    pageTitle: 'Subscription Profile',
  },
  {
    pathname: '/settings/access-control/role-based',
    pageTitle: 'Role based',
  },
  {
    pathname: '/settings/subscription',
    pageTitle: 'Subscriptions',
  },
  {
    pathname: '/settings/instance',
    pageTitle: 'Instances',
  },
  {
    pathname: '/settings/user',
    pageTitle: 'Users',
  },
  {
    pathname: '/settings/credential',
    pageTitle: 'Service Credentials',
  },
  {
    pathname: '/settings/specter',
    pageTitle: 'Specter',
  },
  {
    pathname: '/settings/datastore/configure',
    pageTitle: 'Configure',
  },
  {
    pathname: '/settings/sso',
    pageTitle: 'Single Sign-On',
  },
];

interface RenderOptions {
  roles?: RoleName[];
  pathname?: string;
  ghosted?: boolean;
  isSuperAdmin?: boolean;
  isGhostUser?: boolean;
}
const renderSettings = (options?: RenderOptions) => {
  const location = { pathname: options?.pathname ?? '/settings' } as ReachRouter.WindowLocation;

  return render(<Settings location={location} />, {
    testState: {
      user: {
        currentInstanceNextEdgeId: 'VGNXFZ',
        userRoles: { VGNXFZ: options?.roles ?? [] },
        ghosted: Boolean(options?.ghosted),
        isSuperAdmin: Boolean(options?.isSuperAdmin ?? true),
        isGhostUser: Boolean(options?.isGhostUser),
        privileges: Object.values(AllPermissions),
      },
    },
  });
};

describe('Settings', () => {
  beforeAll(() => {
    localStorage.setItem('ACCESS_CONTROL', 'true');
    localStorage.setItem('RBAC', 'true');
  });

  afterAll(() => {
    localStorage.removeItem('ACCESS_CONTROL');
    localStorage.removeItem('RBAC');
  });

  afterEach(() => {
    jest.resetAllMocks();
  });

  it('renders', () => {
    render(<Settings />);
  });

  it('renders buttons for all Settings pages', async () => {
    renderSettings();
    await userEvent.click(screen.queryByText('Access Control')!);
    await userEvent.click(screen.queryByText('Data Store')!);

    settingsPages.forEach((page) => {
      expect(screen.queryByText(page.pageTitle)).toBeVisible();
    });
  });

  it('hides Subscriptions and Specter for non-Super Admins', async () => {
    renderSettings({ roles: [CapConstants.ADMIN as RoleName], isSuperAdmin: false });
    await userEvent.click(screen.queryByText('Access Control')!);
    await userEvent.click(screen.queryByText('Data Store')!);

    settingsPages.forEach((page) => {
      if (['Subscriptions', 'Specter'].includes(page.pageTitle)) {
        expect(screen.queryByText(page.pageTitle)).not.toBeInTheDocument();
      } else {
        expect(screen.queryByText(page.pageTitle)).toBeVisible();
      }
    });
  });

  it('hides Subscriptions, Specter and Subscription Profile for non-Admins', async () => {
    renderSettings({ roles: ['Instance Admin'], isSuperAdmin: false });
    await userEvent.click(screen.queryByText('Access Control')!);
    await userEvent.click(screen.queryByText('Data Store')!);

    settingsPages.forEach((page) => {
      if (['Subscriptions', 'Specter'].includes(page.pageTitle)) {
        expect(screen.queryByText(page.pageTitle)).not.toBeInTheDocument();
      } else {
        expect(screen.queryByText(page.pageTitle)).toBeVisible();
      }
    });
  });

  it('hides Subscriptions from any ghosted user', async () => {
    renderSettings({ isSuperAdmin: true, ghosted: true });
    await userEvent.click(screen.queryByText('Access Control')!);
    await userEvent.click(screen.queryByText('Data Store')!);

    settingsPages.forEach((page) => {
      if (['Subscriptions'].includes(page.pageTitle)) {
        expect(screen.queryByText(page.pageTitle)).not.toBeInTheDocument();
      } else {
        expect(screen.queryByText(page.pageTitle)).toBeVisible();
      }
    });
  });

  it('selects Subscription Profile page by default', async () => {
    const { container } = renderSettings();
    await userEvent.click(screen.queryByText('Access Control')!);
    await userEvent.click(screen.queryByText('Data Store')!);

    await waitFor(() => expect(navigate).toHaveBeenCalledTimes(1));

    expect(navigate).toHaveBeenCalledWith('/settings/subscription-profile');
    // eslint-disable-next-line testing-library/no-container
    expect(container.getElementsByClassName('ant-menu-item-selected')).toHaveLength(1);
    // eslint-disable-next-line testing-library/no-container
    expect(container.getElementsByClassName('ant-menu-item-selected')[0]).toHaveTextContent('Subscription Profile');
  });

  it('selects Instances page if user is Instance Admin', async () => {
    const { container } = renderSettings({ roles: ['Instance Admin'], isSuperAdmin: false });
    await userEvent.click(screen.queryByText('Access Control')!);
    await userEvent.click(screen.queryByText('Data Store')!);

    await waitFor(() => expect(navigate).toHaveBeenCalledTimes(1));

    expect(navigate).toHaveBeenCalledWith('/settings/instance');
    // eslint-disable-next-line testing-library/no-container
    expect(container.getElementsByClassName('ant-menu-item-selected')).toHaveLength(1);
    // eslint-disable-next-line testing-library/no-container
    expect(container.getElementsByClassName('ant-menu-item-selected')[0]).toHaveTextContent('Instances');
  });

  it("redirects to base page if on a settings page that doesn't exist", async () => {
    renderSettings({ pathname: '/settings/fake' });
    await userEvent.click(screen.queryByText('Access Control')!);
    await userEvent.click(screen.queryByText('Data Store')!);

    await waitFor(() => expect(navigate).toHaveBeenCalledTimes(1));

    expect(navigate).toHaveBeenLastCalledWith('/settings');
  });

  test.each([
    [{ isSuperAdmin: true }, 'super admin'],
    [{ isGhostUser: true, isSuperAdmin: false }, 'ghost user'],
  ])(`Show show all settings for %s`, async (options, user) => {
    renderSettings(options);
    await userEvent.click(screen.queryByText('Access Control')!);
    await userEvent.click(screen.queryByText('Data Store')!);

    settingsPages.forEach((page) => {
      expect(screen.queryByText(page.pageTitle)).toBeVisible();
    });
  });

  describe('successfully triggers navigation for each page', () => {
    settingsPages.forEach((page) => {
      it(page.pageTitle, async () => {
        renderSettings();
        if (page.pathname === '/settings/datastore/configure') {
          await userEvent.click(screen.queryByText('Data Store')!);
        } else {
          await userEvent.click(screen.queryByText('Access Control')!);
        }

        await waitFor(() => expect(navigate).toHaveBeenCalledTimes(1));
        await userEvent.click(screen.getByText(page.pageTitle));
        await waitFor(() => expect(navigate).toHaveBeenCalledTimes(2));

        expect(navigate).toHaveBeenCalledWith(page.pathname);
      });
    });
  });
});
