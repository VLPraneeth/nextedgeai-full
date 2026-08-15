import { message } from 'antd';

import { SyncariThunkDispatch } from 'hooks/redux';
import { ColumnItem } from 'pages/schema-studio/ConfigureTableColumnsModal';
import { LOCAL_STORAGE_INSTANCE_ID } from 'store/instances/slice';
import ActionTypeConstants from 'utils/ActionTypeConstants';
import { deleteRequest, get, HTTP, patch, post, put } from 'utils/AjaxUtil';
import AppConstants from 'utils/AppConstants';
import { UserPrefKeys } from 'utils/AppConstants.types';
import { getErrorMessage, handleAsApplicationError } from 'utils/AppUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { t } from 'utils/i18nUtil';
import * as LoginUtil from 'utils/LoginUtil';
import { RELATIVE_URL } from 'utils/RegexUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { PromiseThunkAction, ResponseError } from '../types';
import {
  activateUserFailed,
  activateUserFulfilled,
  activateUserPending,
  deactivateUserFailed,
  deactivateUserFulfilled,
  deactivateUserPending,
  deleteUserFailed,
  deleteUserFulfilled,
  deleteUserPending,
  DP,
  forgotPasswordFailed,
  forgotPasswordFulfilled,
  forgotPasswordPending,
  getAllRolesFulfilled,
  getAllRolesPending,
  getCsrfFailed,
  getCsrfFulfilled,
  getPreferenceFailed,
  getPreferenceFulfilled,
  getPreferencePending,
  getProfileFulfilled,
  getProfilePending,
  getUserInstancesFailed,
  getUserInstancesFulfilled,
  getUserInstancesPending,
  getUsersFailed,
  getUsersFulfilled,
  getUsersPending,
  getVersionFufilled,
  inviteUserFailed,
  inviteUserFulfilled,
  inviteUserPending,
  loginFailed,
  loginFulfilled,
  logoutFulfilled,
  removeUserFailed,
  removeUserFulfilled,
  removeUserPending,
  resendInviteUserFailed,
  resendInviteUserFulfilled,
  resendInviteUserPending,
  setPasswordFailed as setPasswordFailedMessage,
  setPasswordFulfilled,
  setPasswordPending,
  setPreferenceFailed,
  setPreferenceFulfilled,
  setPreferencePending,
  SetResetPasswordFailed,
  setResetPasswordPending,
  SetResetPasswordSuccess,
  setThoughtspotToken,
  updateDataStudioColumnsForEntityFailed,
  updateDataStudioColumnsForEntityFulfilled,
  updateDataStudioColumnsForEntityPending,
  updatePasswordFailed,
  updatePasswordFulfilled,
  updatePasswordPending,
  updateProfileFailed,
  updateProfileFulfilled,
  updateProfilePending,
  updateUserFailed,
  updateUserFulfilled,
  updateUserPending,
  updateUserPrefSchemaStudioFailed,
  updateUserPrefSchemaStudioFulfilled,
  updateUserPrefSchemaStudioPending,
} from './actions';
import { ForgotPassword, LoginError, USER_PREF_SYNC_STUDIO_FIELD_FILTER } from './types';

/**
 * Get the version information of the application build
 */
export function getVersion(): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    return get('/version').then((resp) => {
      dispatch(getVersionFufilled(resp.data));
    });
  };
}

export function resetPassword({ newPwd, userId }: { newPwd: string; userId: string }): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch(setResetPasswordPending());
    const url = makeUrl(DataUrlConstants.RESET_PASSWORD, { userId });

    return put(url, { newPwd })
      .then((resp) => {
        dispatch(SetResetPasswordSuccess());
        dispatch(setPasswordFulfilled(resp.data));
      })
      .catch((error) => {
        dispatch(SetResetPasswordFailed());
        dispatch(setPasswordFailedMessage([error.response.data.message]));
      });
  };
}

