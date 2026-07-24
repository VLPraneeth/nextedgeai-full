import AppConstants from './AppConstants';
import { ValuesOf } from './TypeUtils';

export type SyncStatusType = ValuesOf<typeof AppConstants.SYNC_STATUS>;

export type UserPrefKeys = ValuesOf<typeof AppConstants.USER_PREF>;

export type NodeTypeKeys = ValuesOf<typeof AppConstants.NODE_TYPE>;
