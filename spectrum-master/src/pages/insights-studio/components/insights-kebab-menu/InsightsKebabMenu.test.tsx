import { LocationProvider } from '@reach/router';
import { dash1 } from 'mocks/fixtures/insights';

import * as InsightsApi from 'store/insights-studio/api';
import { render, screen, userEvent } from 'tests/helpers';
import { AllPermissions } from 'utils/PermissionsConstants';

import { InsightsKebabMenu } from './InsightsKebabMenu';

const discardSpy = jest.fn(() => Promise.resolve({ data: undefined }));
const deleteSpy = jest.fn(() => Promise.resolve({ data: undefined }));
// @ts-expect-error typing of function
jest.spyOn(InsightsApi, 'useDeleteDraftDashboardMutation').mockReturnValue([discardSpy, {}]);
// @ts-expect-error typing of function
jest.spyOn(InsightsApi, 'useDeleteDashboardMutation').mockReturnValue([deleteSpy, {}]);

describe('InsightsKebabMenu', () => {
  it('prompts to confirm and then discards draft', async () => {
    render(
      <LocationProvider>
        <InsightsKebabMenu selectedDashboard={{ ...dash1, draftStatus: 'NEW' }} />
      </LocationProvider>,
      {
        testState: {
          user: {
            privileges: [AllPermissions.DELETE_DASHBOARD, AllPermissions.VIEW_DATACARD],
          },
        },
      }
    );

    await userEvent.click(screen.getByRole('button', { name: 'Dashboard Options' }));
    await userEvent.click(screen.getByText('Delete Draft'));
    // button on confirmation modal
    await userEvent.click(screen.getByText('Delete'));
    expect(discardSpy).toHaveBeenCalledWith(dash1.id);
  });

  it('prompts to confirm and then deletes published', async () => {
    render(
      <LocationProvider>
        <InsightsKebabMenu selectedDashboard={{ ...dash1, draftStatus: 'APPROVED' }} />
      </LocationProvider>,
      {
        testState: {
          user: {
            privileges: [AllPermissions.DELETE_DASHBOARD, AllPermissions.VIEW_DATACARD],
          },
        },
      }
    );

    await userEvent.click(screen.getByRole('button', { name: 'Dashboard Options' }));
    await userEvent.click(screen.getByText('Delete dashboard'));
    // button on confirmation modal
    await userEvent.click(screen.getByText('Delete'));
    expect(deleteSpy).toHaveBeenCalledWith(dash1.id);
  });
});
