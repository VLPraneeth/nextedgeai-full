import { ColumnItem } from 'pages/schema-studio/ConfigureTableColumnsModal';
import { UserRole } from 'store/access-control/types';
import { ErrorNotificationPayload } from 'store/error-notifications/types';
import { Instance, InstanceType } from 'store/instances/slice';
import { FetchStatus, ResponseError } from 'store/types';
import ActionTypeConstants from 'utils/ActionTypeConstants';
import { KeysOf, ValuesOf } from 'utils/TypeUtils';

interface GetVersionFufilled {
  type: typeof ActionTypeConstants.GET_VERSION_FULFILLED;
  payload: {
    versionMetadata: any;
  };
}

export interface AllRolesAllInstance {
  [key: string]: UserRole[];
}

interface ShowAboutPage {
  type: typeof ActionTypeConstants.SHOW_ABOUT_PAGE;
  payload: {
    visible: boolean;
  };
}

interface LogoutFulfilled {
  type: typeof ActionTypeConstants.LOGOUT_FULFILLED;
}

interface GetCsrfFulfilled {
  type: typeof ActionTypeConstants.GET_CSRF_FULFILLED;
  payload: {
    csrfToken: string;
  };
}

interface GetCsrfFailed {
  type: typeof ActionTypeConstants.GET_CSRF_FAILED;
}

export const LOGIN_FULFILLED = 'LOGIN_FULFILLED';
interface LoginFulfilled {
  type: typeof LOGIN_FULFILLED;
  payload: {
    csrfToken: string;
    username: string;
  };
}
export const LOGIN_FAILED = 'LOGIN_FAILED';
interface LoginFailed {
  type: typeof LOGIN_FAILED;
  payload: {
    error: LoginError;
  };
}
export const LOGIN_PENDING = 'LOGIN_PENDING';
interface LoginPending {
  type: typeof LOGIN_PENDING;
}

export const SET_PASSWORD_PENDING = 'SET_PASSWORD_PENDING';
interface SetPasswordPending {
  type: typeof SET_PASSWORD_PENDING;
}
export const SET_PASSWORD_FULFILLED = 'SET_PASSWORD_FULFILLED';
interface SetPasswordFulfilled {
  type: typeof SET_PASSWORD_FULFILLED;
  payload: {
    data: any;
  };
}

export const SET_RESET_PASSWORD_PENDING = 'SET_RESET_PASSWORD_PENDING';

interface SetResetPasswordPending {
  type: typeof SET_RESET_PASSWORD_PENDING;
  payload: {
    status: PasswordResetStatusOptions;
  };
}

export const SET_RESET_PASSWORD_SUCCESS = 'SET_RESET_PASSWORD_SUCCESS';

interface SetResetPasswordSuccess {
  type: typeof SET_RESET_PASSWORD_SUCCESS;
  payload: {
    status: PasswordResetStatusOptions;
  };
}

export const SET_RESET_PASSWORD_FAILED = 'SET_RESET_PASSWORD_FAILED';

interface SetResetPasswordFailed {
  type: typeof SET_RESET_PASSWORD_FAILED;
  payload: {
    status: PasswordResetStatusOptions;
  };
}

export const SET_PASSWORD_FAILED = 'SET_PASSWORD_FAILED';
interface SetPasswordFailed {
  type: typeof SET_PASSWORD_FAILED;
  payload: {
    errorMessages: string[];
  };
}

export const GET_USERS_PENDING = 'GET_USERS_PENDING';
interface GetUsersPending {
  type: typeof GET_USERS_PENDING;
}
export const GET_USERS_FULFILLED = 'GET_USERS_FULFILLED';
interface GetUsersFulfilled {
  type: typeof GET_USERS_FULFILLED;
  payload: {
    users: any; // TODO: type
  };
}
export const GET_USERS_FAILED = 'GET_USERS_FAILED';
interface GetUsersFailed {
  type: typeof GET_USERS_FAILED;
  payload: {
    error: ResponseError;
  };
}

export const SHOW_INVITE_USER_MODAL = 'SHOW_INVITE_USER_MODAL';
interface ShowInviteUserModal {
  type: typeof SHOW_INVITE_USER_MODAL;
  payload: {
    visible: boolean;
  };
}

