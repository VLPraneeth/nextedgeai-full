//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { dataCard1, customerCount } from 'mocks/fixtures/insights';

import { render, screen } from 'tests/helpers';

import { DataCardSettingsContextProvider } from './DataCardSettingsContext';
import DataCardSettingsModal from './DataCardSettingsModal';

describe('Insights Settings Modal', () => {
  it('should render the modal without any issues', async () => {
    render(
      <DataCardSettingsContextProvider
        value={{
          showSettings: () => {},
          settingsVisible: true,
          settingsOptions: {
            dashboardId: 'testDashboard',
            dataCard: dataCard1,
          },
        }}>
        <DataCardSettingsModal />
      </DataCardSettingsContextProvider>,
      {
        testState: {
          user: {},
          insightsStudio: { userDataCardConfig: {} },
        },
      }
    );
    expect(screen.queryByText('No configuration found')).toBeVisible();
    expect(screen.queryByText('Apply')).toBeVisible();
  });

  it('should render configuration metadata', async () => {
    render(
      <DataCardSettingsContextProvider
        value={{
          showSettings: () => {},
          settingsVisible: true,
          settingsOptions: {
            dashboardId: 'testDashboard',
            dataCard: customerCount,
          },
        }}>
        <DataCardSettingsModal />
      </DataCardSettingsContextProvider>,
      {
        testState: {
          user: {},
          insightsStudio: { userDataCardConfig: {} },
        },
      }
    );
    expect(screen.queryByText('Customer Count')).toBeVisible();
    expect(screen.getByDisplayValue('10000')).toBeVisible();
  });
});
