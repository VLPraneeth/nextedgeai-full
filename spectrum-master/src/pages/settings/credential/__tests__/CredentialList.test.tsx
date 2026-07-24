import * as CredentialSlice from 'store/credentials/slice';
import { ServiceCredentialTypeOptionsEnum } from 'store/credentials/types';
import { mockedAjaxUtils, render, screen, sleep, userEvent, waitFor, within } from 'tests/helpers';

import CredentialList from '../CredentialList';

jest.mock('utils/AjaxUtil');

const ajaxMock = mockedAjaxUtils();
const showCredentialModal = jest.spyOn(CredentialSlice, 'showCredentialModal');

const renderPage = () =>
  render(<CredentialList />, {
    testState: {
      credential: {
        credentials: [
          {
            id: 'one-id',
            key: 'one-key',
            name: 'one-name',
            type: ServiceCredentialTypeOptionsEnum.CLEARBIT,
          },
          {
            id: 'two-id',
            key: 'two-key',
            name: 'two-name',
            type: ServiceCredentialTypeOptionsEnum.CLEARBIT,
          },
        ],
      },
    },
  });

describe('CredentialList.tsx', () => {
  afterEach(() => jest.clearAllMocks());

  it('sets the page title', () => {
    renderPage();
    expect(window.document.title.includes('Service Credentials')).toBe(true);
  });

  it('requests credentials ', () => {
    renderPage();
    expect(ajaxMock.get).toHaveBeenCalledTimes(1);
    expect(ajaxMock.get).toHaveBeenCalledWith('/arcade/api/v1/service/credential');
  });

  it('Renders the names of all credentials from redux store', async () => {
    renderPage();

    expect(await screen.findByText('one-name')).toBeVisible();
    expect(await screen.findByText('two-name')).toBeVisible();
  });

  it('can trigger delete credential', async () => {
    renderPage();

    await sleep(100);

    await userEvent.click(await screen.findByTestId('one-name-menu'));
    await userEvent.click(await screen.findByText('Delete'));
    await userEvent.click(within(screen.getByRole('dialog')).getByText('Delete'));

    await waitFor(() => expect(ajaxMock.deleteRequest).toHaveBeenCalledTimes(1));
  });

  it('can trigger the update credential modal', async () => {
    renderPage();

    await userEvent.click(await screen.findByTestId('one-name-menu'));
    await userEvent.click(await screen.findByText('Update'));

    await waitFor(() => expect(showCredentialModal).toHaveBeenCalledTimes(1));
    expect(showCredentialModal).toHaveBeenLastCalledWith({ visible: true, credentialId: 'one-id' });
  });

  it('can trigger create credential modal', async () => {
    renderPage();

    await userEvent.click(await screen.findByText('Add Credential'));

    await waitFor(() => expect(showCredentialModal).toHaveBeenCalledTimes(1));
    expect(showCredentialModal).toHaveBeenLastCalledWith({ visible: true });
  });
});
