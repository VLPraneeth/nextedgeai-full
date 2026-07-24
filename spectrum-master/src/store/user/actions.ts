//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import ActionTypeConstants from 'utils/ActionTypeConstants';

import { ResponseError } from '../types';
import {
  User,
  UserAction,
  LOGIN_FULFILLED,
  LOGIN_FAILED,
  LOGIN_PENDING,
  SET_PASSWORD_PENDING,
  SET_PASSWORD_FULFILLED,
  SET_PASSWORD_FAILED,
  GET_USERS_PENDING,
  GET_USERS_FULFILLED,
  GET_USERS_FAILED,
  SHOW_INVITE_USER_MODAL,
  INVITE_USER_PENDING,
  INVITE_USER_FULFILLED,
  INVITE_USER_FAILED,
  RESEND_INVITE_USER_PENDING,
  RESEND_INVITE_USER_FULFILLED,
  RESEND_INVITE_USER_FAILED,
  GET_PROFILE_FULFILLED,
  GET_PROFILE_FAILED,
  GET_PROFILE_PENDING,
  UPDATE_PROFILE_PENDING,
  UPDATE_PROFILE_FULFILLED,
  UPDATE_PROFILE_FAILED,
  GET_PREFERENCE_PENDING,
  GET_PREFERENCE_FULFILLED,
  GET_PREFERENCE_FAILED,
  SET_PREFERENCE_PENDING,
  SET_PREFERENCE_FULFILLED,
  SET_PREFERENCE_FAILED,
  GET_ALL_ROLES_PENDING,
  GET_ALL_ROLES_FULFILLED,
  GET_ALL_ROLES_FAILED,
  UPDATE_PASSWORD_PENDING,
  UPDATE_PASSWORD_FULFILLED,
  UPDATE_PASSWORD_FAILED,
  FORGOT_PASSWORD_PENDING,
  FORGOT_PASSWORD_FULFILLED,
  FORGOT_PASSWORD_FAILED,
  GET_USER_INSTANCES_PENDING,
  GET_USER_INSTANCES_FULFILLED,
  GET_USER_INSTANCES_FAILED,
  DELETE_USER_PENDING,
  DELETE_USER_FULFILLED,
  DELETE_USER_FAILED,
  REMOVE_USER_PENDING,
  REMOVE_USER_FULFILLED,
  REMOVE_USER_FAILED,
  ACTIVATE_USER_PENDING,
  ACTIVATE_USER_FULFILLED,
  ACTIVATE_USER_FAILED,
  DEACTIVATE_USER_PENDING,
  DEACTIVATE_USER_FULFILLED,
  DEACTIVATE_USER_FAILED,
  UPDATE_USER_PENDING,
  UPDATE_USER_FULFILLED,
  UPDATE_USER_FAILED,
  HIDE_BREADCRUMBS,
  UPDATE_USER_PREF_SCHEMA_STUDIO_PENDING,
  UPDATE_USER_PREF_SCHEMA_STUDIO_FULFILLED,
  UPDATE_USER_PREF_SCHEMA_STUDIO_FAILED,
  UPDATE_DATA_STUDIO_COLUMNS_FOR_ENTITY_PENDING,
  UPDATE_DATA_STUDIO_COLUMNS_FOR_ENTITY_FULFILLED,
  UPDATE_DATA_STUDIO_COLUMNS_FOR_ENTITY_FAILED,
  SET_RESET_PASSWORD_PENDING,
  SET_RESET_PASSWORD_FAILED,
  SET_RESET_PASSWORD_SUCCESS,
  LoginError,
  ForgotPassword,
  USER_PREFERENCE_UPDATE_ENTITY_FILTER,
  SchemaStudioPreference,
  DataStudioPreference,
  SET_THOUGHTSPOT_TOKEN,
} from './types';

// Local constant data properties
export const DP = {
  USERNAME: 'username',
  PASSWORD: 'password',
  CSRF_TOKEN: '_csrf',
  // AUTHORIZATION: 'authorization'
};

export const getVersionFufilled = (versionMetadata: any): UserAction => ({
  type: ActionTypeConstants.GET_VERSION_FULFILLED,
  payload: {
    versionMetadata,
  },
});

export const logoutFulfilled = (): UserAction => ({
  type: ActionTypeConstants.LOGOUT_FULFILLED,
});

export const getCsrfFulfilled = (csrfToken: string): UserAction => ({
  type: ActionTypeConstants.GET_CSRF_FULFILLED,
  payload: { csrfToken },
});

export const getCsrfFailed = (): UserAction => ({
  type: ActionTypeConstants.GET_CSRF_FAILED,
});

/**
 * Show hide the about modal popup
 */

export const showAboutPage = (visible: boolean): UserAction => ({
  type: ActionTypeConstants.SHOW_ABOUT_PAGE,
  payload: {
    visible,
  },
});

export function showInviteUserModal(show = true): UserAction {
  return {
    type: SHOW_INVITE_USER_MODAL,
    payload: {
      visible: show,
    },
  };
}

