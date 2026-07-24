import { server } from 'mocks/server';
import { rest } from 'msw';

import configureAppStore from 'store/configureStore';
import { Instance } from 'store/instances/slice';
import * as UserSelectors from 'store/user/selectors';
import { render, screen } from 'tests/helpers';
import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import ManageInstancesModal from './ManageInstancesModal';

const closeSpy = jest.fn();
const selectUser = jest.spyOn(UserSelectors, 'selectUser');

const renderModal = (userId: string) =>
  render(
    <ManageInstancesModal
      onRequestClose={closeSpy}
      orgInstances={{ ABCDEF: { name: 'Test Instance', syncariId: 'ABCDEF' } as Instance }}
      userId={userId}
      visible
    />,
    { store: configureAppStore() }
  );

describe('ManageInstancesModal', () => {
  it('should display a loading state on data fetching', async () => {
    server.use(
      rest.post(makeUrl(DataUrlConstants.GET_ALL_ROLES_ALL_INSTANCES), (_req, res, ctx) =>
        res.once(ctx.status(200), ctx.json({}), ctx.delay('real'))
      )
    );

    renderModal('testUser');

    expect(await screen.findByText('Loading…')).toBeInTheDocument();
  });

  it('should display a RTK Query error if the data fetching fails', async () => {
    server.use(
      rest.post(makeUrl(DataUrlConstants.GET_ALL_ROLES_ALL_INSTANCES), (_req, res, ctx) =>
        res.once(ctx.status(500), ctx.json({ message: 'This is an error' }))
      )
    );

    renderModal('testUser');

    expect(await screen.findByText('This is an error')).toBeInTheDocument();
  });

  it('should properly render when user is not an Org Admin', async () => {
    server.use(
      rest.post(makeUrl(DataUrlConstants.GET_ALL_ROLES_ALL_INSTANCES), (_req, res, ctx) =>
        res.once(
          ctx.status(200),
          ctx.json({
            ABCDEF: [
              {
                name: 'Test Role',
                users: [
                  {
                    id: 'testUser',
                    ordAdmin: false,
                  },
                ],
              },
            ],
          })
        )
      )
    );

    // @ts-ignore
    selectUser.mockReturnValue(() => ({
      firstName: 'Test',
      lastName: 'User',
    }));

    renderModal('testUser');
    expect(await screen.findByText('Modify Instance Permissions')).toBeVisible();
    expect(await screen.findByText('Test User')).toBeInTheDocument();
    expect(screen.getByTestId('Test Instance')).toBeChecked();
    expect(await screen.findByText('Test Role')).toBeVisible();
  });

  it.skip('should properly render when user is an Org Admin', async () => {
    server.use(
      rest.post(makeUrl(DataUrlConstants.GET_ALL_ROLES_ALL_INSTANCES), (_req, res, ctx) =>
        res.once(
          ctx.status(200),
          ctx.json({
            ABCDEF: [
              {
                name: 'Test Role',
                users: [
                  {
                    id: 'testUser',
                    orgAdmin: true,
                  },
                ],
              },
            ],
          })
        )
      )
    );

    renderModal('testUser');

    expect(await screen.findByText('Modify Instance Permissions')).toBeVisible();
    expect(screen.getByTestId('Test Instance')).toBeChecked();
    expect(screen.queryByText('Test Role')).toBeNull();
  });
});
