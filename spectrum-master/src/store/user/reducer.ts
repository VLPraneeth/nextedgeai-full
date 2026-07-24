//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import produce, { Draft } from 'immer';

import ActionTypeConstants from 'utils/ActionTypeConstants';
import AppConstants from 'utils/AppConstants';
import { isGatewayTimeoutError } from 'utils/AppUtil';
import { t } from 'utils/i18nUtil';
import { getReducerDefaultValues } from 'utils/LocalStorageUtil';

import {
  UserState,
  UserAction,
  LOGIN_FULFILLED,
  LOGIN_FAILED,
  SET_PASSWORD_FULFILLED,
  SET_PASSWORD_FAILED,
  GET_USERS_PENDING,
  GET_USERS_FULFILLED,
  SHOW_INVITE_USER_MODAL,
  INVITE_USER_PENDING,
  INVITE_USER_FULFILLED,
  INVITE_USER_FAILED,
  RESEND_INVITE_USER_PENDING,
  RESEND_INVITE_USER_FULFILLED,
  RESEND_INVITE_USER_FAILED,
  GET_PROFILE_FULFILLED,
  UPDATE_PROFILE_PENDING,
  UPDATE_PROFILE_FULFILLED,
  GET_PREFERENCE_PENDING,
  GET_PREFERENCE_FULFILLED,
  GET_PREFERENCE_FAILED,
  SET_PREFERENCE_PENDING,
  SET_PREFERENCE_FULFILLED,
  SET_PREFERENCE_FAILED,
  FORGOT_PASSWORD_INITIALIZE,
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
  UPDATE_USER_PREF_SCHEMA_STUDIO_FULFILLED,
  UPDATE_DATA_STUDIO_COLUMNS_FOR_ENTITY_FULFILLED,
  SET_RESET_PASSWORD_PENDING,
  PasswordResetStatusOptions,
  SET_RESET_PASSWORD_SUCCESS,
  SET_RESET_PASSWORD_FAILED,
  USER_PREFERENCE_UPDATE_ENTITY_FILTER,
  SET_THOUGHTSPOT_TOKEN,
} from './types';

const { FETCH_STATUS, REDUCER_NAME, USER_PREF, USER_STATUS } = AppConstants;

const defaultPrefResult = {
  entityGraph: undefined,
};

function _getUserPreference(data: any = {}) {
  const supportedPrefs = Object.values(USER_PREF);

  return supportedPrefs.reduce((acc, pref) => {
    return {
      ...acc,
      [pref]: data[pref],
    };
  }, defaultPrefResult);
}

export function _getDefaultState() {
  return {
    ...getReducerDefaultValues(REDUCER_NAME.USER),
    errorMessage: '',
    errorMessages: [],
    userPref: {
      // NOTE: Undefined is special here. this means we have not
      // grabbed the entity graph yet.
      entityGraph: undefined,
      dataStudio: {
        columns: {},
      },
      schemaStudio: {},
    },
    aboutModalVisible: false,
    allRoles: [],
    currentInstanceName: '',
    currentInstanceNextEdgeId: '',
    currentInstanceType: 'production',
    fetchingLoginStatus: FETCH_STATUS.IDLE,
    fetchingUserInstances: FETCH_STATUS.IDLE,
    fetchingUserInstancesError: null,
    fetchingUsers: false,
    instances: [],
    inviteUserErrorMessage: null,
    inviteUserModalVisible: false,
    ghosted: false,
    passwordUpdating: false,
    passwordResetStatus: '',
    profileUpdating: false,
    resendInviteUserErrorMessage: '',
    resendingUserInvites: [],
    sendingInviteUser: false,
    settingPassword: true,
    status: '',
    userPreferenceFetching: false,
    userRoles: {},
    userUpdatesPending: [],
    users: [],
    versionMetadata: {},
  };
}