export const INVITE_USER_PENDING = 'INVITE_USER_PENDING';
interface InviteUserPending {
  type: typeof INVITE_USER_PENDING;
}
export const INVITE_USER_FULFILLED = 'INVITE_USER_FULFILLED';
interface InviteUserFulfilled {
  type: typeof INVITE_USER_FULFILLED;
  payload: {
    user: User;
  };
}
export const INVITE_USER_FAILED = 'INVITE_USER_FAILED';
interface InviteUserFailed {
  type: typeof INVITE_USER_FAILED;
  payload: {
    error: ResponseError;
  };
}

export const RESEND_INVITE_USER_PENDING = 'RESEND_INVITE_USER_PENDING';
interface ResendInviteUserPending {
  type: typeof RESEND_INVITE_USER_PENDING;
  payload: {
    userId: string;
  };
}
export const RESEND_INVITE_USER_FULFILLED = 'RESEND_INVITE_USER_FULFILLED';
interface ResendInviteUserFulfilled {
  type: typeof RESEND_INVITE_USER_FULFILLED;
  payload: {
    userId: string;
  };
}
export const RESEND_INVITE_USER_FAILED = 'RESEND_INVITE_USER_FAILED';
interface ResendInviteUserFailed {
  type: typeof RESEND_INVITE_USER_FAILED;
  payload: {
    userId: string;
    error: ResponseError;
  };
}

export const GET_PROFILE_FULFILLED = 'GET_PROFILE_FULFILLED';
interface GetProfileFulfilled {
  type: typeof GET_PROFILE_FULFILLED;
  payload: {
    user: User;
  };
}
export const GET_PROFILE_FAILED = 'GET_PROFILE_FAILED';
interface GetProfileFailed {
  type: typeof GET_PROFILE_FAILED;
}
export const GET_PROFILE_PENDING = 'GET_PROFILE_PENDING';
interface GetProfilePending {
  type: typeof GET_PROFILE_PENDING;
}

export const UPDATE_PROFILE_PENDING = 'UPDATE_PROFILE_PENDING';
interface UpdateProfilePending {
  type: typeof UPDATE_PROFILE_PENDING;
}
export const UPDATE_PROFILE_FULFILLED = 'UPDATE_PROFILE_FULFILLED';
interface UpdateProfileFulfilled {
  type: typeof UPDATE_PROFILE_FULFILLED;
  payload: {
    user: User;
  };
}
export const UPDATE_PROFILE_FAILED = 'UPDATE_PROFILE_FAILED';
interface UpdateProfileFailed {
  type: typeof UPDATE_PROFILE_FAILED;
}

export const GET_PREFERENCE_PENDING = 'GET_PREFERENCE_PENDING';
interface GetPreferencePending {
  type: typeof GET_PREFERENCE_PENDING;
}
export const GET_PREFERENCE_FULFILLED = 'GET_PREFERENCE_FULFILLED';
interface GetPreferenceFulfilled {
  type: typeof GET_PREFERENCE_FULFILLED;
  payload: { data: any };
}

export const USER_PREFERENCE_UPDATE_ENTITY_FILTER = 'USER_PREFERENCE_UPDATE_ENTITY_FILTER';
interface UserPreferenceUpdate {
  type: typeof USER_PREFERENCE_UPDATE_ENTITY_FILTER;
  payload: { data: any; entityId: string };
}

export const GET_PREFERENCE_FAILED = 'GET_PREFERENCE_FAILED';
interface GetPreferenceFailed {
  type: typeof GET_PREFERENCE_FAILED;
  payload: { error: ResponseError };
}

export const SET_PREFERENCE_PENDING = 'SET_PREFERENCE_PENDING';
interface SetPreferencePending {
  type: typeof SET_PREFERENCE_PENDING;
  payload: {
    key: string;
    data: any;
  };
}
export const SET_PREFERENCE_FULFILLED = 'SET_PREFERENCE_FULFILLED';
interface SetPreferenceFulfilled {
  type: typeof SET_PREFERENCE_FULFILLED;
  payload: {
    key: string;
    data: any;
  };
}
export const SET_PREFERENCE_FAILED = 'SET_PREFERENCE_FAILED';
interface SetPreferenceFailed {
  type: typeof SET_PREFERENCE_FAILED;
  payload: {
    error: ResponseError;
  };
}

