import { ResponseError } from 'store/types';

import {
  OrganizationAction,
  IdentityProvider,
  IdentityProviderConfig,
  GET_ORGANIZATION_SSO_CONFIG_PENDING,
  GET_ORGANIZATION_SSO_CONFIG_FULFILLED,
  GET_ORGANIZATION_SSO_CONFIG_FAILED,
  UPDATE_ORGANIZATION_SSO_CONFIG_PENDING,
  UPDATE_ORGANIZATION_SSO_CONFIG_FULFILLED,
  UPDATE_ORGANIZATION_SSO_CONFIG_FAILED,
  DELETE_ORGANIZATION_SSO_CONFIG_PENDING,
  DELETE_ORGANIZATION_SSO_CONFIG_FULFILLED,
  DELETE_ORGANIZATION_SSO_CONFIG_FAILED,
} from './types';

export const getOrganizationSsoConfigPending = (): OrganizationAction => ({
  type: GET_ORGANIZATION_SSO_CONFIG_PENDING,
});

export const getOrganizationSsoConfigFulfilled = (
  providers: Partial<Record<IdentityProvider, IdentityProviderConfig>>
): OrganizationAction => ({
  type: GET_ORGANIZATION_SSO_CONFIG_FULFILLED,
  payload: {
    providers,
  },
});

export const getOrganizationSsoConfigFailed = (error: ResponseError): OrganizationAction => ({
  type: GET_ORGANIZATION_SSO_CONFIG_FAILED,
  payload: {
    error,
  },
});

export const updateOrganizationSsoConfigPending = (provider: IdentityProvider): OrganizationAction => ({
  type: UPDATE_ORGANIZATION_SSO_CONFIG_PENDING,
  payload: {
    provider,
  },
});

export const updateOrganizationSsoConfigFulfilled = (
  provider: IdentityProvider,
  config: IdentityProviderConfig
): OrganizationAction => ({
  type: UPDATE_ORGANIZATION_SSO_CONFIG_FULFILLED,
  payload: {
    provider,
    config,
  },
});

export const updateOrganizationSsoConfigFailed = (
  provider: IdentityProvider,
  error: ResponseError
): OrganizationAction => ({
  type: UPDATE_ORGANIZATION_SSO_CONFIG_FAILED,
  payload: {
    provider,
    error,
  },
});

export const deleteOrganizationSsoConfigPending = (provider: IdentityProvider): OrganizationAction => ({
  type: DELETE_ORGANIZATION_SSO_CONFIG_PENDING,
  payload: {
    provider,
  },
});

export const deleteOrganizationSsoConfigFulfilled = (
  provider: IdentityProvider,
  config: IdentityProviderConfig
): OrganizationAction => ({
  type: DELETE_ORGANIZATION_SSO_CONFIG_FULFILLED,
  payload: {
    provider,
    config,
  },
});

export const deleteOrganizationSsoConfigFailed = (
  provider: IdentityProvider,
  error: ResponseError
): OrganizationAction => ({
  type: DELETE_ORGANIZATION_SSO_CONFIG_FAILED,
  payload: {
    provider,
    error,
  },
});
