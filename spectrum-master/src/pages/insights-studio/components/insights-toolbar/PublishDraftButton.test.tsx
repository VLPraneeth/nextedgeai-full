import * as Reach from '@reach/router';
import { dash1 } from 'mocks/fixtures/insights';

import * as InsightsApi from 'store/insights-studio/api';
import { render, screen, userEvent, waitFor } from 'tests/helpers';
import { AllPermissions } from 'utils/PermissionsConstants';

import { PublishDraftButton } from './PublishDraftButton';

const { createHistory, createMemorySource, LocationProvider, Router } = Reach;

const navigate = jest.spyOn(Reach, 'navigate');
const publish = jest.fn(() => Promise.resolve({ data: undefined }));
jest
  .spyOn(InsightsApi, 'usePublishDraftDashboardMutation')
  // @ts-expect-error typing of function
  .mockReturnValue([publish, {}]);

const renderComponent = () =>
  render(
    <LocationProvider history={createHistory(createMemorySource(`/insights-studio/1`))}>
      <Router>
        <PublishDraftButton
          // @ts-expect-error doesn't have a path prop
          path="/insights-studio/1"
          selectedDashboard={dash1}
        />
      </Router>
    </LocationProvider>,
    {
      testState: {
        user: {
          privileges: [AllPermissions.PUBLISH_DASHBOARD],
        },
      },
    }
  );

describe('PublishDraftButton', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('calls publish with the draft dashboard ID and redirects to published URL', async () => {
    renderComponent();

    // Main publish button
    await userEvent.click(screen.queryAllByRole('button', { name: 'Publish' })[0]);
    // Confirm modal publish button
    await userEvent.click(screen.queryAllByRole('button', { name: 'Publish' })[1]);

    await waitFor(() => expect(publish).toHaveBeenCalledWith(dash1.id));
    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/insights-studio/1'));
  });
});