export const FORGOT_PASSWORD_INITIALIZE = 'FORGOT_PASSWORD_INITIALIZE';
interface ForgotPasswordInitialize {
  type: typeof FORGOT_PASSWORD_INITIALIZE;
}

export const GET_ALL_ROLES_PENDING = 'GET_ALL_ROLES_PENDING';
interface GetAllRolesPending {
  type: typeof GET_ALL_ROLES_PENDING;
}
export const GET_ALL_ROLES_FULFILLED = 'GET_ALL_ROLES_FULFILLED';
interface GetAllRolesFulfilled {
  type: typeof GET_ALL_ROLES_FULFILLED;
  payload: {
    allRoles: any;
  };
}
export const GET_ALL_ROLES_FAILED = 'GET_ALL_ROLES_FAILED';
interface GetAllRolesFailed {
  type: typeof GET_ALL_ROLES_FAILED;
}

export const UPDATE_PASSWORD_PENDING = 'UPDATE_PASSWORD_PENDING';
interface UpdatePasswordPending {
  type: typeof UPDATE_PASSWORD_PENDING;
}
export const UPDATE_PASSWORD_FULFILLED = 'UPDATE_PASSWORD_FULFILLED';
interface UpdatePasswordFulfilled {
  type: typeof UPDATE_PASSWORD_FULFILLED;
}
export const UPDATE_PASSWORD_FAILED = 'UPDATE_PASSWORD_FAILED';
interface UpdatePasswordFailed {
  type: typeof UPDATE_PASSWORD_FAILED;
  payload: {
    error: ResponseError;
  };
}

export const FORGOT_PASSWORD_PENDING = 'FORGOT_PASSWORD_PENDING';
interface ForgotPasswordPending {
  type: typeof FORGOT_PASSWORD_PENDING;
}
export const FORGOT_PASSWORD_FULFILLED = 'FORGOT_PASSWORD_FULFILLED';
interface ForgotPasswordFulfilled {
  type: typeof FORGOT_PASSWORD_FULFILLED;
  payload: {
    data: ForgotPassword;
  };
}
export const FORGOT_PASSWORD_FAILED = 'FORGOT_PASSWORD_FAILED';
interface ForgotPasswordFailed {
  type: typeof FORGOT_PASSWORD_FAILED;
  payload: {
    error: ResponseError;
  };
}

export const GET_USER_INSTANCES_PENDING = 'GET_USER_INSTANCES_PENDING';
interface GetUserInstancesPending {
  type: typeof GET_USER_INSTANCES_PENDING;
}
export const GET_USER_INSTANCES_FULFILLED = 'GET_USER_INSTANCES_FULFILLED';
interface GetUserInstancesFulfilled {
  type: typeof GET_USER_INSTANCES_FULFILLED;
  payload: {
    instances: any[];
  };
}
export const GET_USER_INSTANCES_FAILED = 'GET_USER_INSTANCES_FAILED';
interface GetUserInstancesFailed {
  type: typeof GET_USER_INSTANCES_FAILED;
  payload: {
    errorMessage: string;
  };
}

export const DELETE_USER_PENDING = 'DELETE_USER_PENDING';
interface DeleteUserPending {
  type: typeof DELETE_USER_PENDING;
  payload: {
    userId: string;
  };
}
export const DELETE_USER_FULFILLED = 'DELETE_USER_FULFILLED';
interface DeleteUserFulfilled {
  type: typeof DELETE_USER_FULFILLED;
  payload: {
    userId: string;
  };
}
export const DELETE_USER_FAILED = 'DELETE_USER_FAILED';
interface DeleteUserFailed {
  type: typeof DELETE_USER_FAILED;
  payload: {
    userId: string;
  };
}

export const REMOVE_USER_PENDING = 'REMOVE_USER_PENDING';
interface RemoveUserPending {
  type: typeof REMOVE_USER_PENDING;
  payload: {
    userId: string;
  };
}
export const REMOVE_USER_FULFILLED = 'REMOVE_USER_FULFILLED';
interface RemoveUserFulfilled {
  type: typeof REMOVE_USER_FULFILLED;
  payload: {
    userId: string;
  };
}
export const REMOVE_USER_FAILED = 'REMOVE_USER_FAILED';
interface RemoveUserFailed {
  type: typeof REMOVE_USER_FAILED;
  payload: {
    userId: string;
  };
}