export const hideBreadcrumbs = (hide = true): UserAction => {
  return {
    type: HIDE_BREADCRUMBS,
    payload: {
      hide,
    },
  };
};

export const loginFulfilled = (csrfToken: string, username: string): UserAction => ({
  type: LOGIN_FULFILLED,
  payload: {
    csrfToken,
    username,
  },
});

export const loginFailed = (error: LoginError): UserAction => ({
  type: LOGIN_FAILED,
  payload: {
    error,
  },
});

export const loginPending = (): UserAction => ({
  type: LOGIN_PENDING,
});

export const setResetPasswordPending = () => ({
  type: SET_RESET_PASSWORD_PENDING,
});

export const SetResetPasswordSuccess = () => ({
  type: SET_RESET_PASSWORD_SUCCESS,
});

export const SetResetPasswordFailed = () => ({
  type: SET_RESET_PASSWORD_FAILED,
});

export const setPasswordPending = (): UserAction => ({
  type: SET_PASSWORD_PENDING,
});

export const setPasswordFulfilled = (data: any): UserAction => ({
  type: SET_PASSWORD_FULFILLED,
  payload: {
    data,
  },
});

export const setPasswordFailed = (errorMessages: string[]): UserAction => ({
  type: SET_PASSWORD_FAILED,
  payload: {
    errorMessages,
  },
});

export const getUsersPending = (): UserAction => ({
  type: GET_USERS_PENDING,
});

export const getUsersFulfilled = (users: any): UserAction => ({
  type: GET_USERS_FULFILLED,
  payload: {
    users,
  },
});

export const getUsersFailed = (error: ResponseError): UserAction => ({
  type: GET_USERS_FAILED,
  payload: {
    error,
  },
});

export const inviteUserPending = (): UserAction => ({
  type: INVITE_USER_PENDING,
});

export const inviteUserFulfilled = (user: User): UserAction => ({
  type: INVITE_USER_FULFILLED,
  payload: { user },
});

export const inviteUserFailed = (error: ResponseError): UserAction => ({
  type: INVITE_USER_FAILED,
  payload: { error },
});

export const resendInviteUserPending = (userId: string): UserAction => ({
  type: RESEND_INVITE_USER_PENDING,
  payload: { userId },
});

export const resendInviteUserFulfilled = (userId: string, user: User): UserAction => ({
  type: RESEND_INVITE_USER_FULFILLED,
  payload: { userId },
});

export const resendInviteUserFailed = (userId: string, error: ResponseError): UserAction => ({
  type: RESEND_INVITE_USER_FAILED,
  payload: { error, userId },
});

export const getProfileFulfilled = (user: User): UserAction => ({
  type: GET_PROFILE_FULFILLED,
  payload: { user },
});

export const getProfileFailed = (): UserAction => ({
  type: GET_PROFILE_FAILED,
});

export const getProfilePending = (): UserAction => ({
  type: GET_PROFILE_PENDING,
});

export const updateProfilePending = (): UserAction => ({
  type: UPDATE_PROFILE_PENDING,
});

export const updateProfileFulfilled = (user: User): UserAction => ({
  type: UPDATE_PROFILE_FULFILLED,
  payload: { user },
});

export const updateProfileFailed = (): UserAction => ({
  type: UPDATE_PROFILE_FAILED,
});

export const getPreferencePending = (): UserAction => ({
  type: GET_PREFERENCE_PENDING,
});

export const getPreferenceFulfilled = (data: any): UserAction => ({
  type: GET_PREFERENCE_FULFILLED,
  payload: { data },
});

export const updateUserPreferencesEntityFilter = (entityId: string, data: any): UserAction => ({
  type: USER_PREFERENCE_UPDATE_ENTITY_FILTER,
  payload: { entityId, data },
});

export const getPreferenceFailed = (error: any): UserAction => ({
  type: GET_PREFERENCE_FAILED,
  payload: { error },
});

export const setPreferencePending = (key: string, data: any): UserAction => ({
  type: SET_PREFERENCE_PENDING,
  payload: { key, data },
});

export const setPreferenceFulfilled = (key: string, data: any): UserAction => ({
  type: SET_PREFERENCE_FULFILLED,
  payload: {
    key,
    data,
  },
});

export const setPreferenceFailed = (error: ResponseError): UserAction => ({
  type: SET_PREFERENCE_FAILED,
  payload: { error },
});

export const forgotPasswordInitialize = (): UserAction => ({
  type: FORGOT_PASSWORD_PENDING,
});

export const getAllRolesPending = (): UserAction => ({
  type: GET_ALL_ROLES_PENDING,
});

export const getAllRolesFulfilled = (allRoles: any): UserAction => ({
  type: GET_ALL_ROLES_FULFILLED,
  payload: { allRoles },
});

export const getAllRolesFailed = (): UserAction => ({
  type: GET_ALL_ROLES_FAILED,
});

export const updatePasswordPending = (): UserAction => ({
  type: UPDATE_PASSWORD_PENDING,
});

