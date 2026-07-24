import { partition } from 'lodash';

import getTagDataForEntityNode from 'components/graph/getTagDataForEntityNode';
import { Entity, EntityStatus } from 'store/entity/types';
import { ResyncDetails } from 'store/pipeline/types';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { EntityMetadataMapType } from '../PipelineDetails';
import { SearchParamValues } from '../PipelineDetails/PipelineDetails.hooks';
import { TagData } from './PipelineDetailsTable.renderers';

const DEFAULT_PIPELINE_STATUS = {
  pipelineStatus: 'UNPUBLISHED',
};

const getGraphVersionForTag = (label: string, hasPublishedPipeline?: boolean) => {
  switch (label.toLowerCase()) {
    case 'published':
      return 'approved';
    case 'draft':
      return hasPublishedPipeline ? 'draft' : 'new';
    case 'unmapped':
      return 'new';
    default:
      return undefined;
  }
};

export const getPipelineStateObject = (tagData: TagData[] | undefined) => {
  const pipelineStates = {
    isRunning: false,
    isPaused: false,
    isPausing: false,
  };

  tagData?.forEach((data) => {
    const label = data.label.toLowerCase();

    switch (label) {
      case 'running':
      case 'queued':
        pipelineStates.isRunning = true;
        break;
      case 'pausing':
        pipelineStates.isPausing = true;
        break;
      case 'paused':
      case 'error':
        pipelineStates.isPaused = true;
        break;
      case 'resyncing':
        pipelineStates.isRunning = true;
        break;
    }
  });

  return pipelineStates;
};

export const getPipelineStatusObject = (tagData: TagData[] | undefined) => {
  const pipelineStatuses = {
    isUnmapped: false,
    hasDraft: false,
    isPublished: false,
  };

  tagData?.forEach((data: any) => {
    const label = data.label.toLowerCase();

    switch (label) {
      case 'unmapped':
        pipelineStatuses.isUnmapped = true;
        break;
      case 'draft':
        pipelineStatuses.hasDraft = true;
        break;
      case 'published':
        pipelineStatuses.isPublished = true;
        break;
    }
  });

  return pipelineStatuses;
};

export const getPipelineResyncStatus = (resyncDetail: ResyncDetails | undefined) => {
  const resyncStatuses = {
    isResyncing: false,
    isCancelling: false,
  };

  if (resyncDetail) {
    switch (resyncDetail.status) {
      case 'NEW':
      case 'PROCESSING':
        resyncStatuses.isResyncing = true;
        break;
      case 'CANCEL_REQUESTED':
        resyncStatuses.isCancelling = true;
        break;
    }
  }

  return resyncStatuses;
};

export const getPipelineTagData = (
  entityId: string,
  pipelineState: ReturnType<typeof getPipelineState>,
  pipelineStatus: EntityStatus,
  warningCount?: number
) => {
  const statusTagLabels = ['Unmapped', 'Published', 'Draft'];
  const baseTagData = getTagDataForEntityNode(pipelineState, pipelineStatus);

  // Remove 'Unmapped' tag label data if the 'Published' / 'Draft' tags exist.
  const filteredTagData = baseTagData.filter((badge) => (badge.label !== 'Unmapped' ? true : baseTagData.length === 1));
  const hasPublishedPipeline = filteredTagData.some((data) => data.label === 'Published');

  // Enhance the tag data for the renderer.
  const enhancedTagData = filteredTagData.map((tagData) => {
    const graphVersion = getGraphVersionForTag(tagData.label, hasPublishedPipeline);
    const url = graphVersion
      ? makeUrl(RouteConstants.ENTITY_PIPELINE_GRAPH_VERSION, { graphVersion, entityId })
      : undefined;

    return {
      label: tagData.label,
      color: tagData.color,
      url,
    };
  });

  if (warningCount) {
    enhancedTagData.push({
      label: 'Warning',
      color: 'orange',
      url: '',
    });
  }

  const [pipelineStatusTagData, pipelineStateTagData] = partition(enhancedTagData, (data) =>
    statusTagLabels.includes(data.label)
  );

  return {
    pipelineStatusTagData,
    pipelineStateTagData,
  };
};

export const getPipelineState = (entity: Entity, entityStatusMap: EntityMetadataMapType) => {
  const { pipelineStatus: pipelineState = 'UNPUBLISHED' } = entityStatusMap[entity.id] || DEFAULT_PIPELINE_STATUS;

  return pipelineState;
};

export const getLastSyncTime = (entity: Entity, entityStatusMap: EntityMetadataMapType) => {
  const { lastSyncTime } = entityStatusMap[entity.id];

  return lastSyncTime;
};

export const getIsFilterActive = (searchParams: SearchParamValues) => {
  const isPipelineStateFiltered = Object.values(searchParams.pipelineState).some((value) => value);
  const isPipelineStatusFiltered = Object.values(searchParams.pipelineStatus).some((value) => value);
  const isPipelineSourcesFiltered = Object.values(searchParams.sources).some((value) => value);
  const isPipelineDestinationsFiltered = Object.values(searchParams.destinations).some((value) => value);

  return (
    isPipelineStateFiltered || isPipelineStatusFiltered || isPipelineSourcesFiltered || isPipelineDestinationsFiltered
  );
};