export const ACTIVATE_USER_PENDING = 'ACTIVATE_USER_PENDING';
interface ActivateUserPending {
  type: typeof ACTIVATE_USER_PENDING;
  payload: {
    userId: string;
  };
}
export const ACTIVATE_USER_FULFILLED = 'ACTIVATE_USER_FULFILLED';
interface ActivateUserFulfilled {
  type: typeof ACTIVATE_USER_FULFILLED;
  payload: {
    userId: string;
  };
}
export const ACTIVATE_USER_FAILED = 'ACTIVATE_USER_FAILED';
interface ActivateUserFailed {
  type: typeof ACTIVATE_USER_FAILED;
  payload: {
    userId: string;
  };
}

export const DEACTIVATE_USER_PENDING = 'DEACTIVATE_USER_PENDING';
interface DeactivateUserPending {
  type: typeof DEACTIVATE_USER_PENDING;
  payload: {
    userId: string;
  };
}
export const DEACTIVATE_USER_FULFILLED = 'DEACTIVATE_USER_FULFILLED';
interface DeactivateUserFulfilled {
  type: typeof DEACTIVATE_USER_FULFILLED;
  payload: {
    userId: string;
  };
}
export const DEACTIVATE_USER_FAILED = 'DEACTIVATE_USER_FAILED';
interface DeactivateUserFailed {
  type: typeof DEACTIVATE_USER_FAILED;
  payload: {
    userId: string;
  };
}

export const UPDATE_USER_PENDING = 'UPDATE_USER_PENDING';
interface UpdateUserPending {
  type: typeof UPDATE_USER_PENDING;
  payload: {
    userId: string;
  };
}
export const UPDATE_USER_FULFILLED = 'UPDATE_USER_FULFILLED';
interface UpdateUserFulfilled {
  type: typeof UPDATE_USER_FULFILLED;
  payload: {
    userId: string;
  };
}
export const UPDATE_USER_FAILED = 'UPDATE_USER_FAILED';
interface UpdateUserFailed {
  type: typeof UPDATE_USER_FAILED;
  payload: {
    userId: string;
  };
}

export const HIDE_BREADCRUMBS = 'HIDE_BREADCRUMBS';
interface HideBreadcrumbs {
  type: typeof HIDE_BREADCRUMBS;
  payload: {
    hide: boolean;
  };
}

export const UPDATE_USER_PREF_SCHEMA_STUDIO_PENDING = 'UPDATE_USER_PREF_SCHEMA_STUDIO_PENDING';
interface UpdateUserPrefSchemaStudioPending {
  type: typeof UPDATE_USER_PREF_SCHEMA_STUDIO_PENDING;
}
export const UPDATE_USER_PREF_SCHEMA_STUDIO_FULFILLED = 'UPDATE_USER_PREF_SCHEMA_STUDIO_FULFILLED';
interface UpdateUserPrefSchemaStudioFulfilled {
  type: typeof UPDATE_USER_PREF_SCHEMA_STUDIO_FULFILLED;
  payload: {
    data: {
      schemaStudio: SchemaStudioPreference;
    };
  };
}
export const UPDATE_USER_PREF_SCHEMA_STUDIO_FAILED = 'UPDATE_USER_PREF_SCHEMA_STUDIO_FAILED';
interface UpdateUserPrefSchemaStudioFailed {
  type: typeof UPDATE_USER_PREF_SCHEMA_STUDIO_FAILED;
  payload: {
    error: ResponseError;
  };
}