const reducer = produce((draft: Draft<UserState>, action: UserAction) => {
  switch (action.type) {
    // TODO: unused?
    case ActionTypeConstants.GET_CSRF_FULFILLED:
      // @ts-ignore
      draft.csrfToken = action.csrfToken;
      break;
    // TODO: unused?
    case ActionTypeConstants.GET_CSRF_FAILED:
      draft.csrfToken = undefined;
      break;
    case ActionTypeConstants.LOGIN_PENDING:
      draft.fetchingLoginStatus = FETCH_STATUS.LOADING;
      break;
    case LOGIN_FULFILLED:
      draft.fetchingLoginStatus = FETCH_STATUS.SUCCESS;
      draft.csrfToken = action.payload.csrfToken;
      draft.username = action.payload.username;
      draft.errorMessage = '';
      break;
    case LOGIN_FAILED:
      draft.fetchingLoginStatus = FETCH_STATUS.ERROR;
      draft.errorMessage = action.payload.error.message;
      break;
    case ActionTypeConstants.LOGOUT_FULFILLED:
      draft.errorMessage = '';
      break;
    case ActionTypeConstants.SHOW_ABOUT_PAGE:
      draft.aboutModalVisible = action.payload.visible;
      break;
    case ActionTypeConstants.GET_VERSION_FULFILLED:
      draft.versionMetadata = action.payload.versionMetadata;
      break;
    case GET_USERS_PENDING:
      draft.fetchingUsers = true;
      break;
    case GET_USERS_FULFILLED:
      draft.users = action.payload.users;
      draft.fetchingUsers = false;
      break;
    case SHOW_INVITE_USER_MODAL:
      draft.inviteUserModalVisible = action.payload.visible;
      break;
    case SET_RESET_PASSWORD_PENDING:
      draft.passwordResetStatus = PasswordResetStatusOptions.pending;
      break;
    case SET_RESET_PASSWORD_SUCCESS:
      draft.passwordResetStatus = PasswordResetStatusOptions.success;
      break;
    case SET_RESET_PASSWORD_FAILED:
      draft.passwordResetStatus = PasswordResetStatusOptions.failed;
      break;
    case SET_PASSWORD_FULFILLED:
      draft.status = 'success';
      draft.settingPassword = false;
      break;
    case SET_PASSWORD_FAILED:
      draft.errorMessages = action.payload.errorMessages;
      draft.settingPassword = true;
      break;
    case UPDATE_PROFILE_PENDING:
      draft.errorMessage = '';
      draft.profileUpdating = true;
      break;
    case UPDATE_PROFILE_FULFILLED:
      // Ignoring the privileges since the user cannot update it
      const { privileges, ...userProfile } = action.payload.user;
      Object.assign(draft, {
        ...userProfile,
        errorMessage: '',
        profileUpdating: false,
      });
      break;
    case GET_PROFILE_FULFILLED:
      Object.assign(draft, action.payload.user);
      break;
    case INVITE_USER_PENDING:
      draft.sendingInviteUser = true;
      break;
    case INVITE_USER_FULFILLED:
      draft.sendingInviteUser = false;
      draft.inviteUserErrorMessage = '';
      break;
    case INVITE_USER_FAILED:
      draft.sendingInviteUser = false;
      draft.inviteUserErrorMessage = action.payload.error.errorMessage;
      break;
    case RESEND_INVITE_USER_PENDING:
      draft.resendingUserInvites.push(action.payload.userId);
      draft.resendInviteUserErrorMessage = '';
      break;
    case RESEND_INVITE_USER_FULFILLED:
      draft.resendingUserInvites = draft.resendingUserInvites.filter((userId) => userId !== action.payload.userId);
      break;
    case RESEND_INVITE_USER_FAILED:
      draft.resendingUserInvites = draft.resendingUserInvites.filter((userId) => userId !== action.payload.userId);
      draft.resendInviteUserErrorMessage = action.payload.error.errorMessage;
      break;
    case UPDATE_PASSWORD_PENDING:
      draft.errorMessage = '';
      draft.passwordUpdating = true;
      break;
    case UPDATE_PASSWORD_FULFILLED:
      draft.errorMessage = '';
      draft.passwordUpdating = false;
      break;
    case UPDATE_PASSWORD_FAILED:
      draft.errorMessage = action.payload.error.errorMessage;
      draft.passwordUpdating = false;
      break;
    case GET_PREFERENCE_PENDING:
      draft.userPreferenceFetching = true;
      break;
    case GET_PREFERENCE_FULFILLED:
      draft.userPreferenceFetching = false;
      draft.userPref = {
        ...draft.userPref,
        ..._getUserPreference(action.payload.data),
      };
      break;
    case USER_PREFERENCE_UPDATE_ENTITY_FILTER:
      if (draft.userPref.syncStudio) {
        draft.userPref.syncStudio.filterSelections[action.payload.entityId] = action.payload.data;
      }
      break;
    case GET_PREFERENCE_FAILED:
      draft.userPreferenceFetching = false;
      break;
    case SET_PREFERENCE_PENDING:
      draft.userPref = {
        ...draft.userPref,
        [action.payload.key]: action.payload.data,
      };
      draft.preferenceErrorMessage = '';
      draft.preferenceSaving = true;
      break;
    case SET_PREFERENCE_FULFILLED:
      draft.userPref = {
        ...draft.userPref,
        // return data from api is full preference object, need to access
        // specific preference settings by key
        [action.payload.key]: action.payload.data[action.payload.key],
      };
      draft.preferenceErrorMessage = '';
      draft.preferenceSaving = false;
      break;
    case SET_PREFERENCE_FAILED:
      draft.preferenceErrorMessage = action.payload.error.errorMessage;
      draft.preferenceSaving = false;
      break;
    case GET_ALL_ROLES_PENDING:
    case GET_ALL_ROLES_FAILED:
      break;
    case GET_ALL_ROLES_FULFILLED:
      draft.allRoles = action.payload.allRoles;
      break;
    case FORGOT_PASSWORD_INITIALIZE:
      draft.forgotErrorMessage = '';
      draft.forgotPending = false;
      break;
    case FORGOT_PASSWORD_PENDING:
      draft.forgotErrorMessage = '';
      draft.forgotPending = true;
      break;
    case FORGOT_PASSWORD_FULFILLED:
      draft.forgotPending = false;
      draft.forgotPasswordHeader = action.payload.data.header;
      draft.forgotPasswordSubheader = action.payload.data.subheader;
      break;
    case FORGOT_PASSWORD_FAILED:
      if (isGatewayTimeoutError(action.payload.error)) {
        draft.forgotPending = false;
        draft.forgotErrorMessage = t('ErrorUi.error_sent_to_syncari');
      } else {
        draft.forgotPending = false;
        draft.forgotErrorMessage = action.payload.error.errorMessage;
      }
      break;
    case GET_USER_INSTANCES_PENDING:
      draft.fetchingUserInstances = FETCH_STATUS.LOADING;
      draft.fetchingUserInstancesError = null;
      break;
    case GET_USER_INSTANCES_FULFILLED:
      draft.fetchingUserInstances = FETCH_STATUS.SUCCESS;
      draft.instances = action.payload.instances;
      break;
    case GET_USER_INSTANCES_FAILED:
      draft.fetchingUserInstances = FETCH_STATUS.ERROR;
      draft.fetchingUserInstancesError = action.payload.errorMessage;
      break;
    // Activate User
    case ACTIVATE_USER_PENDING:
      draft.userUpdatesPending.push(action.payload.userId);
      break;
    case ACTIVATE_USER_FULFILLED:
      draft.userUpdatesPending = draft.userUpdatesPending.filter((userId) => userId !== action.payload.userId);
      draft.users = draft.users.map((user) => ({ ...user, status: USER_STATUS.ACTIVE }));
      break;
    case ACTIVATE_USER_FAILED:
      draft.userUpdatesPending = draft.userUpdatesPending.filter((userId) => userId !== action.payload.userId);
      draft.users = draft.users.map((user) => ({ ...user, status: USER_STATUS.INACTIVE }));
      break;
    // Deactivate User
    case DEACTIVATE_USER_PENDING:
      draft.userUpdatesPending.push(action.payload.userId);
      break;
    case DEACTIVATE_USER_FULFILLED:
    case DEACTIVATE_USER_FAILED:
      draft.userUpdatesPending = draft.userUpdatesPending.filter((userId) => userId !== action.payload.userId);
      break;
    // Delete User
    case DELETE_USER_PENDING:
      draft.userUpdatesPending.push(action.payload.userId);
      break;
    case DELETE_USER_FULFILLED:
      draft.userUpdatesPending = draft.userUpdatesPending.filter((userId) => userId !== action.payload.userId);
      draft.users = draft.users.filter((user) => user.id !== action.payload.userId);
      break;
    case DELETE_USER_FAILED:
      draft.userUpdatesPending = draft.userUpdatesPending.filter((userId) => userId !== action.payload.userId);
      break;
    // Update User
    case UPDATE_USER_PENDING:
      draft.userUpdatesPending.push(action.payload.userId);
      break;
    case UPDATE_USER_FULFILLED:
    case UPDATE_USER_FAILED:
      draft.userUpdatesPending = draft.userUpdatesPending.filter((userId) => userId !== action.payload.userId);
      break;
    case HIDE_BREADCRUMBS:
      draft.hideBreadcrumbs = action.payload.hide;
      break;
    case UPDATE_USER_PREF_SCHEMA_STUDIO_FULFILLED:
      draft.userPref.schemaStudio = action.payload.data.schemaStudio;
      break;
    case UPDATE_DATA_STUDIO_COLUMNS_FOR_ENTITY_FULFILLED:
      draft.userPref.dataStudio = action.payload.data.dataStudio;
      break;
    case SET_THOUGHTSPOT_TOKEN:
      draft.thoughtspotToken = action.payload.thoughtspotToken;
      break;
    default:
      return draft;
  }
}, _getDefaultState());

export default reducer;