export const updatePasswordFulfilled = (): UserAction => ({
  type: UPDATE_PASSWORD_FULFILLED,
});

export const updatePasswordFailed = (error: ResponseError): UserAction => ({
  type: UPDATE_PASSWORD_FAILED,
  payload: { error },
});

export const forgotPasswordPending = (): UserAction => ({
  type: FORGOT_PASSWORD_PENDING,
});

export const forgotPasswordFulfilled = (data: ForgotPassword): UserAction => ({
  type: FORGOT_PASSWORD_FULFILLED,
  payload: {
    data,
  },
});

export const forgotPasswordFailed = (error: ResponseError): UserAction => ({
  type: FORGOT_PASSWORD_FAILED,
  payload: {
    error,
  },
});

export const getUserInstancesPending = (): UserAction => ({
  type: GET_USER_INSTANCES_PENDING,
});

export const getUserInstancesFulfilled = (instances: any): UserAction => ({
  type: GET_USER_INSTANCES_FULFILLED,
  payload: { instances },
});

export const getUserInstancesFailed = (errorMessage: string): UserAction => ({
  type: GET_USER_INSTANCES_FAILED,
  payload: { errorMessage },
});

export const deleteUserPending = (userId: string): UserAction => ({
  type: DELETE_USER_PENDING,
  payload: {
    userId,
  },
});

export const deleteUserFulfilled = (userId: string): UserAction => ({
  type: DELETE_USER_FULFILLED,
  payload: {
    userId,
  },
});

export const deleteUserFailed = (userId: string): UserAction => ({
  type: DELETE_USER_FAILED,
  payload: {
    userId,
  },
});

export const removeUserPending = (userId: string): UserAction => ({
  type: REMOVE_USER_PENDING,
  payload: {
    userId,
  },
});

export const removeUserFulfilled = (userId: string): UserAction => ({
  type: REMOVE_USER_FULFILLED,
  payload: {
    userId,
  },
});

export const removeUserFailed = (userId: string): UserAction => ({
  type: REMOVE_USER_FAILED,
  payload: {
    userId,
  },
});

export const activateUserPending = (userId: string): UserAction => ({
  type: ACTIVATE_USER_PENDING,
  payload: {
    userId,
  },
});

export const activateUserFulfilled = (userId: string): UserAction => ({
  type: ACTIVATE_USER_FULFILLED,
  payload: {
    userId,
  },
});

export const activateUserFailed = (userId: string): UserAction => ({
  type: ACTIVATE_USER_FAILED,
  payload: {
    userId,
  },
});

export const deactivateUserPending = (userId: string): UserAction => ({
  type: DEACTIVATE_USER_PENDING,
  payload: {
    userId,
  },
});

export const deactivateUserFulfilled = (userId: string): UserAction => ({
  type: DEACTIVATE_USER_FULFILLED,
  payload: { userId },
});

export const deactivateUserFailed = (userId: string): UserAction => ({
  type: DEACTIVATE_USER_FAILED,
  payload: {
    userId,
  },
});

export const updateUserPending = (userId: string): UserAction => ({
  type: UPDATE_USER_PENDING,
  payload: {
    userId,
  },
});

export const updateUserFulfilled = (userId: string): UserAction => ({
  type: UPDATE_USER_FULFILLED,
  payload: {
    userId,
  },
});

export const updateUserFailed = (userId: string): UserAction => ({
  type: UPDATE_USER_FAILED,
  payload: {
    userId,
  },
});

export const updateUserPrefSchemaStudioPending = (): UserAction => ({
  type: UPDATE_USER_PREF_SCHEMA_STUDIO_PENDING,
});

export const updateUserPrefSchemaStudioFulfilled = (data: { schemaStudio: SchemaStudioPreference }): UserAction => ({
  type: UPDATE_USER_PREF_SCHEMA_STUDIO_FULFILLED,
  payload: { data },
});

export const updateUserPrefSchemaStudioFailed = (error: ResponseError): UserAction => ({
  type: UPDATE_USER_PREF_SCHEMA_STUDIO_FAILED,
  payload: { error },
});

export const updateDataStudioColumnsForEntityPending = (entityId: string): UserAction => ({
  type: UPDATE_DATA_STUDIO_COLUMNS_FOR_ENTITY_PENDING,
  payload: { entityId },
});

export const updateDataStudioColumnsForEntityFulfilled = (data: { dataStudio: DataStudioPreference }): UserAction => ({
  type: UPDATE_DATA_STUDIO_COLUMNS_FOR_ENTITY_FULFILLED,
  payload: { data },
});

export const updateDataStudioColumnsForEntityFailed = (entityId: string, error: ResponseError): UserAction => ({
  type: UPDATE_DATA_STUDIO_COLUMNS_FOR_ENTITY_FAILED,
  payload: { entityId, error },
});

export const setThoughtspotToken = (thoughtspotToken: string): UserAction => ({
  type: SET_THOUGHTSPOT_TOKEN,
  payload: {
    thoughtspotToken,
  },
});
