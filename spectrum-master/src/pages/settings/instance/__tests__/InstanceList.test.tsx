import * as InstanceSlice from 'store/instances/slice';
import { mockedAjaxUtils, render, screen, userEvent, waitFor, within } from 'tests/helpers';
import { sleep } from 'tests/helpers';
import CapConstants from 'utils/CapConstants';
import { AllPermissions } from 'utils/PermissionsConstants';

import InstanceList from '../InstanceList';

jest.mock('utils/AjaxUtil');
const ajaxMock = mockedAjaxUtils();

interface TestOptions {
  instanceType?: InstanceSlice.InstanceType;
  roles?: string[];
}

const renderInstanceList = (options?: TestOptions) =>
  render(<InstanceList />, {
    testState: {
      instance: {
        instances: [
          {
            name: 'Master Instance',
            displayName: 'Syncari Master Instance',
            syncariId: 'syncari_admin',
            type: 'production',
            status: 'ACTIVE',
            planName: '',
            orgId: '5e0d22b47df51d37e546172e',
            orgName: 'Syncari Master',
            features: [],
          },
          {
            name: 'Sandbox Instance',
            displayName: 'Test Instance',
            syncariId: 'test_test',
            type: 'sandbox',
            status: 'ACTIVE',
            planName: '',
            orgId: '51d37e54e0d22b47df56172e',
            orgName: 'Syncari Master',
            features: [],
          },
        ],
      },
      user: {
        currentInstanceNextEdgeId: 'asdf',
        userRoles: { asdf: options?.roles ?? [CapConstants.ADMIN] },
        currentInstanceType: options?.instanceType ?? 'sandbox',
        privileges: [AllPermissions.ADD_INSTANCE, AllPermissions.DELETE_INSTANCE],
      },
    },
  });

describe('Instance List', () => {
  afterEach(() => {
    jest.resetAllMocks();
  });

  it('sets the page title', () => {
    renderInstanceList();
    expect(window.document.title.includes('Instances')).toBe(true);
  });

  it('renders a list of instances from redux store', async () => {
    renderInstanceList();

    await sleep(100);

    expect(await screen.findByText('Master Instance')).toBeVisible();
    expect(await screen.findByText('Syncari Master Instance')).toBeVisible();
    expect(await screen.findByText('Production')).toBeVisible();

    expect(await screen.findByText('Sandbox Instance')).toBeVisible();
    expect(await screen.findByText('Test Instance')).toBeVisible();
    expect(await screen.findByText('Sandbox')).toBeVisible();

    expect(screen.getByText('Create Instance').closest('button')).not.toBeDisabled();
  });

  it('triggers API call for instances', () => {
    renderInstanceList();

    expect(ajaxMock.get).toHaveBeenCalledTimes(1);
    expect(ajaxMock.get).toHaveBeenCalledWith('/arcade/api/v1/organization/instance');
  });

  it('can open create instance modal', async () => {
    const openModalAction = jest.spyOn(InstanceSlice, 'showInstanceModal');
    renderInstanceList();
    await userEvent.click(await screen.findByText('Create Instance'));

    expect(openModalAction).toHaveBeenCalledTimes(1);
    expect(openModalAction).toHaveBeenCalledWith(true);
  });

  it('can trigger a refresh of instances', async () => {
    renderInstanceList();

    expect(ajaxMock.get).toHaveBeenCalledTimes(1);
    expect(ajaxMock.get).toHaveBeenLastCalledWith('/arcade/api/v1/organization/instance');

    // There's a button and a column header both titled "Actions"
    const actionElements = await screen.findAllByText('Actions');
    await userEvent.click(actionElements[0]);
    await userEvent.click(await screen.findByText('Refresh'));

    await waitFor(() => expect(ajaxMock.get).toHaveBeenCalledTimes(2));
    expect(ajaxMock.get).toHaveBeenLastCalledWith('/arcade/api/v1/organization/instance');
  });

  it('can trigger delete instance', async () => {
    renderInstanceList();

    await userEvent.click(await screen.findByTestId('test_test-actions'));
    await userEvent.click(await screen.findByText('Delete'));
    await userEvent.type(await screen.findByPlaceholderText('Type "DELETE" to confirm'), 'DELETE');

    const modal = await screen.findByRole('dialog');

    await userEvent.click(within(modal).getByText('Delete'));

    expect(ajaxMock.deleteRequest).toHaveBeenCalledTimes(1);
  });
});
