import { FetchStatus, ResponseError } from 'store/types';

export const GET_ORGANIZATION_SSO_CONFIG_PENDING = 'organization/sso/pending';
interface GetOrganizationSsoConfigPending {
  type: typeof GET_ORGANIZATION_SSO_CONFIG_PENDING;
}

export const GET_ORGANIZATION_SSO_CONFIG_FULFILLED = 'organization/sso/fulfilled';
interface GetOrganizationSsoConfigFulfilled {
  type: typeof GET_ORGANIZATION_SSO_CONFIG_FULFILLED;
  payload: {
    providers: Partial<Record<IdentityProvider, IdentityProviderConfig>>;
  };
}

export const GET_ORGANIZATION_SSO_CONFIG_FAILED = 'organization/sso/failed';
interface GetOrganizationSsoConfigFailed {
  type: typeof GET_ORGANIZATION_SSO_CONFIG_FAILED;
  payload: {
    error: ResponseError;
  };
}

export const UPDATE_ORGANIZATION_SSO_CONFIG_PENDING = 'organization/sso/update/pending';
interface UpdateOrganizationSsoConfigPending {
  type: typeof UPDATE_ORGANIZATION_SSO_CONFIG_PENDING;
  payload: {
    provider: IdentityProvider;
  };
}

export const UPDATE_ORGANIZATION_SSO_CONFIG_FULFILLED = 'organization/sso/update/fulfilled';
interface UpdateOrganizationSsoConfigFulfilled {
  type: typeof UPDATE_ORGANIZATION_SSO_CONFIG_FULFILLED;
  payload: {
    provider: IdentityProvider;
    config: IdentityProviderConfig;
  };
}

export const UPDATE_ORGANIZATION_SSO_CONFIG_FAILED = 'organization/sso/update/failed';
interface UpdateOrganizationSsoConfigFailed {
  type: typeof UPDATE_ORGANIZATION_SSO_CONFIG_FAILED;
  payload: {
    provider: IdentityProvider;
    error: ResponseError;
  };
}

export const DELETE_ORGANIZATION_SSO_CONFIG_PENDING = 'organization/sso/delete/pending';
interface DeleteOrganizationSsoConfigPending {
  type: typeof DELETE_ORGANIZATION_SSO_CONFIG_PENDING;
  payload: {
    provider: IdentityProvider;
  };
}

export const DELETE_ORGANIZATION_SSO_CONFIG_FULFILLED = 'organization/sso/delete/fulfilled';
interface DeleteOrganizationSsoConfigFulfilled {
  type: typeof DELETE_ORGANIZATION_SSO_CONFIG_FULFILLED;
  payload: {
    provider: IdentityProvider;
    config: IdentityProviderConfig;
  };
}

export const DELETE_ORGANIZATION_SSO_CONFIG_FAILED = 'organization/sso/delete/failed';
interface DeleteOrganizationSsoConfigFailed {
  type: typeof DELETE_ORGANIZATION_SSO_CONFIG_FAILED;
  payload: {
    provider: IdentityProvider;
    error: ResponseError;
  };
}

export type OrganizationAction =
  | GetOrganizationSsoConfigPending
  | GetOrganizationSsoConfigFulfilled
  | GetOrganizationSsoConfigFailed
  | UpdateOrganizationSsoConfigPending
  | UpdateOrganizationSsoConfigFulfilled
  | UpdateOrganizationSsoConfigFailed
  | DeleteOrganizationSsoConfigPending
  | DeleteOrganizationSsoConfigFulfilled
  | DeleteOrganizationSsoConfigFailed;

export interface IdentityProviderConfig {
  provider: IdentityProvider;
  entityId: string;
  certificate: string;
  ssoUrl: string;
}

export type IdentityProvider = 'OKTA';

export interface OrganizationState {
  sso: {
    fetchStatus: FetchStatus;
    updateStatus: Record<string, FetchStatus>;
    updateError: Record<string, ResponseError>;
    deleteStatus: Record<string, FetchStatus>;
    deleteError: Record<string, ResponseError>;
    identityProviders: Partial<Record<IdentityProvider, IdentityProviderConfig>>;
  };
}
