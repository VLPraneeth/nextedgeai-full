import * as ReachRouter from '@reach/router';

import { mockedAjaxUtils, render, screen, userEvent, waitFor } from 'tests/helpers';
import CapConstants from 'utils/CapConstants';

import Specter from './Specter';

jest.mock('utils/AjaxUtil');

const ajaxMock = mockedAjaxUtils();
ajaxMock.get.mockImplementation(() => Promise.resolve());
ajaxMock.post.mockImplementation(() => Promise.resolve());
const navigate = jest.spyOn(ReachRouter, 'navigate');

const renderPage = (roles?: string[]) =>
  render(<Specter />, {
    testState: {
      specter: { enableSpecterDebuggingStatus: false },
      user: { userRoles: { asdf: roles ?? [CapConstants.SUPER_ADMIN] }, currentInstanceNextEdgeId: 'asdf' },
    },
  });

describe('CredentialList.tsx', () => {
  afterEach(() => jest.clearAllMocks());

  it('sets the page title', () => {
    renderPage();
    expect(window.document.title.includes('Specter')).toBe(true);
  });

  it('can fill out form and trigger setOauth action', async () => {
    renderPage();

    await userEvent.type(await screen.findByLabelText('From NextEdge ID'), 'old');
    await userEvent.type(await screen.findByLabelText('To NextEdge ID'), 'new');
    await userEvent.click(await screen.findByText('Set Impartner Oauth'));

    await waitFor(() => expect(ajaxMock.post).toHaveBeenCalledTimes(1));
    expect(ajaxMock.post).toHaveBeenCalledWith('/arcade/api/v1/specter/setOauthConfig/old/new');
  });

  it('redirects any non-super admin to base settings', () => {
    renderPage([CapConstants.INSTANCE_ADMIN]);
    expect(navigate).toHaveBeenCalledWith('/settings');
  });
});
