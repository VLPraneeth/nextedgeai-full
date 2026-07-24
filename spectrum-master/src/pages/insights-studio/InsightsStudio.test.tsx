import { createHistory, createMemorySource, LocationProvider, Router } from '@reach/router';
import { dashboards } from 'mocks/fixtures/insights';
import { Globals } from 'react-spring';

import configureAppStore from 'store/configureStore';
import * as InsightsApi from 'store/insights-studio/api';
import { render, screen, userEvent, within } from 'tests/helpers';
import * as AppUtils from 'utils/AppUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';

import InsightsStudio from './InsightsStudio';
import { ExampleDashboards } from './utils/ExampleDashboards';

Globals.assign({ skipAnimation: true });

const renderPage = (dashboardId = 'dash1') =>
  render(
    <LocationProvider history={createHistory(createMemorySource(`/insights-studio/${dashboardId}`))}>
      <Router>
        {/* @ts-ignore InsightsStudio doesn't have a path prop*/}
        <InsightsStudio path="/insights-studio/:dashboardId" />
      </Router>
    </LocationProvider>,
    {
      store: configureAppStore(),
      testState: {
        insightsStudio: { userDataCardConfig: {} },
        user: {
          privileges: [AllPermissions.READ_INSIGHTS, AllPermissions.READ_STUDIO, AllPermissions.READ_DATA_STUDIO],
        },
      },
    }
  );

const mockDashboards = [...dashboards, ...ExampleDashboards];
jest.mock('./utils/useDashboards', () => ({ useDashboards: () => mockDashboards }));

const lastVisitedQuery = jest
  .spyOn(InsightsApi, 'useGetLastVisitedDashboardQuery')
  // @ts-expect-error only returning necessary data
  .mockImplementation(() => ({ data: { lastVisitedDashboardId: null }, isFetching: false }));

const navigateSpy = jest.spyOn(AppUtils, 'navigateTo');

describe('InsightsStudio', async () => {
  it('renders a select option for each dashboard', async () => {
    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: dashboards[0].displayName }));

    for (const dash of mockDashboards) {
      expect(await screen.findAllByRole('button', { name: dash.displayName })).toHaveLength(
        // First dashboard is auto-selected so there are 2, the one selected and the one in the menu
        dashboards[0].id === dash.id ? 2 : 1
      );
    }
  });

  it('adds an [Example] badge to example dashboards in menu', async () => {
    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: dashboards[0].displayName }));

    for (const dash of mockDashboards) {
      if (dash.isExample) {
        expect(
          within(screen.queryAllByRole('button', { name: dash.displayName })[0]).getByText('Example')
        ).toBeVisible();
      } else {
        expect(
          within(screen.queryAllByRole('button', { name: dash.displayName })[0]).queryByText('Example')
        ).not.toBeInTheDocument();
      }
    }
  });

  it('selects the dashboard in url', async () => {
    renderPage(dashboards[1].id);

    expect(await screen.findAllByRole('button', { name: dashboards[1].displayName })).toHaveLength(1);
  });

  it('reroutes to last visited dashboard when no url dash found', async () => {
    // @ts-expect-error
    lastVisitedQuery.mockImplementationOnce(() => ({
      data: { lastVisitedDashboardId: 'dash3', useNestedDraft: false },
      isFetching: false,
    }));
    renderPage('fake');

    expect(navigateSpy).toHaveBeenCalledWith(RouteConstants.INSIGHTS_STUDIO + '/dash3');
  });

  it('can reroute to last visited dashboard draft', async () => {
    // @ts-expect-error
    lastVisitedQuery.mockImplementationOnce(() => ({
      data: { lastVisitedDashboardId: 'dash3', useNestedDraft: true },
      isFetching: false,
    }));
    renderPage('fake');

    expect(navigateSpy).toHaveBeenCalledWith(RouteConstants.INSIGHTS_STUDIO + '/dash3/draft');
  });

  it('displays example header when viewing an example dashboard', async () => {
    renderPage(ExampleDashboards[0].id);

    expect(screen.queryByText('Welcome to Insights')).toBeVisible();
  });
});
