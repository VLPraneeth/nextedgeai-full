import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';

import { TagColors } from './graph/GraphTags';
import StatusTag, { StatusTagColors } from './StatusTag';
import { TextTagColorOptions } from './text-tag/TextTag';

const tn = tNamespaced('EntityNode');

export type SyncStatuses = keyof typeof AppConstants.SYNC_STATUS;

type SyncStatusColors = (TagColors & TextTagColorOptions) | 'alert';

export const syncStatusColorMap: Record<SyncStatuses, SyncStatusColors> = {
  RUNNING: 'green',
  RESYNCING: 'green',
  PAUSING: 'gray',
  PAUSED: 'gray',
  STALLED: 'gray',
  UNPUBLISHED: 'gray',
  TEST: 'purple',
  ERROR: 'red',
  QUEUED: 'gray',
  RETRYING: 'orange',
};

export interface SyncStatusTagProps {
  syncStatus?: SyncStatuses;
  color?: SyncStatusColors;
  large?: boolean;
  tooltipText?: string;
}

const SyncStatusTag = ({ syncStatus, color: overrideColor, large, tooltipText }: SyncStatusTagProps) => {
  const label = syncStatus ? tn(syncStatus) : tn('UNPUBLISHED');
  const color: StatusTagColors = overrideColor || (syncStatus && syncStatusColorMap[syncStatus]) || 'gray';
  return <StatusTag text={label} color={color} large={large} tooltipText={tooltipText} />;
};

export default SyncStatusTag;
