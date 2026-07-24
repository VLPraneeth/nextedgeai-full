//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { TextTagColorOptions } from 'components/text-tag/TextTag';
import { EntityStatus } from 'store/entity/types';
import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';

import { TagColors, TagData } from './GraphTags';

const tn = tNamespaced('EntityNode');

export type SyncStatuses = keyof typeof AppConstants.SYNC_STATUS;

type SyncStatusColors = TagColors & TextTagColorOptions;

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

export const pipelineStatusColorMap: Record<EntityStatus, SyncStatusColors> = {
  [AppConstants.SYNCARI_NODE_STATUS.DRAFT]: 'orange',
  [AppConstants.SYNCARI_NODE_STATUS.PUBLISHED]: 'blue',
  [AppConstants.SYNCARI_NODE_STATUS.ERROR]: 'red',
  [AppConstants.SYNCARI_NODE_STATUS.PUBLISHED_WITH_DRAFT]: 'blue',
  [AppConstants.SYNCARI_NODE_STATUS.UNMAPPED]: 'gray',
  [AppConstants.SYNCARI_NODE_STATUS.NO_STATUS]: 'gray',
};

// Static svg width sizes for status tags
const sizeMap: Record<SyncStatuses, number> = {
  RUNNING: 47,
  RESYNCING: 56,
  PAUSING: 46,
  PAUSED: 43,
  ERROR: 31,
  STALLED: 40,
  TEST: 43,
  UNPUBLISHED: 58,
  QUEUED: 43,
  RETRYING: 47,
};

const getTagDataForEntityNode = (syncStatus: SyncStatuses | null, pipelineStatus: string) => {
  let tags: TagData[] = [];

  if (syncStatus) {
    const color = syncStatusColorMap[syncStatus as SyncStatuses] || syncStatusColorMap.UNPUBLISHED;
    const tagLabel = syncStatus ? tn(syncStatus) : tn('UNPUBLISHED');
    const tagWidth = sizeMap[syncStatus as SyncStatuses] || sizeMap.UNPUBLISHED;

    tags.push({ label: tagLabel, color, tagWidth });
  }

  if (
    pipelineStatus === AppConstants.SYNCARI_NODE_STATUS.PUBLISHED ||
    pipelineStatus === AppConstants.SYNCARI_NODE_STATUS.PUBLISHED_WITH_DRAFT
  ) {
    tags.push({
      label: tn('published'),
      color: pipelineStatusColorMap[AppConstants.SYNCARI_NODE_STATUS.PUBLISHED],
      tagWidth: 54,
    });
  }
  if (
    pipelineStatus === AppConstants.SYNCARI_NODE_STATUS.DRAFT ||
    pipelineStatus === AppConstants.SYNCARI_NODE_STATUS.PUBLISHED_WITH_DRAFT
  ) {
    const tag: TagData = {
      label: tn('draft'),
      color: pipelineStatusColorMap[AppConstants.SYNCARI_NODE_STATUS.DRAFT],
      tagWidth: 33,
    };
    if (pipelineStatus === AppConstants.SYNCARI_NODE_STATUS.DRAFT) {
      tags = [tag];
    } else {
      tags.push(tag);
    }
  }

  return tags;
};

export default getTagDataForEntityNode;
