import * as Reach from '@reach/router';
import { dash1 } from 'mocks/fixtures/insights';

import * as InsightsApi from 'store/insights-studio/api';
import { render, screen, userEvent } from 'tests/helpers';
import { AllPermissions } from 'utils/PermissionsConstants';

import { CreateDraftButton } from './CreateDraftButton';

const { createHistory, createMemorySource, LocationProvider, Router } = Reach;

const navigate = jest.spyOn(Reach, 'navigate');
const createDraft = jest.fn(() => Promise.resolve({ data: undefined }));
jest
  .spyOn(InsightsApi, 'useCreateDraftDashboardMutation')
  // @ts-expect-error typing of function
  .mockReturnValue([createDraft, {}]);

const renderComponent = () =>
  render(
    <LocationProvider history={createHistory(createMemorySource(`/insights-studio/1`))}>
      <Router>
        <CreateDraftButton
          // @ts-expect-error doesn't have a path prop
          path="/insights-studio/1"
          selectedDashboard={dash1}
        />
      </Router>
    </LocationProvider>,
    {
      testState: {
        user: {
          privileges: [AllPermissions.UPDATE_DASHBOARD],
        },
      },
    }
  );

describe('CreateDraftButton', () => {
  it('calls publish with the draft dashboard ID and redirects to published URL', async () => {
    renderComponent();

    await userEvent.click(screen.getByRole('button', { name: 'Create Draft' }));

    expect(createDraft).toHaveBeenCalledWith(dash1.id);
    setTimeout(() => {
      expect(navigate).toHaveBeenCalledWith('insights-studio/1');
    }, 1);
  });
});
