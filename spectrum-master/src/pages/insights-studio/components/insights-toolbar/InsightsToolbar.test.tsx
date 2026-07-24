import * as Reach from '@reach/router';
import { dash1, dash2 } from 'mocks/fixtures/insights';
import { Globals } from 'react-spring';

import * as InsightsUtils from 'pages/insights-studio/utils/useInsightsEnabled';
import configureAppStore from 'store/configureStore';
import * as InsightsApi from 'store/insights-studio/api';
import { InsightsDashboard } from 'store/insights-studio/types';
import { render, screen, userEvent, waitFor } from 'tests/helpers';
import { AllPermissions } from 'utils/PermissionsConstants';

import { InsightsToolbar } from './InsightsToolbar';
Globals.assign({ skipAnimation: true });

const { createHistory, createMemorySource, LocationProvider, Router } = Reach;

const navigate = jest.spyOn(Reach, 'navigate');
const useInsightsEnabled = jest.spyOn(InsightsUtils, 'useInsightsEnabled');
const publish = jest.fn(() => Promise.resolve({ data: undefined }));
jest
  .spyOn(InsightsApi, 'usePublishDraftDashboardMutation')
  // @ts-expect-error typing of publish function
  .mockReturnValue([publish, {}]);

const renderComponent = (selectedDashboard: InsightsDashboard) =>
  render(
    <LocationProvider history={createHistory(createMemorySource(`/insights-studio/1`))}>
      <Router>
        <InsightsToolbar
          availableDashboards={[dash1, dash2]}
          selectedDashboard={selectedDashboard}
          // @ts-expect-error InsightsToolbar doesn't have a path prop
          path="/insights-studio/1"
        />
      </Router>
    </LocationProvider>,
    {
      store: configureAppStore(),
      testState: {
        user: {
          privileges: [AllPermissions.UPDATE_DASHBOARD, AllPermissions.CREATE_DASHBOARD],
        },
      },
    }
  );

describe('InsightsToolbar', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it("doesn't show controls if insights not enabled", async () => {
    useInsightsEnabled.mockReturnValue(false);
    renderComponent(dash1);

    expect(await screen.findByText('Dash 1')).toBeVisible();

    expect(screen.queryByRole('button', { name: 'Publish' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Dashboard Options' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Settings' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Toggle sidebar' })).not.toBeInTheDocument();
  });

  it("doesn't show authoring controls on published dashboards", async () => {
    useInsightsEnabled.mockReturnValue(true);
    renderComponent(dash2);
    await waitFor(() => {
      expect(screen.queryByRole('button', { name: 'Publish' })).not.toBeInTheDocument();
    });
    await waitFor(() => {
      expect(screen.queryByRole('button', { name: 'Settings' })).not.toBeInTheDocument();
    });
    await waitFor(() => {
      expect(screen.queryByRole('button', { name: 'Toggle sidebar' })).not.toBeInTheDocument();
    });
  });

  it("doesn't show controls when there is no synced data", async () => {
    useInsightsEnabled.mockReturnValue(true);
    renderComponent(dash2);
    await waitFor(() => {
      expect(screen.queryByRole('button', { name: 'Publish' })).not.toBeInTheDocument();
    });
    await waitFor(() => {
      expect(screen.queryByRole('button', { name: 'Settings' })).not.toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.queryByRole('button', { name: 'Toggle sidebar' })).not.toBeInTheDocument();
    });
  });

  it('shows authoring controls for draft dashboard', async () => {
    renderComponent(dash1);

    expect(await screen.findByRole('button', { name: 'Publish' })).toBeVisible();
    expect(await screen.findByRole('button', { name: 'Settings' })).toBeVisible();
    expect(await screen.findByRole('button', { name: 'Dashboard Options' })).toBeVisible();
    expect(await screen.findByRole('button', { name: 'Toggle sidebar' })).toBeVisible();
  });

  it('shows kebab menu with delete option for draft dashboard', async () => {
    renderComponent(dash1);

    await userEvent.click(await screen.findByRole('button', { name: 'Dashboard Options' }));

    expect(await screen.findByRole('menuitem', { name: 'Delete Draft' })).toBeVisible();
  });

  it('shows create draft option for published dashboard', async () => {
    renderComponent(dash2);

    expect(await screen.findByRole('button', { name: 'Create Draft' })).toBeVisible();
  });

  it('does not show create draft option for seeded dashboard', async () => {
    renderComponent({ ...dash2, seeded: true });

    expect(await screen.findByText('Dash 2')).toBeVisible();

    expect(screen.queryByRole('button', { name: 'Create Draft' })).not.toBeInTheDocument();
  });

  /* TODO: figure out why these two tests aren't finding the button
  it('can open and close create dashboard form', async () => {
    useInsightsEnabled.mockReturnValue(true);
    renderComponent(dash1);

   await userEvent.click(await screen.findByRole('button', { name: 'Create Dashboard' }));
    expect(screen.queryByText('New Dashboard')).toBeVisible();
    expect(screen.queryByText('Display name')).toBeVisible();

   await userEvent.click(screen.getByRole('button', { name: 'Close' }));

    expect(screen.queryByText('Display name')).not.toBeVisible();
  });

  it('can open and close edit dashboard form', async () => {
    useInsightsEnabled.mockReturnValue(true);

    renderComponent(dash1);
   await userEvent.click(await screen.findByRole('button', { name: 'Settings' }));

    expect(await screen.findByText('Edit Dashboard')).toBeVisible();
    expect(await screen.findByText('Display name')).toBeVisible();

   await userEvent.click(screen.getByRole('button', { name: 'Close' }));

    expect(screen.queryByText('Display name')).not.toBeVisible();
  });
  */

  it('redirects to dashboard selected from dropdown', async () => {
    renderComponent(dash1);

    await userEvent.click(await screen.findByRole('button', { name: dash1.displayName }));
    await userEvent.click(screen.getByRole('button', { name: dash2.displayName }));

    expect(navigate).toHaveBeenCalledWith('/insights-studio/' + dash2.id);
  });
});
