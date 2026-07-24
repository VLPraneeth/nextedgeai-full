import { LocationProvider } from '@reach/router';
import { dash1 } from 'mocks/fixtures/insights';

import configureAppStore from 'store/configureStore';
import { render, screen } from 'tests/helpers';

import { DashboardLayout } from './DashboardLayout';

const renderComponent = (dashId: string) =>
  render(
    <LocationProvider>
      <DashboardLayout dashboardId={dashId} />
    </LocationProvider>,
    {
      store: configureAppStore(),
      testState: {
        insightsStudio: { userDataCardConfig: {} },
      },
    }
  );

describe('DashboardLayout', () => {
  it('renders all data cards', async () => {
    renderComponent('dash1');

    if (dash1?.dataCards) {
      for (let card of dash1.dataCards) {
        if (card.displayName) {
          expect(await screen.findByText(card.displayName)).toBeVisible();
        }
      }
    }
  });

  it('renders an error message when request fails', async () => {
    renderComponent('fakeDash');

    // Can't use .toBeVisible() due to react-spring animation setting opacity: 0 on render
    expect(await screen.findByText('Could not find dashboard fakeDash')).toBeInTheDocument();
  });
});