interface SetPasswordParams {
  invitationId: string;
  password: string;
}

// new user - set password
export function setPassword({ password, invitationId }: SetPasswordParams): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch(setPasswordPending());

    const url = makeUrl(DataUrlConstants.SET_PASSWORD, { invitationId });

    return post(url, { password })
      .then((resp) => {
        dispatch(setPasswordFulfilled(resp.data));
      })
      .catch((error) => {
        dispatch(setPasswordFailedMessage([error.response?.data?.message]));
      });
  };
}

export function forgotPassword(emailAddress: string): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    const formBody = new FormData();
    formBody.set('email', emailAddress);

    dispatch(forgotPasswordPending());

    return post(DataUrlConstants.FORGOT_PASSWORD, formBody)
      .then((resp) => {
        if (resp.status === HTTP.OK) {
          const data: ForgotPassword = resp.data;
          dispatch(forgotPasswordFulfilled(data));
          return HTTP.OK;
        }
      })
      .catch((error) => {
        dispatch(forgotPasswordFailed(getErrorMessage(error) as ResponseError));
        return error;
      });
  };
}

export function getUsers(): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch(getUsersPending());

    return get(DataUrlConstants.USER)
      .then((resp) => {
        dispatch(getUsersFulfilled(resp.data));
      })
      .catch((error) => {
        dispatch(getUsersFailed(getErrorMessage(error) as ResponseError));
      });
  };
}

export function activateUser(userId: string): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch(activateUserPending(userId));

    return post(makeUrl(DataUrlConstants.ACTIVATE_USER, { userId }))
      .then((resp) => {
        dispatch(activateUserFulfilled(userId));

        return { success: true };
      })
      .catch((err) => {
        dispatch(activateUserFailed(userId));

        return {
          success: false,
          message: getErrorMessage(err),
          userId,
        };
      });
  };
}

export function deactivateUser(userId: string): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch(deactivateUserPending(userId));

    return post(makeUrl(DataUrlConstants.DEACTIVATE_USER, { userId }))
      .then((resp) => {
        dispatch(deactivateUserFulfilled(userId));

        return { success: true, userId };
      })
      .catch((err) => {
        dispatch(deactivateUserFailed(userId));

        return {
          success: false,
          message: err?.message,
          userId,
        };
      });
  };
}

export function deleteUser(userId: string): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch(deleteUserPending(userId));

    return deleteRequest(makeUrl(DataUrlConstants.DELETE_USER, { userId }))
      .then(() => {
        dispatch(deleteUserFulfilled(userId));

        return { success: true };
      })
      .catch((err) => {
        dispatch(deleteUserFailed(userId));

        return {
          success: false,
          message: getErrorMessage(err),
          userId,
        };
      });
  };
}

export function removeUser(userId: string): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch(removeUserPending(userId));

    return post(makeUrl(DataUrlConstants.REMOVE_USER, { userId }))
      .then(() => {
        dispatch(removeUserFulfilled(userId));

        return { success: true };
      })
      .catch((err) => {
        dispatch(removeUserFailed(userId));

        return {
          success: false,
          message: getErrorMessage(err),
          userId,
        };
      });
  };
}

export function updateUserRoles(
  userId: string,
  userRoles: Record<string, string[]>,
  ghostUser: boolean,
  orgAdmin: boolean
): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch(updateUserPending(userId));

    return patch(makeUrl(DataUrlConstants.UPDATE_USER_ROLES, { userId }), { ghostUser, userRoles, orgAdmin })
      .then(() => {
        dispatch(updateUserFulfilled(userId));
        dispatch(getUsers());

        return { success: true };
      })
      .catch((err) => {
        dispatch(updateUserFailed(userId));
        const message = getErrorMessage(err);

        return { success: false, message };
      });
  };
}

interface InviteUserParams {
  admin: boolean;
  apiUser: boolean;
  email: string;
  firstName: string;
  lastName: string;
  superAdmin: boolean;
  ghostUser: boolean;
  userRoles: Record<string, string[]>;
}

