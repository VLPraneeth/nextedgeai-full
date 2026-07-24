import AppConstants from 'utils/AppConstants';

import { RootState } from '../../reducers';
import { IdentityProvider } from './types';

export const selectSsoIdpConfig = (state: RootState, id: IdentityProvider) =>
  state.organization.sso.identityProviders[id];
export const selectSsoIdpUpdateError = (state: RootState, provider: IdentityProvider) =>
  state.organization.sso.updateError[provider];
export const selectSsoIdpFetchStatus = (state: RootState) => state.organization.sso.fetchStatus;
export const selectSsoIdpUpdateStatus = (state: RootState, provider: IdentityProvider) =>
  state.organization.sso.updateStatus[provider] || AppConstants.FETCH_STATUS.IDLE;
export const selectSsoIdpDeleteStatus = (state: RootState, provider: IdentityProvider) =>
  state.organization.sso.deleteStatus[provider] || AppConstants.FETCH_STATUS.IDLE;
