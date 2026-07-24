//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { TagValueModel } from 'components/inputs/Tag';
import { PipelineContext } from 'pages/sync-studio/types';
import { FetchStatus } from 'store/types';

export const SET_NODE_CHECK = 'SET_NODE_CHECK';
export const ENABLE_NODE_CHECK = 'ENABLE_NODE_CHECK';
export const CLEAR_NODE_CHECK = 'CLEAR_NODE_CHECK';
export const SELECT_ALL_NODE_CHECK = 'SELECT_ALL_NODE_CHECK';

export const SAVE_FRAGMENT_PENDING = 'SAVE_FRAGMENT_PENDING';
export const SAVE_FRAGMENT_FULFILLED = 'SAVE_FRAGMENT_FULFILLED';
export const SAVE_FRAGMENT_FAILED = 'SAVE_FRAGMENT_FAILED';

export const GET_FRAGMENTS_PENDING = 'GET_FRAGMENTS_PENDING';
export const GET_FRAGMENTS_FULFILLED = 'GET_FRAGMENTS_FULFILLED';
export const GET_FRAGMENTS_FAILED = 'GET_FRAGMENTS_FAILED';

export const DELETE_FRAGMENT_PENDING = 'DELETE_FRAGMENT_PENDING';
export const DELETE_FRAGMENT_FULFILLED = 'DELETE_FRAGMENT_FULFILLED';
export const DELETE_FRAGMENT_FAILED = 'DELETE_FRAGMENT_FAILED';

export const HIDE_FRAGMENT_PENDING = 'HIDE_FRAGMENT_PENDING';
export const HIDE_FRAGMENT_FULFILLED = 'HIDE_FRAGMENT_FULFILLED';
export const HIDE_FRAGMENT_FAILED = 'HIDE_FRAGMENT_FAILED';

export const SHOW_FRAGMENT_PENDING = 'SHOW_FRAGMENT_PENDING';
export const SHOW_FRAGMENT_FULFILLED = 'SHOW_FRAGMENT_FULFILLED';
export const SHOW_FRAGMENT_FAILED = 'SHOW_FRAGMENT_FAILED';

export const SHARE_FRAGMENT_PENDING = 'SHARE_FRAGMENT_PENDING';
export const SHARE_FRAGMENT_FULFILLED = 'SHARE_FRAGMENT_FULFILLED';
export const SHARE_FRAGMENT_FAILED = 'SHARE_FRAGMENT_FAILED';

export const GET_FRAGMENT_SHARES_PENDING = 'GET_FRAGMENT_SHARES_PENDING';
export const GET_FRAGMENT_SHARES_FULFILLED = 'GET_FRAGMENT_SHARES_FULFILLED';
export const GET_FRAGMENT_SHARES_FAILED = 'GET_FRAGMENT_SHARES_FAILED';

export const SHOW_SHARE_FRAGMENT = 'SHOW_SHARE_FRAGMENT';
export const SHOW_CREATE_FRAGMENT = 'SHOW_CREATE_FRAGMENT';
export const RESET_FRAGMENT_MODAL = 'RESET_FRAGMENT_MODAL';
export const RESET_SHARE_FRAGMENT_MODAL = 'RESET_SHARE_FRAGMENT_MODAL';

interface ShowShareFragmentModalAction {
  type: typeof SHOW_SHARE_FRAGMENT;
  visible: boolean;
  fragmentId: string;
  context: PipelineContext;
}

interface ShowCreateFragmentModalAction {
  type: typeof SHOW_CREATE_FRAGMENT;
  visible: boolean;
}

interface SetNodeCheckAction {
  type: typeof SET_NODE_CHECK;
  nodeCheckValue: boolean;
  nodeCheckId: string;
}

interface EnableNodeCheckAction {
  type: typeof ENABLE_NODE_CHECK;
  nodeCheckMode: boolean;
}

interface ClearNodeCheckValuesAction {
  type: typeof CLEAR_NODE_CHECK;
}

interface ResetFragmentModalAction {
  type: typeof RESET_FRAGMENT_MODAL;
}

interface ResetShareFragmentModalAction {
  type: typeof RESET_SHARE_FRAGMENT_MODAL;
}

export type FragmentActionTypes =
  | ShowShareFragmentModalAction
  | ShowCreateFragmentModalAction
  | SetNodeCheckAction
  | EnableNodeCheckAction
  | ClearNodeCheckValuesAction
  | ResetFragmentModalAction
  | ResetShareFragmentModalAction;

export type NodeCheckValuesModel = Record<string, boolean>;

export interface FragmentModel {
  id?: string;
  tags?: TagValueModel;
  displayName?: string;
  description?: string;
  hidden?: boolean;
  shared?: boolean;
  sharedWithInstances?: boolean;
  ownerFirstName?: string;
  ownerLastName?: string;
  iconPath?: string;
}

export type InstanceIdsModel = string[];
export type FragmentSharedModel = Record<string, InstanceIdsModel>;

export interface FragmentState {
  shareFragmentModalVisible: boolean;
  shareFragmentId?: string;
  fragmentContext: PipelineContext;
  createFragmentVisible: boolean;
  nodeCheckId?: string;
  nodeCheckValue?: boolean;
  nodeCheckValues: NodeCheckValuesModel;
  nodeCheckMode: boolean;
  fragmentSaving: boolean;
  saveFragmentErrorMessage?: string;
  fragmentSharing: boolean;
  fragmentSharingErrorMessage?: string;
  fragments?: FragmentModel[];
  fragmentShares: FragmentSharedModel;
  deleteFragmentStatus: FetchStatus;
  deleteFragmentErrorMessage?: string;
  hideFragmentStatus: FetchStatus;
  hideFragmentErrorMessage?: string;
  showFragmentStatus: FetchStatus;
  showFragmentErrorMessage?: string;
  getFragmentStatus: FetchStatus;
}
