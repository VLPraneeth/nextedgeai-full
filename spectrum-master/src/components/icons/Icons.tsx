//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
// There are 3 maps to fill if you want to add a new icon
// This mapping is temporary...
import InlineSvg from 'components/icons/InlineSvg';

export const ICON_TYPE = {
  SYNCARI_NO_STATUS: 'SYNCARI_NO_STATUS',
};

export const ICON_MAP = {
  [ICON_TYPE.SYNCARI_NO_STATUS]: '/assets/icons/syncari-node.svg',
};

export const ENTITY_ICON_MAP = {
  Account: '/assets/icons/account-entity.svg',
  Activity: '/assets/icons/activity-entity.svg',
  Contact: '/assets/icons/contact-entity.svg',
  Custom: '/assets/icons/custom-entity.svg',
  Lead: '/assets/icons/lead-entity.svg',
  Opportunity: '/assets/icons/opportunity-entity.svg',
  Ticket: '/assets/icons/ticket-entity.svg',
  User: '/assets/icons/user-entity.svg',
} as const;

export const SELECTED_ENTITY_ICON_MAP = {
  Account: '/assets/icons/account-entity-selected.svg',
  Activity: '/assets/icons/activity-entity-selected.svg',
  Contact: '/assets/icons/contact-entity-selected.svg',
  Custom: '/assets/icons/custom-entity-selected.svg',
  Lead: '/assets/icons/lead-entity-selected.svg',
  Opportunity: '/assets/icons/opportunity-entity-selected.svg',
  Ticket: '/assets/icons/ticket-entity-selected.svg',
  User: '/assets/icons/user-entity-selected.svg',
} as const;

export const SYNCARI_ICON = '/assets/brand/nextedge-mark.svg';
export const SYNCARI_CORE_NODE_INTRO = '/assets/brand/nextedge-core-node-intro.svg';
export const SETTINGS_ICON = '/assets/icons/settings-cog.svg';
export const SETTINGS_KEBAB_ICON = '/assets/icons/kebab.svg';
export const COLLAPSE_GROUP_ICON = '/assets/icons/groups/collapse_group.svg';
export const EXPAND_GROUP_ICON = '/assets/icons/groups/expand_group.svg';
export const GROUP_GRAY_ICON = '/assets/icons/groups/group_gray.svg';
export const DEDUPLICATE_ICON = '/assets/icons/deduplicate.svg';
export const DATA_AUTHORITY_ICON = '/assets/icons/data-authority.svg';
export const DISTANCE_ICON = '/assets/icons/distance.svg';
export const FUNCTION_ICON = '/assets/icons/function.svg';
export const PIPELINE_ICON = '/assets/icons/pipeline.svg';
export const PIPELINE_GRAPH_ICON = '/assets/icons/pipeline-graph.svg';
export const SYNC_FROM_ICON = '/assets/icons/sync-from.svg';
export const CHEVRON_DOWN_ICON = '/assets/icons/chevron-down.svg';
export const CHEVRON_UP_ICON = '/assets/icons/chevron-up.svg';
export const SYNC_TO_ICON = '/assets/icons/sync-to.svg';
export const TAG_ICON = '/assets/icons/tag.svg';
export const ACTION_ICON = '/assets/icons/action.svg';
export const ENTITY_ICON = '/assets/icons/entity.svg';
export const CHECK_ICON = '/assets/icons/selected-check.svg';
export const UNCHECK_ICON = '/assets/icons/unselected-check.svg';
export const UNCHECK_ICON_BORDER = '/assets/icons/unselected-check-border.svg';
export const ERROR_ICON = '/assets/icons/node-error.svg';
export const HIDDEN_TAG_ICON = '/assets/icons/b-eye.svg';
export const NEW_FRAGMENT = '/assets/icons/new-fragment.svg';
export const TEST_NEW = '/assets/icons/test-new.svg';
export const OPEN_OUTLINE = '/assets/icons/open-outline.svg';
export const TIME_TICKER_ENTITY_ICON = '/assets/icons/time-ticker.svg';

export const NODE_GRAPH_READY = '/assets/icons/graph-node-ready.svg';
export const NODE_GRAPH_TEST = '/assets/icons/graph-node-test.svg';
export const NODE_GRAPH_SYNCING = '/assets/icons/graph-node-sync.svg';
export const NODE_GRAPH_PAUSED = '/assets/icons/graph-node-pause.svg';
export const NODE_GRAPH_WARNING = '/assets/icons/graph-node-warning.svg';
export const NODE_GRAPH_ERROR = '/assets/icons/graph-node-error.svg';

export function getIconFromPath(path: string, title = '') {
  return <InlineSvg src={path} title={title} />;
}