export function inviteUser(newUser: InviteUserParams): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch(inviteUserPending());

    return post(DataUrlConstants.INVITE_USER, newUser)
      .then((resp) => {
        dispatch(inviteUserFulfilled(resp.data));
        return resp;
      })
      .catch((error) => {
        const message = getErrorMessage(error);

        dispatch(inviteUserFailed(message as ResponseError));

        return {
          success: false,
          message,
        };
      })
      .finally(() => {
        dispatch(getUsers());
      });
  };
}

export function resendInvite(userId: string): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch(resendInviteUserPending(userId));

    return post(makeUrl(DataUrlConstants.RESEND_INVITE_USER, { userId }))
      .then((resp) => {
        dispatch(resendInviteUserFulfilled(userId, resp.data));

        message.success(t('Settings.Users.invite_email_sent'));
        dispatch(getUsers());
      })
      .catch((error) => {
        const errorMessage = getErrorMessage(error);

        dispatch(resendInviteUserFailed(userId, errorMessage));

        message.error(errorMessage.errorMessage);
      });
  };
}

export function getProfile(): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch(getProfilePending());

    return get(DataUrlConstants.PROFILE)
      .then((resp) => {
        // Store the instanceId in localStorage. Other tabs are listening and
        // will show a modal to refresh if this changes.
        localStorage.setItem(LOCAL_STORAGE_INSTANCE_ID, resp.data.currentInstanceNextEdgeId);
        return dispatch(getProfileFulfilled(resp.data));
      })
      .catch(handleAsApplicationError(dispatch));
  };
}

interface UpdateProfileParams {
  firstName: string;
  lastName: string;
  photo: Blob | string;
  id: string;
  timeZone: string;
  currentPassword?: string;
  newPassword?: string;
}

export function updateProfile({
  firstName,
  lastName,
  photo,
  id,
  timeZone,
  ...params
}: UpdateProfileParams): PromiseThunkAction {
  const payload = new FormData();
  payload.append('firstName', firstName);
  payload.append('lastName', lastName);
  payload.append('id', id);
  payload.append('photo', photo);
  payload.append('timeZone', timeZone);

  return (dispatch: SyncariThunkDispatch) => {
    dispatch(updateProfilePending());

    return post(DataUrlConstants.PROFILE, payload)
      .then((resp) => {
        message.success(t('Profile.profile_updated'));
        dispatch(updateProfileFulfilled(resp.data));
      })
      .catch((resp) => {
        message.error(t('Profile.profile_error'));
        dispatch(updateProfileFailed());
      });
  };
}

interface UpdatePasswordParams {
  id: string;
  currentPassword: string;
  newPassword: string;
}

export function updatePassword({ id, currentPassword, newPassword }: UpdatePasswordParams): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    const bodyParams = {
      userId: id,
      currentPwd: currentPassword,
      newPwd: newPassword,
    };

    dispatch(updatePasswordPending());

    const url = makeUrl(DataUrlConstants.UPDATE_PASSWORD, { userId: id });
    return put(url, bodyParams)
      .then((resp) => {
        message.success(t('Profile.password_updated'));
        dispatch(updatePasswordFulfilled());
      })
      .catch((error) => {
        message.error(t('Profile.password_error') + ': ' + error.response.data.message);
        dispatch(updatePasswordFailed(getErrorMessage(error) as ResponseError));
      });
  };
}

export function setUserPreference(prefKey: UserPrefKeys, prefJson: any, refresh = false): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    const url = makeUrl(DataUrlConstants.PREFERENCE, { key: prefKey });
    dispatch(setPreferencePending(prefKey, prefJson));

    return post(url, prefJson)
      .then((resp) => {
        if (refresh) {
          if (prefKey) {
            dispatch(setPreferenceFulfilled(prefKey, resp.data));
          }

          dispatch(getUserPreference());
        }
      })
      .catch((error) => {
        dispatch(setPreferenceFailed(getErrorMessage(error) as ResponseError));
      });
  };
}