export const UPDATE_DATA_STUDIO_COLUMNS_FOR_ENTITY_PENDING = 'UPDATE_DATA_STUDIO_COLUMNS_FOR_ENTITY_PENDING';
interface UpdateDataStudioColumnsForEntityPending {
  type: typeof UPDATE_DATA_STUDIO_COLUMNS_FOR_ENTITY_PENDING;
  payload: {
    entityId: string;
  };
}
export const UPDATE_DATA_STUDIO_COLUMNS_FOR_ENTITY_FULFILLED = 'UPDATE_DATA_STUDIO_COLUMNS_FOR_ENTITY_FULFILLED';
interface UpdateDataStudioColumnsForEntityFulfilled {
  type: typeof UPDATE_DATA_STUDIO_COLUMNS_FOR_ENTITY_FULFILLED;
  payload: {
    data: {
      dataStudio: DataStudioPreference;
    };
  };
}
export const UPDATE_DATA_STUDIO_COLUMNS_FOR_ENTITY_FAILED = 'UPDATE_DATA_STUDIO_COLUMNS_FOR_ENTITY_FAILED';
interface UpdateDataStudioColumnsForEntityFailed {
  type: typeof UPDATE_DATA_STUDIO_COLUMNS_FOR_ENTITY_FAILED;
  payload: {
    entityId: string;
    error: ResponseError;
  };
}

export const SET_THOUGHTSPOT_TOKEN = 'SET_THOUGHTSPOT_TOKEN';
interface SetThoughtspotToken {
  type: typeof SET_THOUGHTSPOT_TOKEN;
  payload: {
    thoughtspotToken: string;
  };
}

export enum PasswordResetStatusOptions {
  success = 'success',
  failed = 'failed',
  pending = 'pending',
}

export type UserAction =
  | LoginFulfilled
  | LoginFailed
  | LoginPending
  | SetPasswordPending
  | SetPasswordFulfilled
  | SetPasswordFailed
  | SetResetPasswordSuccess
  | SetResetPasswordPending
  | SetResetPasswordFailed
  | GetUsersPending
  | GetUsersFulfilled
  | GetUsersFailed
  | ShowInviteUserModal
  | InviteUserPending
  | InviteUserFulfilled
  | InviteUserFailed
  | ResendInviteUserPending
  | ResendInviteUserFulfilled
  | ResendInviteUserFailed
  | GetProfileFulfilled
  | GetProfileFailed
  | GetProfilePending
  | UpdateProfilePending
  | UpdateProfileFulfilled
  | UpdateProfileFailed
  | GetPreferencePending
  | GetPreferenceFulfilled
  | UserPreferenceUpdate
  | GetPreferenceFailed
  | SetPreferencePending
  | SetPreferenceFulfilled
  | SetPreferenceFailed
  | ForgotPasswordInitialize
  | GetAllRolesPending
  | GetAllRolesFulfilled
  | GetAllRolesFailed
  | UpdatePasswordPending
  | UpdatePasswordFulfilled
  | UpdatePasswordFailed
  | ForgotPasswordPending
  | ForgotPasswordFulfilled
  | ForgotPasswordFailed
  | GetUserInstancesPending
  | GetUserInstancesFulfilled
  | GetUserInstancesFailed
  | DeleteUserPending
  | DeleteUserFulfilled
  | DeleteUserFailed
  | RemoveUserPending
  | RemoveUserFulfilled
  | RemoveUserFailed
  | ActivateUserPending
  | ActivateUserFulfilled
  | ActivateUserFailed
  | DeactivateUserPending
  | DeactivateUserFulfilled
  | DeactivateUserFailed
  | UpdateUserPending
  | UpdateUserFulfilled
  | UpdateUserFailed
  | HideBreadcrumbs
  | UpdateUserPrefSchemaStudioPending
  | UpdateUserPrefSchemaStudioFulfilled
  | UpdateUserPrefSchemaStudioFailed
  | UpdateDataStudioColumnsForEntityPending
  | UpdateDataStudioColumnsForEntityFulfilled
  | UpdateDataStudioColumnsForEntityFailed
  | GetVersionFufilled
  | ShowAboutPage
  | LogoutFulfilled
  | GetCsrfFulfilled
  | GetCsrfFailed
  | SetThoughtspotToken;

export type RoleName = 'Org Admin' | 'Instance Admin' | 'Super Admin' | 'Sync Manager' | 'Viewer';
export interface Role {
  name: string;
  id: string;
}
export type UserRoles = Record<string, string[]>;

