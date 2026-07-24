import { PromiseThunkAction, ResponseError } from 'store/types';
import { deleteRequest, get, post } from 'utils/AjaxUtil';
import { getErrorMessage } from 'utils/AppUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import {
  getOrganizationSsoConfigPending,
  getOrganizationSsoConfigFulfilled,
  getOrganizationSsoConfigFailed,
  updateOrganizationSsoConfigPending,
  updateOrganizationSsoConfigFulfilled,
  updateOrganizationSsoConfigFailed,
  deleteOrganizationSsoConfigPending,
  deleteOrganizationSsoConfigFulfilled,
  deleteOrganizationSsoConfigFailed,
} from './actions';
import { IdentityProviderConfig } from './types';

export const getOrganizationSsoConfig = (orgId: string): PromiseThunkAction => (dispatch) => {
  dispatch(getOrganizationSsoConfigPending());

  return get(makeUrl(DataUrlConstants.ORGANIZATION_SSO, { orgId }))
    .then((resp) => {
      // For now we only support OKTA, but at some point in the future, this might return a map of
      // { provider: config }
      dispatch(
        getOrganizationSsoConfigFulfilled({
          OKTA: resp.data,
        })
      );
    })
    .catch((err) => {
      dispatch(getOrganizationSsoConfigFailed(getErrorMessage(err) as ResponseError));
    });
};

export const updateOrganizationSsoConfig = (orgId: string, ssoConfig: IdentityProviderConfig): PromiseThunkAction => (
  dispatch
) => {
  dispatch(updateOrganizationSsoConfigPending(ssoConfig.provider));

  return post(makeUrl(DataUrlConstants.ORGANIZATION_SSO, { orgId }), ssoConfig)
    .then((resp) => {
      dispatch(updateOrganizationSsoConfigFulfilled(ssoConfig.provider, resp.data));
    })
    .catch((err) => {
      dispatch(updateOrganizationSsoConfigFailed(ssoConfig.provider, getErrorMessage(err) as ResponseError));
    });
};

export const removeOrganizationSsoConfig = (orgId: string, ssoConfig: IdentityProviderConfig): PromiseThunkAction => (
  dispatch
) => {
  dispatch(deleteOrganizationSsoConfigPending(ssoConfig.provider));

  return deleteRequest(makeUrl(DataUrlConstants.ORGANIZATION_SSO, { orgId }))
    .then((resp) => {
      dispatch(deleteOrganizationSsoConfigFulfilled(ssoConfig.provider, resp.data));
    })
    .catch((err) => {
      dispatch(deleteOrganizationSsoConfigFailed(ssoConfig.provider, getErrorMessage(err) as ResponseError));
    });
};