export function getUserPreference(): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch(getPreferencePending());

    return get(DataUrlConstants.BASE_PREFERENCE)
      .then((resp) => {
        dispatch(getPreferenceFulfilled(resp.data));
      })
      .catch((error) => {
        dispatch(getPreferenceFailed(error?.response?.data));

        handleAsApplicationError(dispatch)(error);
      });
  };
}

export function getAllRoles(): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch(getAllRolesPending());

    return get(DataUrlConstants.GET_ALL_ROLES)
      .then((resp) => {
        dispatch(getAllRolesFulfilled(resp.data));
      })
      .catch(handleAsApplicationError(dispatch));
  };
}

export function getUserInstances(): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch(getUserInstancesPending());

    return get(DataUrlConstants.USER_INSTANCES)
      .then((resp) => {
        dispatch(getUserInstancesFulfilled(resp.data));
      })
      .catch((err) => {
        dispatch(getUserInstancesFailed(err.toString()));
      });
  };
}

export function updateSchemaStudioPreferences(prefs: any): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch(updateUserPrefSchemaStudioPending());

    let url;
    let columns;

    if ('allEntityColumns' in prefs) {
      url = DataUrlConstants.PREFERENCE_SCHEMA_STUDIO_ENTITY_COLUMNS;
      columns = prefs.allEntityColumns;
    } else {
      url = DataUrlConstants.PREFERENCE_SCHEMA_STUDIO_FIELD_COLUMNS;
      columns = prefs.allFieldColumns;
    }

    return post(url, columns)
      .then((resp) => {
        dispatch(updateUserPrefSchemaStudioFulfilled(resp.data));
      })
      .catch((err) => {
        dispatch(updateUserPrefSchemaStudioFailed(getErrorMessage(err) as ResponseError));
      });
  };
}

export function updateDataStudioColumnsForEntityId(entityId: string, columns: ColumnItem[]): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch(updateDataStudioColumnsForEntityPending(entityId));

    return post(
      makeUrl(DataUrlConstants.PREFERENCE_DATA_STUDIO, {
        entityId,
      }),
      columns
    )
      .then((resp) => {
        dispatch(updateDataStudioColumnsForEntityFulfilled(resp.data));
      })
      .catch((err) => {
        dispatch(updateDataStudioColumnsForEntityFailed(entityId, getErrorMessage(err) as ResponseError));
        message.error('Failed to save column configuration: ' + getErrorMessage(err).errorMessage);
      });
  };
}

export function updateSyncStudioFieldFiltersForEntityId(
  entityId: string,
  filterSelections: USER_PREF_SYNC_STUDIO_FIELD_FILTER[]
) {
  const url = makeUrl(DataUrlConstants.PREFERENCE_SYNC_STUDIO_FIELD_FILTERS, { entityId });
  return post(url, filterSelections);
}

export function updateSyncStudioHiddenFieldsForEntityId(entityId: string, hiddenFieldIds: string[]) {
  const url = makeUrl(DataUrlConstants.PREFERENCE_SYNC_STUDIO_HIDDEN_FIELDS, { entityId });
  return post(url, hiddenFieldIds);
}

export function updateSyncStudioPipelineViewports(pipelineId: string, viewportMatrix: number[]) {
  const url = makeUrl(DataUrlConstants.PREFERENCE_SYNC_STUDIO_PIPELINE_VIEWPORTS, { pipelineId });
  return post(url, viewportMatrix);
}

/**
 * Get the csrf for future api requests
 */
