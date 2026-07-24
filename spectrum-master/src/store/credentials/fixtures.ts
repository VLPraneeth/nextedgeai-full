import AppConstants from 'utils/AppConstants';

import { ServiceCredentialsState } from './slice';

export const getEmptyCredentialState = (credential: Partial<ServiceCredentialsState>): ServiceCredentialsState => {
  return {
    fetchingCredentials: AppConstants.FETCH_STATUS.IDLE,
    credentials: [],
    credentialModal: false,
    fetchingCredentialsError: null,
    deleteCredentialStatusById: {},
    deleteCredentialErrorById: {},
    credentialData: {},
    ...credential,
  };
};
