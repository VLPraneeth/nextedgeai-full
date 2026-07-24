import { countBy, sum, values } from 'lodash';

import { useEnhancedSelector } from 'hooks/redux';
import { useResyncStates } from 'pages/sync-studio/entity-pipeline/entity-resync/ResyncRequestModal';
import { EMPTY_ARRAY } from 'store/constants';
import AppConstants from 'utils/AppConstants';

import { getEntityMetadataMap } from '../PipelineDetails';

const { RESYNCING, RUNNING, PAUSED, ERROR } = AppConstants.SYNC_STATUS;
const { DRAFT, PUBLISHED_WITH_DRAFT } = AppConstants.SYNCARI_NODE_STATUS;

export const usePipelineDetailsSummary = () => {
  const { entities } = useEnhancedSelector((state) => state.entity);
  const entitySyncStatuses = useEnhancedSelector((state) => state.entityPipeline.entitySyncStatuses ?? EMPTY_ARRAY);
  const resyncStatus = useResyncStates();

  const entityStatusMap = getEntityMetadataMap(entities, entitySyncStatuses, resyncStatus);
  const stats = countBy(entityStatusMap, 'filterStatus');

  // Compute total before correcting draft count
  const total = sum(values(stats));

  // Correct draft count
  if (entities) {
    stats[DRAFT] = entities.filter(
      (entity) => entity.pipelineStatus === PUBLISHED_WITH_DRAFT || entity.pipelineStatus === DRAFT
    ).length;
  }

  // Always show these statuses; assign the value to a 0 if they are not present
  // in the map so they will display in the UI.
  [RESYNCING, RUNNING, PAUSED, ERROR].forEach((constantStatus) => {
    if (!stats[constantStatus]) {
      stats[constantStatus] = 0;
    }
  });

  return { stats, total };
};
