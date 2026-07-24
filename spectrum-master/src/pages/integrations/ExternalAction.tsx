//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { RouteComponentProps } from '@reach/router';
import { useEffect, useMemo } from 'react';

import CenterLayout from 'components/layout/CenterLayout';
import { useForm } from 'hooks/form';
import { useEnhancedDispatch as useDispatch } from 'hooks/redux';
import { getProfile } from 'store/user/thunks';
import { get } from 'utils/AjaxUtil';
import { tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('ExternalAction');

const ACTION_PATTERN = /http[s]?:\/\/?[^\/\s]+\/(([^\/\s]+\/){2})(.*)/;
// We have a custom header for redirect to avoid csp and override normal 302
const REDIRECT_HEADER = 'x-syncari-oauth-redirect';
const REDIRECT_METHOD = 'x-redirect-method';
const JWT_TOKEN_HEADER = 'x-jwt-token';

const ACTIONS = {
  REDIRECT: 'redirect', // Syncari redirect header
  REDIRECT_PAGE: 'redirectpage', // Basic redirect page
};

export interface ExternalActionProps extends RouteComponentProps {}

const ExternalAction = ({ location }: ExternalActionProps) => {
  const dispatch = useDispatch();
  const { formPostToPage } = useForm();
  const { action, arcadeRequest } = useMemo(() => {
    const parsedUrl = String(location?.href).match(ACTION_PATTERN);
    /* eslint-disable @typescript-eslint/no-unused-vars */
    let [_, __, action, arcadeRequest] = parsedUrl ? parsedUrl : [];
    return {
      action: action?.replace('/', ''),
      arcadeRequest: arcadeRequest && `/${arcadeRequest}`,
    };
  }, [location]);

  useEffect(() => {
    if (action === ACTIONS.REDIRECT_PAGE) {
      dispatch(getProfile()).then((profile) => {
        if (profile?.payload?.user?.id) {
          window.location.assign(arcadeRequest);
        }
      });
    } else {
      get(arcadeRequest).then((resp) => {
        if (resp.headers[REDIRECT_HEADER]) {
          if (resp.headers[REDIRECT_METHOD]?.toLowerCase() === 'post') {
            formPostToPage(resp.headers[REDIRECT_HEADER], {
              // Note: We need to make this generic but for now we're only supporting
              // zendesk sso form post redirect
              jwt: resp.headers[JWT_TOKEN_HEADER] || '',
            });
          } else {
            window.location.assign(resp.headers[REDIRECT_HEADER]);
          }
        }
      });
    }
  }, [action, arcadeRequest, dispatch, formPostToPage]);

  // TODO: Error page
  return <CenterLayout>{tn(action)}</CenterLayout>;
};

export default ExternalAction;