export type User = {
  id: string;
  firstName?: string;
  lastName?: string;
  email: string;
  timeZone: string;
  ghosted: boolean;
  status: string;
  orgAdmin: boolean;
  orgId: string;
  orgName: string;
  orgLogo: string;
  orgType: string;
  maxInstance?: string;
  isGhostUser: boolean;
  isSuperAdmin: boolean;
  syncariDev: boolean;
  currentInstanceNextEdgeId: string;
  currentInstanceName: string;
  currentInstanceType: InstanceType;
  isApiUser: boolean;
  clientId: string;
  clientSecret: string;
  passwordExpired: boolean;
  createdBy: string;
  updatedBy: string;
  createdAt: string;
  updatedAt: string;
  userRoles: UserRoles;
  privileges: string[];
  userType: 'LIGHT' | 'STANDARD';
};
export interface SchemaStudioPreference {
  allEntityColumns: ColumnItem[];
  allFieldColumns: ColumnItem[];
  entityColumns: string[];
  fieldColumns: string[];
}
export interface DataStudioPreference {
  columns: Record<string, string[]>;
  allColumns: Record<string, ColumnItem[]>;
}

export interface UserState extends User {
  aboutModalVisible: boolean;
  activeGhostAccessList: string[];
  allRoles: Role[];
  authorization?: string;
  csrfToken?: string;
  errorMessage: string;
  errorMessages: string[];
  fetchingLoginStatus: FetchStatus;
  fetchingUserInstances: FetchStatus;
  fetchingUserInstancesError: null | string;
  fetchingUsers: boolean;
  forgotErrorMessage: string;
  forgotPending: boolean;
  forgotPasswordHeader: string;
  forgotPasswordSubheader: string;
  hideBreadcrumbs: boolean;
  instances: Instance[];
  inviteUserErrorMessage: string | null;
  inviteUserModalVisible: boolean;
  passwordUpdating: boolean;
  passwordResetStatus: PasswordResetStatusOptions;
  preferenceErrorMessage: string;
  preferenceSaving: boolean;
  profileUpdating: boolean;
  resendInviteUserErrorMessage: string;
  resendingUserInvites: string[];
  sendingInviteUser: boolean;
  settingPassword: boolean;
  userPreferenceFetching: boolean;
  userUpdatesPending: string[];
  username?: string;
  users: User[];
  privileges: string[];
  versionMetadata: Record<string, any>;
  userPref: {
    entityGraph: undefined | Record<string, any>;
    syncStudio?: {
      filterSelections: Record<string, USER_PREF_SYNC_STUDIO_FIELD_FILTER[]>;
      hiddenFields: Record<string, string[]>;
      pipelineViewports: Record<string, number[]>;
    };
    dataStudio: DataStudioPreference;
    errorNotification: ErrorNotificationPayload;
    schemaStudio: SchemaStudioPreference;
  };
  thoughtspotToken?: string;
}

export enum USER_PREF_SYNC_STUDIO_FIELD_FILTER {
  HIDDEN = 'HIDDEN',
  DRAFT = 'DRAFT',
  READY = 'READY',
  MAPPED = 'MAPPED',
  NOT_MAPPED = 'NOT_MAPPED',
}

export type UserPrefSyncStudioFieldFilters = Record<ValuesOf<typeof USER_PREF_SYNC_STUDIO_FIELD_FILTER>, boolean>;

export const userPrefSyncStudioFieldFilters = Object.values(USER_PREF_SYNC_STUDIO_FIELD_FILTER);
export interface RequestGhost {
  userId?: string;
  syncariId: string;
  category: string;
  roleId: string;
  reason?: string;
  accessDetails?: string;
  duration: string;
}

export interface GhostAccessAudit {
  id: string;
  requesterId: string;
  requesterEmail: string;
  approverId: string;
  approverEmail: string;
  syncariId: string;
  roleName: string;
  requestedAt: string;
  approvedAt: string;
  expireAt: string;
  reason: string;
  accessDetails?: string;
  status: 'ACTIVE' | 'COMPLETED' | 'ERROR';
  auditTrail: string;
}

export interface LoginError {
  error: string;
  message: string;
  status: number;
  timestamp: string;
}

export interface ForgotPassword {
  header: string;
  subheader: string;
}

export const CustomPreferences = {
  PipelineDocumentationAgreeTos: 'PipelineDocumentationAgreeTos',
};

export type CustomPreference = { [key in KeysOf<typeof CustomPreferences>]?: string | boolean | number | null };
