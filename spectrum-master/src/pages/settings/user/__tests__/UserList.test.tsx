// @ts-nocheck

import { Instance } from 'store/instances/slice';
import * as UserActions from 'store/user/actions';
import { User, UserState } from 'store/user/types';
import { mockedAjaxUtils, queryByDataAttribute, render, screen, userEvent, waitFor, within } from 'tests/helpers';
import CapConstants from 'utils/CapConstants';
import DataUrlConstants from 'utils/DataUrlConstants';
import { AllPermissions } from 'utils/PermissionsConstants';

import UserList from '../UserList';

jest.mock('utils/AjaxUtil');

const usersListRequest = jest.fn();

const ajaxMock = mockedAjaxUtils();
ajaxMock.get.mockImplementation((request) => {
  if (request === DataUrlConstants.USER) {
    usersListRequest(request);
  }
  return Promise.resolve();
});

ajaxMock.post.mockImplementation(() => Promise.resolve());
ajaxMock.deleteRequest.mockImplementation(() => Promise.resolve());

const testInstances: Partial<Instance>[] = [
  {
    syncariId: '12345a',
    name: 'Production',
    type: 'production',
    orgId: '9876bb',
    orgName: 'Test Organization',
  },
  {
    syncariId: '12543a',
    name: 'Staging',
    type: 'sandbox',
    orgId: '9876bb',
    orgName: 'Test Organization',
  },
];

const randomItem = (xs) => xs[Math.floor(Math.random() * xs.length)];
const randomString = () => Math.random().toString(36);
const randomUserRoles = () => {
  const instance = randomItem(testInstances);

  return {
    [instance.syncariId]: [randomItem([CapConstants.VIEWER, CapConstants.ADMIN])],
  };
};

const makeUser = ({
  id = randomString(),
  firstName = randomString(),
  lastName = randomString(),
  email = randomString(),
  status = 'ACTIVE',
  userRoles = randomUserRoles(),
  orgId = testInstances[0].orgId || '',
  isApiUser = false,
}): Partial<User> => ({
  id,
  firstName,
  lastName,
  email,
  status,
  userRoles,
  orgId,
  isApiUser,
});

const testUsers = Array.from({ length: 10 }, () => makeUser({}));

const renderUserList = (userOptions?: UserState) =>
  render(<UserList />, {
    testState: {
      instance: {
        instances: testInstances,
      },
      user: {
        id: '12345667',
        firstName: 'Test',
        lastName: 'User',
        email: 'user@test.com',
        users: testUsers,
        currentInstanceNextEdgeId: testInstances[0].syncariId,
        currentInstanceName: testInstances[0].name,
        currentInstanceType: testInstances[0].type,
        instances: testInstances,
        orgId: testInstances[0].orgId,
        orgName: testInstances[0].orgName,
        versionMetadata: {
          arcadeTarget: 'https://localhost:3000',
        },
        userUpdatesPending: [],
        ...userOptions,
        privileges: [
          AllPermissions.LIST_ROLES,
          AllPermissions.REINVITE_USER,
          AllPermissions.DELETE_USR,
          AllPermissions.INVITE_USER,
          AllPermissions.ACTIVATE_USER,
          AllPermissions.DEACTIVATE_USER,
          AllPermissions.REMOVE_USER,
        ],
        isSuperAdmin: true,
      },
    },
  });

