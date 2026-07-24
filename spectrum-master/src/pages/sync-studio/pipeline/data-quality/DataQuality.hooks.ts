import { navigate, useMatch } from '@reach/router';

import AppConstants from 'utils/AppConstants';
import { getPipelineDraftStatus } from 'utils/PipelineUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

export const useDataQuality = () => {
  const categoriesMatch = useMatch('/sync-studio/entity/:entityId/data-quality/:graphVersion/manage-categories/*');
  const rulesMatch = useMatch('/sync-studio/entity/:entityId/data-quality/:graphVersion/manage-rules/*');
  const ruleIdMatch = useMatch('/sync-studio/entity/:entityId/data-quality/:graphVersion/manage-rules/:ruleId/*');
  const dataQualityMatch = useMatch('/sync-studio/entity/:entityId/data-quality/:graphVersion/*');

  const navigateToDataQuality = () => {
    if (dataQualityMatch?.entityId && dataQualityMatch?.graphVersion) {
      navigate(
        makeUrl(RouteConstants.DATA_QUALITY, {
          entityId: dataQualityMatch.entityId,
          graphVersion: dataQualityMatch.graphVersion,
        })
      );
    }
  };

  const navigateToEditRule = (ruleId: string) => {
    if (dataQualityMatch?.entityId && dataQualityMatch?.graphVersion) {
      navigate(
        makeUrl(RouteConstants.DATA_QUALITY_RULE, {
          entityId: dataQualityMatch.entityId,
          graphVersion: dataQualityMatch.graphVersion,
          ruleId,
        })
      );
    }
  };

  return {
    navigateToDataQuality,
    navigateToEditRule,
    categoriesMatch,
    rulesMatch,
    ruleIdMatch,
    entityId: dataQualityMatch?.entityId || '',
    graphVersion: getPipelineDraftStatus(
      dataQualityMatch?.graphVersion?.toUpperCase() || AppConstants.GRAPH_STATUS.NEW
    ),
    editable: ['draft', 'new'].includes(dataQualityMatch?.graphVersion?.toLowerCase() || ''),
  };
};
