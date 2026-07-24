import { EntityFilter } from 'store/data-studio';
import { render, userEvent, screen, mockedAjaxUtils, fireEvent, waitFor } from 'tests/helpers';
import { AllPermissions } from 'utils/PermissionsConstants';

import UpdateFilterDrawer from './UpdateFilterDrawer';

const closeSpy = jest.fn();
const saveSpy = jest.fn();

jest.mock('utils/AjaxUtil');
const ajaxMock = mockedAjaxUtils();
ajaxMock.post.mockImplementation(() => Promise.resolve());
ajaxMock.put.mockImplementation(() => Promise.resolve({ success: true }));

const renderComp = (overrides?: { filter: Partial<EntityFilter> }) =>
  render(
    <UpdateFilterDrawer
      entityId="test"
      onRequestClose={closeSpy}
      onSaveFilter={saveSpy}
      filter={overrides?.filter ?? {}}
    />,
    {
      testState: {
        dataStudio: {
          filterCreatingStatus: {},
          filterUpdatingStatus: {},
        },
        user: {
          privileges: [AllPermissions.READ_TAG, AllPermissions.REMOVE_TAG, AllPermissions.ASSIGN_TAG],
        },
      },
    }
  );

describe('UpdateFilterDrawer', () => {
  afterEach(() => jest.clearAllMocks());

  it('renders New Filter by default', () => {
    renderComp();

    expect(screen.getByText('New Filter')).toBeVisible();
  });

  it('calls close function when closed from footer', async () => {
    renderComp();

    await userEvent.click(screen.getByText('Cancel'));
    expect(closeSpy).toHaveBeenCalledTimes(1);
  });

  it('calls close function when closed from x', async () => {
    renderComp();

    await userEvent.click(screen.getByLabelText('Close'));
    expect(closeSpy).toHaveBeenCalledTimes(1);
  });

  it('requires a name more than 3 characters to save', async () => {
    renderComp();

    await userEvent.type(await screen.findByLabelText('Name'), '   ');
    expect(screen.getByText(/Create/i).closest('button')).toBeDisabled();
    await userEvent.clear(await screen.findByLabelText('Name'));

    await userEvent.type(await screen.findByLabelText('Name'), 'T');
    await userEvent.click(await screen.findByText('Create'));
    expect(await screen.findByText('Name must be at least 3 characters')).toBeVisible();
    expect(ajaxMock.post).not.toHaveBeenCalled();
  });

  it('can submit the form', async () => {
    renderComp();

    expect(screen.getByText('New Filter')).toBeVisible();

    await userEvent.type(await screen.findByLabelText('Name'), 'Test Filter');
    expect(await screen.findByLabelText('Name')).toHaveValue('Test Filter');
    await userEvent.type(await screen.findByLabelText('Description'), 'A test filter');
    await userEvent.type(await screen.findByLabelText('Tags'), 'test');
    fireEvent.blur(await screen.findByLabelText('Tags'));
    expect(await screen.findByTestId('tag')).toHaveTextContent('test');
    await userEvent.click(await screen.findByLabelText('Favorite'));

    await userEvent.click(await screen.findByText('Create'));
    await waitFor(() =>
      expect(ajaxMock.post).toHaveBeenCalledWith(`/arcade/api/v1/studio/data/filters`, {
        syncariEntityId: 'test',
        criteria: undefined,
        name: 'Test Filter',
        description: 'A test filter',
        tags: ['test'],
        bookmarked: true,
      })
    );
  });

  it('can update an existing filter', async () => {
    const testFilter = {
      id: 'test-filter',
      syncariEntityId: 'test',
      criteria: undefined,
      name: 'Existing',
      description: 'An existing filter',
      tags: ['existing'],
      bookmarked: true,
    };
    renderComp({ filter: testFilter });

    // Check values are added to the form
    expect(await screen.findByLabelText('Name')).toHaveValue(testFilter.name);
    expect(await screen.findByLabelText('Description')).toHaveValue(testFilter.description);
    expect(await screen.findByTestId('tag')).toHaveTextContent(testFilter.tags[0]);
    expect(await screen.findByLabelText('Favorite')).toBeChecked();

    // Edit the form
    await userEvent.clear(await screen.findByLabelText('Name'));
    await userEvent.type(await screen.findByLabelText('Name'), 'Updated Filter');
    await userEvent.type(await screen.findByLabelText('Tags'), 'updated');
    await userEvent.click(await screen.findByLabelText('Favorite'));

    await userEvent.click(await screen.findByText('Save'));
    await waitFor(() => {
      expect(ajaxMock.put).toHaveBeenCalledWith(`/arcade/api/v1/studio/data/filters/test-filter`, {
        id: testFilter.id,
        syncariEntityId: testFilter.syncariEntityId,
        criteria: undefined,
        name: 'Updated Filter',
        description: testFilter.description,
        tags: ['existing', 'updated'],
        bookmarked: false,
      });
      expect(saveSpy).toHaveBeenCalledTimes(1);
      expect(saveSpy).toHaveBeenCalledWith({
        id: testFilter.id,
        syncariEntityId: testFilter.syncariEntityId,
        criteria: undefined,
        name: 'Updated Filter',
        description: testFilter.description,
        tags: ['existing', 'updated'],
        bookmarked: false,
      });
      expect(closeSpy).toHaveBeenCalledTimes(1);
    });
  });

  it('does not close if update fails', async () => {
    ajaxMock.put.mockImplementationOnce(() => Promise.reject());
    const testFilter = {
      id: 'test-filter',
      syncariEntityId: 'test',
      criteria: undefined,
      name: 'Existing',
      description: 'An existing filter',
      tags: ['existing'],
      bookmarked: true,
    };
    renderComp({ filter: testFilter });

    await userEvent.click(await screen.findByText('Save'));
    await waitFor(() => {
      expect(ajaxMock.put).toHaveBeenCalledTimes(1);
    });
    await waitFor(() => {
      expect(saveSpy).toHaveBeenCalledTimes(0);
      expect(closeSpy).toHaveBeenCalledTimes(0);
    });
  });
});
