import * as Reach from '@reach/router';

import * as InsightsApi from 'store/insights-studio/api';
import { render, screen, userEvent, waitFor } from 'tests/helpers';
import { AllPermissions } from 'utils/PermissionsConstants';

import { DashboardFormDrawer, DashboardFormDrawerProps } from './DashboardFormDrawer';

const onClose = jest.fn();

const testDashboard: DashboardFormDrawerProps['dashboardToEdit'] = {
  dataCards: [],
  description: 'This is the description',
  displayName: 'Dashboard 1',
  draftStatus: 'NEW',
  id: 'dash1',
  name: 'dashboard_1',
  tags: ['tag1'],
};

const create = jest.fn(() => Promise.resolve({ data: testDashboard }));
const edit = jest.fn(() => Promise.resolve({ data: undefined }));
// @ts-ignore typing of function
const createMutation = jest.spyOn(InsightsApi, 'useCreateDashboardMutation').mockReturnValue([create, {}]);
// @ts-ignore typing of function
const editMutation = jest.spyOn(InsightsApi, 'useEditDashboardMutation').mockReturnValue([edit, {}]);

const navigate = jest.spyOn(Reach, 'navigate');

const renderComponent = (dashboard?: DashboardFormDrawerProps['dashboardToEdit']) =>
  render(<DashboardFormDrawer onClose={onClose} visible dashboardToEdit={dashboard} />, {
    testState: {
      dataStudio: {
        filterCreatingStatus: {},
        filterUpdatingStatus: {},
      },
      user: {
        privileges: [AllPermissions.READ_TAG, AllPermissions.REMOVE_TAG, AllPermissions.ASSIGN_TAG],
      },
    },
  });

describe('DashboardFormDrawer', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('calls onClose when x is clicked', async () => {
    renderComponent();
    await userEvent.click(screen.getByRole('button', { name: 'Close' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('calls onClose when Cancel is clicked', async () => {
    renderComponent();
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });
  describe('Create', () => {
    it('shows correct title', () => {
      renderComponent();

      expect(screen.getByText('New Dashboard')).toBeVisible();
    });

    it('displays error message when present', async () => {
      const testError = 'Test Create Error';
      // @ts-ignore create function typing
      createMutation.mockReturnValueOnce([create, { error: { message: testError } }]);
      renderComponent();

      expect(await screen.findByText(testError)).toBeVisible();
    });

    it('can fill out form and submit', async () => {
      renderComponent();

      expect(screen.getByLabelText('Display name*')).toHaveValue('');
      await userEvent.type(screen.getByLabelText('Display name*'), testDashboard.displayName);
      await userEvent.tab();
      expect(screen.getByLabelText('Display name*')).toHaveValue(testDashboard.displayName);

      expect(screen.getByLabelText('Description')).toHaveValue('');
      await userEvent.type(screen.getByLabelText('Description'), testDashboard.description);
      expect(screen.getByLabelText('Description')).toHaveValue(testDashboard.description);

      expect(screen.getByLabelText('Tags')).toHaveValue('');
      await userEvent.type(screen.getByLabelText('Tags'), testDashboard.tags![0]);
      expect(screen.getByLabelText('Tags')).toHaveValue(testDashboard.tags![0]);
      await userEvent.click(screen.getByText('New Dashboard'));
      expect(screen.getByLabelText('Tags')).toHaveValue('');
      expect(await screen.findByText(testDashboard.tags![0])).toBeInTheDocument();

      await userEvent.click(screen.getByText('Create'));

      expect(create).toHaveBeenCalledWith({
        displayName: testDashboard.displayName,
        description: testDashboard.description,
        tags: testDashboard.tags,
      });
      await waitFor(() => expect(navigate).toHaveBeenCalledWith('/insights-studio/' + testDashboard.id));
    });
  });

  describe('Edit', () => {
    it('shows correct title', () => {
      renderComponent(testDashboard);

      expect(screen.getByText('Edit Dashboard')).toBeVisible();
    });

    it('pre-populates form with selected dashboard details', () => {
      renderComponent(testDashboard);

      expect(screen.getByLabelText('Display name*')).toHaveValue(testDashboard.displayName);
      expect(screen.getByLabelText('Description')).toHaveValue(testDashboard.description);
      expect(screen.getByText(testDashboard.tags![0])).toBeInTheDocument();
    });

    it('displays error message when present', async () => {
      const testError = 'Test Update Error';
      // @ts-ignore function typing
      editMutation.mockReturnValueOnce([edit, { error: { message: testError } }]);
      renderComponent(testDashboard);

      expect(await screen.findByText(testError)).toBeVisible();
    });

    it('calls edit dashboard when submitted', async () => {
      renderComponent(testDashboard);

      await userEvent.type(screen.getByLabelText('Display name*'), 'edit');

      await userEvent.type(screen.getByLabelText('Description'), 'edit');

      await userEvent.click(screen.getByText('Save'));

      expect(edit).toHaveBeenCalledWith({
        ...testDashboard,
        displayName: testDashboard.displayName + 'edit',
        description: testDashboard.description + 'edit',
      });
    });
  });
});