describe('UserList', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('sets page title', () => {
    renderUserList();
    expect(window.document.title.includes('Users')).toBe(true);
  });

  it('triggers API call for users and instances', () => {
    renderUserList();

    expect(ajaxMock.get).toHaveBeenCalledTimes(2);
    expect(ajaxMock.get).toHaveBeenCalledWith('/arcade/api/v1/organization/instance');
    expect(ajaxMock.get).toHaveBeenCalledWith('/arcade/api/v1/organization/users');
  });

  it('renders a list of Users from redux store', async () => {
    renderUserList();

    // check each user row
    for (let user of testUsers) {
      const userRow = await screen.findByText(user.firstName);
      const userRowQueries = within(userRow.parentNode);

      expect(await userRowQueries.findByText(user.firstName)).toBeVisible();
      expect(await userRowQueries.findByText(user.lastName)).toBeVisible();
      expect(await userRowQueries.findByText(user.status.toLowerCase())).toBeVisible();
    }
  });

  it('Edit modal opens, shows inputs', async () => {
    renderUserList();

    const manageButtons = await screen.findAllByText('Edit');
    await userEvent.click(manageButtons[0]);

    expect(await screen.findByRole('dialog')).toBeVisible();
  });

  it('can trigger Invite User modal', async () => {
    const showInviteUserModal = jest.spyOn(UserActions, 'showInviteUserModal');
    renderUserList();

    await userEvent.click(screen.getByText('Invite User'));

    expect(showInviteUserModal).toHaveBeenCalledTimes(1);
    expect(showInviteUserModal).toHaveBeenCalledWith(true);
  });

  it('shows the Api User badge for API Users', async () => {
    const apiUser = makeUser({ isApiUser: true });
    renderUserList({ users: [apiUser] });

    expect(await screen.findByText('Api User')).toBeInTheDocument();
  });

  describe('Actions Menu', () => {
    describe('Refresh Users', () => {
      it('can trigger a refresh of users list', async () => {
        renderUserList();

        expect(usersListRequest).toHaveBeenCalledTimes(1); // Once on initial render

        await userEvent.click(await screen.findByText('Actions'));
        await userEvent.click(await screen.findByText('Refresh'));
        expect(usersListRequest).toHaveBeenCalledTimes(2); // Once again on refresh
      });

      it('is disabled if updates are pending', async () => {
        renderUserList({ userUpdatesPending: ['one'] });
        await userEvent.click(screen.getByText('Actions'));
        expect(await screen.findByText('Refresh')).toHaveAttribute('aria-disabled', 'true');
      });
    });

    describe('Dectivate Users', () => {
      it('can trigger deactivate user', async () => {
        const { container } = renderUserList();

        const row = queryByDataAttribute(container, 'data-row-key', testUsers[0].id);
        await userEvent.click(within(row).getByRole('checkbox'));
        await userEvent.click(await screen.findByText('Actions'));
        await userEvent.click(await screen.findByText('Deactivate User'));

        await waitFor(() =>
          expect(ajaxMock.post).toHaveBeenCalledWith(`/arcade/api/v1/organization/user/${testUsers[0].id}/deactivate`)
        );
      });

      it('can trigger deactivate multiple users', async () => {
        const { container } = renderUserList();

        for await (const user of testUsers) {
          const row = queryByDataAttribute(container, 'data-row-key', user.id);
          await userEvent.click(await within(row).findByRole('checkbox'));
        }
        await userEvent.click(await screen.findByText('Actions'));
        await userEvent.click(await screen.findByText('Deactivate 10 Users'));

        for (const user of testUsers) {
          expect(ajaxMock.post).toHaveBeenCalledWith(`/arcade/api/v1/organization/user/${user.id}/deactivate`);
        }
      });

      it('displays errors when users are not deactivated', async () => {
        ajaxMock.post.mockImplementationOnce(() => Promise.reject());
        const { container } = renderUserList();

        const row = queryByDataAttribute(container, 'data-row-key', testUsers[0].id);
        await userEvent.click(within(row).getByRole('checkbox'));
        await userEvent.click(await screen.findByText('Actions'));
        await userEvent.click(await screen.findByText('Deactivate User'));

        expect(ajaxMock.post).toHaveBeenCalledWith(`/arcade/api/v1/organization/user/${testUsers[0].id}/deactivate`);

        expect(
          await screen.findByText(`Could not deactivate user ${testUsers[0].firstName} ${testUsers[0].lastName}`)
        ).toBeVisible();
      });

      it("doesn't allow changes to users in other orgs than current user", async () => {
        const userInOtherOrg = makeUser({ id: 'different', orgId: 'different' });
        const { container } = renderUserList({ users: [userInOtherOrg] });

        const row = queryByDataAttribute(container, 'data-row-key', userInOtherOrg.id);
        await userEvent.click(within(row).getByRole('checkbox'));
        await userEvent.click(await screen.findByText('Actions'));
        await userEvent.click(await screen.findByText('Deactivate User'));

        expect(ajaxMock.post).not.toHaveBeenCalled();
        expect(
          await screen.findByText(`Could not deactivate user ${userInOtherOrg.firstName} ${userInOtherOrg.lastName}`)
        ).toBeVisible();
      });
    });

    describe('Activate Users', () => {
      it('can trigger activate user', async () => {
        const inactiveUsers = Array.from({ length: 2 }, (_, index) => makeUser({ status: 'INACTIVE' }));
        const { container } = renderUserList({ users: inactiveUsers });

        const row = queryByDataAttribute(container, 'data-row-key', inactiveUsers[0].id);
        await userEvent.click(within(row).getByRole('checkbox'));
        await userEvent.click(await screen.findByText('Actions'));
        await userEvent.click(await screen.findByText('Activate User'));

        await waitFor(() =>
          expect(ajaxMock.post).toHaveBeenCalledWith(`/arcade/api/v1/organization/user/${inactiveUsers[0].id}/activate`)
        );
      });

      it('can trigger activate multiple users', async () => {
        const inactiveUsers = Array.from({ length: 2 }, (_, index) => makeUser({ status: 'INACTIVE' }));
        const { container } = renderUserList({ users: inactiveUsers });

        for await (const user of inactiveUsers) {
          const row = queryByDataAttribute(container, 'data-row-key', user.id);
          await userEvent.click(await within(row).findByRole('checkbox'));
        }
        await userEvent.click(await screen.findByText('Actions'));
        await userEvent.click(await screen.findByText(`Activate ${inactiveUsers.length} Users`));

        for (const user of inactiveUsers) {
          expect(ajaxMock.post).toHaveBeenCalledWith(`/arcade/api/v1/organization/user/${user.id}/activate`);
        }
      });

      it('displays error when users are not activated', async () => {
        ajaxMock.post.mockImplementationOnce(() => Promise.reject());
        const inactiveUsers = Array.from({ length: 2 }, (_, index) => makeUser({ status: 'INACTIVE' }));
        const { container } = renderUserList({ users: inactiveUsers });

        const testUser = inactiveUsers[0];

        const row = queryByDataAttribute(container, 'data-row-key', testUser.id);
        await userEvent.click(within(row).getByRole('checkbox'));
        await userEvent.click(await screen.findByText('Actions'));
        await userEvent.click(await screen.findByText('Activate User'));

        expect(ajaxMock.post).toHaveBeenCalledWith(`/arcade/api/v1/organization/user/${testUser.id}/activate`);
        expect(
          await screen.findByText(`Could not activate user ${testUser.firstName} ${testUser.lastName}`)
        ).toBeVisible();
      });
    });

    describe('Delete Users', () => {
      it('can trigger delete user', async () => {
        const { container } = renderUserList();

        const row = queryByDataAttribute(container, 'data-row-key', testUsers[0].id);
        await userEvent.click(within(row).getByRole('checkbox'));
        await userEvent.click(await screen.findByText('Actions'));
        await userEvent.click(await screen.findByText('Delete User'));

        await waitFor(() =>
          expect(ajaxMock.deleteRequest).toHaveBeenCalledWith(`/arcade/api/v1/organization/user/${testUsers[0].id}`)
        );
      });

      it('can trigger delete multiple users', async () => {
        const { container } = renderUserList();

        for await (const user of testUsers) {
          const row = queryByDataAttribute(container, 'data-row-key', user.id);
          await userEvent.click(await within(row).findByRole('checkbox'));
        }
        await userEvent.click(await screen.findByText('Actions'));
        await userEvent.click(await screen.findByText(`Delete ${testUsers.length} Users`));

        for (const user of testUsers) {
          expect(ajaxMock.deleteRequest).toHaveBeenCalledWith(`/arcade/api/v1/organization/user/${user.id}`);
        }
      });

      it('displays error when users are not deleted', async () => {
        ajaxMock.deleteRequest.mockImplementationOnce(() => Promise.reject());
        const { container } = renderUserList();

        const testUser = testUsers[0];

        const row = queryByDataAttribute(container, 'data-row-key', testUser.id);
        await userEvent.click(within(row).getByRole('checkbox'));
        await userEvent.click(await screen.findByText('Actions'));
        await userEvent.click(await screen.findByText('Delete User'));

        expect(ajaxMock.deleteRequest).toHaveBeenCalledWith(`/arcade/api/v1/organization/user/${testUser.id}`);
        expect(
          await screen.findByText(`Could not delete user ${testUser.firstName} ${testUser.lastName}`)
        ).toBeVisible();
      });
    });
  });

  describe('Remove User from Subscription', () => {
    it('can trigger remove user', async () => {
      const { container } = renderUserList();

      const row = queryByDataAttribute(container, 'data-row-key', testUsers[0].id);
      await userEvent.click(within(row).getByRole('checkbox'));
      await userEvent.click(await screen.findByText('Actions'));
      await userEvent.click(await screen.findByText('Remove User from Subscription'));

      await waitFor(() =>
        expect(ajaxMock.post).toHaveBeenCalledWith(`/arcade/api/v1/organization/user/${testUsers[0].id}/remove`)
      );
    });

    it('can trigger delete multiple users', async () => {
      const { container } = renderUserList();

      for await (const user of testUsers) {
        const row = queryByDataAttribute(container, 'data-row-key', user.id);
        await userEvent.click(await within(row).findByRole('checkbox'));
      }
      await userEvent.click(await screen.findByText('Actions'));
      await userEvent.click(await screen.findByText(`Remove ${testUsers.length} Users from Subscription`));

      for (const user of testUsers) {
        expect(ajaxMock.post).toHaveBeenCalledWith(`/arcade/api/v1/organization/user/${user.id}/remove`);
      }
    });

    it('displays error when users are not deleted', async () => {
      ajaxMock.post.mockImplementationOnce(() => Promise.reject());
      const { container } = renderUserList();

      const testUser = testUsers[0];

      const row = queryByDataAttribute(container, 'data-row-key', testUser.id);
      await userEvent.click(within(row).getByRole('checkbox'));
      await userEvent.click(await screen.findByText('Actions'));
      await userEvent.click(await screen.findByText('Remove User from Subscription'));

      expect(ajaxMock.post).toHaveBeenCalledWith(`/arcade/api/v1/organization/user/${testUser.id}/remove`);
      expect(await screen.findByText(`Could not remove user ${testUser.firstName} ${testUser.lastName}`)).toBeVisible();
    });
  });

  describe('Resend Invite', () => {
    it('can trigger resend invite for a pending user', async () => {
      const pendingUser = makeUser({ status: 'PENDING' });
      const { container } = renderUserList({ users: [pendingUser] });

      const row = queryByDataAttribute(container, 'data-row-key', pendingUser.id);
      await userEvent.click(within(row).getByText('Reinvite'));

      await waitFor(() =>
        expect(ajaxMock.post).toHaveBeenCalledWith(`/arcade/api/v1/organization/user/reinvite/${pendingUser.id}`)
      );
    });
  });
});
