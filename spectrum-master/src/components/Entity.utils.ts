import { navigate } from '@reach/router';

import { EntityStatus } from 'store/entity/types';
import { getDefaultGraphVersion } from 'utils/PipelineUtil';
import RouteConstants from 'utils/RouteConstants';
import { replaceToken } from 'utils/UrlUtil';

export const navigateToEntity = (entityId: string, pipelineStatus: EntityStatus) => {
  const graphVersion = getDefaultGraphVersion(pipelineStatus).toLowerCase();
  navigate(replaceToken(RouteConstants.ENTITY_PIPELINE_GRAPH_VERSION, { entityId, graphVersion }));
};
