//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { PipelineContext } from 'pages/sync-studio/types';
import AppConstants from 'utils/AppConstants';

import {
  FragmentActionTypes,
  SHOW_SHARE_FRAGMENT,
  SHOW_CREATE_FRAGMENT,
  SET_NODE_CHECK,
  ENABLE_NODE_CHECK,
  CLEAR_NODE_CHECK,
  RESET_FRAGMENT_MODAL,
  RESET_SHARE_FRAGMENT_MODAL,
} from './types';

const { PIPELINE_CONTEXT } = AppConstants;

export function showShareFragmentModal(
  fragmentId: string,
  context: PipelineContext = PIPELINE_CONTEXT.ENTITY,
  visible: boolean = true
): FragmentActionTypes {
  return {
    type: SHOW_SHARE_FRAGMENT,
    visible,
    fragmentId,
    context,
  };
}

export function showCreateFragmentModal(visible: boolean = true): FragmentActionTypes {
  return {
    type: SHOW_CREATE_FRAGMENT,
    visible,
  };
}

export function setNodeCheck(nodeId: string, value: boolean): FragmentActionTypes {
  return {
    type: SET_NODE_CHECK,
    nodeCheckValue: value,
    nodeCheckId: nodeId,
  };
}

export function enableNodeCheck(enable: boolean = true): FragmentActionTypes {
  return {
    type: ENABLE_NODE_CHECK,
    nodeCheckMode: enable,
  };
}

export function clearNodeCheckValues(): FragmentActionTypes {
  return {
    type: CLEAR_NODE_CHECK,
  };
}

export function resetFragmentModal(): FragmentActionTypes {
  return {
    type: RESET_FRAGMENT_MODAL,
  };
}

export function resetShareFragmentModal(): FragmentActionTypes {
  return {
    type: RESET_SHARE_FRAGMENT_MODAL,
  };
}

export * from './thunks';
