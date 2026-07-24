import * as React from 'react';

import { getEmptyCredentialState } from 'store/credentials';
import { ServiceCredentialTypeOptionsEnum } from 'store/credentials/types';
import { render, screen } from 'tests/helpers';
import { t } from 'utils/i18nUtil';

import CredentialModal from '../CredentialModal';

describe('CredentialModal.tsx', () => {
  test('Should render the add title if no id is provided', async () => {
    render(<CredentialModal />, {
      testState: {
        credential: getEmptyCredentialState({
          credentialModal: true,
        }),
      },
    });

    const component = await screen.findByText(t('Settings.ServiceCredentials.create_modal_title'));
    expect(component).toBeInTheDocument();
  });

  test('Should render the name, type, key and "Update Credential" title if modal data is provided', async () => {
    render(<CredentialModal />, {
      testState: {
        credential: getEmptyCredentialState({
          credentialModal: true,
          credentialData: {
            id: 'identifier',
            name: 'cred_one',
            type: ServiceCredentialTypeOptionsEnum.CLEARBIT,
            key: 'a-great-api-key',
          },
        }),
      },
    });

    const credentialInput = await screen.findByDisplayValue('cred_one');
    expect(credentialInput).toBeInTheDocument();

    const title = await screen.findByText(t('Settings.ServiceCredentials.update_modal_title'));
    expect(title).toBeInTheDocument();

    const component = await screen.findByText('Clearbit');
    expect(component).toBeInTheDocument();

    const credentialKey = await screen.findByTestId('credential-input-key');
    expect(credentialKey).toHaveValue('a-great-api-key');
  });

  test('Should render the username and password for ZoomInfo if provided', async () => {
    render(<CredentialModal />, {
      testState: {
        credential: getEmptyCredentialState({
          credentialModal: true,
          credentialData: {
            id: 'identifier',
            name: 'cred_one',
            type: ServiceCredentialTypeOptionsEnum.ZOOMINFO,
            username: 'a-great-username',
            password: 'a-great-password',
          },
        }),
      },
    });

    const credentialUsername = await screen.findByTestId('credential-input-username');
    expect(credentialUsername).toHaveValue('a-great-username');

    const credentialPassword = await screen.findByTestId('credential-input-password');
    expect(credentialPassword).toHaveValue('a-great-password');
  });
});