export function getCsrfToken(): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    return get(DataUrlConstants.LOGIN)
      .then((resp) => {
        const csrfToken = LoginUtil.getCsrfToken();

        if (csrfToken) {
          dispatch(getCsrfFulfilled(csrfToken));
        }
      })
      .catch(() => {
        dispatch(getCsrfFailed());
      })
      .finally(() => {
        const csrfToken = LoginUtil.getCsrfToken();

        if (csrfToken) {
          dispatch(getCsrfFulfilled(csrfToken));
        } else {
          dispatch(getCsrfFailed());
        }
      });
  };
}

/**
 * Logout the user
 */
export function logout(skipRedirect: boolean = false): PromiseThunkAction {
  const csrfToken = LoginUtil.getCsrfToken();

  return (dispatch: SyncariThunkDispatch) => {
    // Remove the current instance from local storage to avoid prompting the
    // user to change instances after logout.
    localStorage.setItem(LOCAL_STORAGE_INSTANCE_ID, '');

    // Remove any copied nodes/edges from the local storage "clipboard"
    localStorage.setItem(AppConstants.COPIED_NODES_CLIPBOARD, '');

    const formBody = new FormData();
    formBody.set(DP.CSRF_TOKEN, csrfToken);

    return post(DataUrlConstants.LOGOUT, formBody)
      .then((resp) => {
        dispatch(logoutFulfilled());
        dispatch(setThoughtspotToken(''));
      })
      .finally(() => {
        if (!skipRedirect) {
          window.location.assign(RouteConstants.LOGIN);
        }
      });
  };
}

/**
 * Log in the user
 */
function redirectAfterAuthentication(userData: any, redirectTo?: string) {
  if (userData.passwordExpired) {
    window.location.href = `${RouteConstants.PASSWORD_RESET}/${redirectTo || ''}`;
  } else if (redirectTo && RELATIVE_URL.test(redirectTo)) {
    window.location.href = redirectTo;
  } else {
    window.location.href = RouteConstants.HOME;
  }
}

export function login(username: string, password: string, redirectTo?: string): PromiseThunkAction {
  const csrfToken = LoginUtil.getCsrfToken();

  return (dispatch: SyncariThunkDispatch) => {
    const formBody = new FormData();
    formBody.set(DP.USERNAME, username);
    formBody.set(DP.PASSWORD, password);

    dispatch({
      type: ActionTypeConstants.LOGIN_PENDING,
    });

    return post(DataUrlConstants.LOGIN, formBody)
      .then(() => {
        dispatch(loginFulfilled(csrfToken, username));

        dispatch(getProfile()).then((result) => {
          const userData = result.payload.user;
          // Use window.location.href to force reload and ensure latest assets are loaded.
          redirectAfterAuthentication(userData, redirectTo);
        });
      })
      .catch((err) => {
        const loginError: LoginError = err.response?.data || {
          error: 'Unauthorized',
          message: 'Unable to sign in. Check your email and password.',
          status: 401,
          timestamp: new Date().toISOString(),
        };
        dispatch(loginFailed(loginError));
      });
  };
}

/**
 * Exchange a verified Google Identity credential for the existing NextEdge AI JWT session.
 * Google accounts must already be provisioned so tenant membership cannot be self-assigned.
 */
export function googleLogin(credential: string, redirectTo?: string): PromiseThunkAction {
  const csrfToken = LoginUtil.getCsrfToken();

  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypeConstants.LOGIN_PENDING,
    });

    return post(DataUrlConstants.GOOGLE_AUTH, { credential })
      .then(() => {
        dispatch(loginFulfilled(csrfToken, ''));
        dispatch(getProfile()).then((result) => {
          const userData = result.payload.user;
          dispatch(loginFulfilled(csrfToken, userData.email));
          redirectAfterAuthentication(userData, redirectTo);
        });
      })
      .catch((err) => {
        const loginError: LoginError = err.response?.data || {
          error: 'Unauthorized',
          message: 'This Google account is not authorized for NextEdge AI.',
          status: 401,
          timestamp: new Date().toISOString(),
        };
        dispatch(loginFailed(loginError));
      });
  };
}
