import { RouteComponentProps, Router, useMatch } from '@reach/router';
import { Suspense, useMemo } from 'react';

import { BreadcrumbLink } from 'components/breadcrumb/BreadcrumbLink';
import { BreadcrumbSeparator } from 'components/breadcrumb/BreadcrumbSeparator';
import RouteSpin from 'components/RouteSpin';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { useBreadcrumb } from 'pages/breadcrumbs/useBreadcrumb';
import { getBreadcrumbMenuItems } from 'pages/MainHeaderBreadcrumbs.utils';
import { setNavigatingTo } from 'store/app/actions';
import { showUnsavedConfirmModal as showConfirmModal } from 'store/pipeline/actions';
import AppConstants from 'utils/AppConstants';
import { getEntityName } from 'utils/EntityUtil';
import RouteConstants from 'utils/RouteConstants';
import { entityIdIsValid } from 'utils/StringUtil';
import { makeRouteConstantToRoute, makeUrl } from 'utils/UrlUtil';

import { FieldPipelineBreadcrumb } from '../field-pipeline/FieldPipelineBreadcrumb';

export interface EntityPipelineBreadcrumbProps extends RouteComponentProps {
  children?: React.ReactNode;
  entityId?: string;
  version?: string;
}

export const EntityPipelineBreadcrumb = ({ children, entityId, version, ...rest }: EntityPipelineBreadcrumbProps) => {
  const entities = useEnhancedSelector((state) => state.entity.entities);
  const dispatch = useEnhancedDispatch();
  const pipelineChanged = useEnhancedSelector((state) => state.pipeline.changed);
  const { setUrlName } = useBreadcrumb();
  const entityMatch = useMatch(`${makeRouteConstantToRoute(RouteConstants.ENTITY)}/*`);

  const entityName = useMemo(() => {
    if (entities) {
      const name = getEntityName(entityId, entities);
      if (name) {
        setUrlName(makeUrl(RouteConstants.ENTITY, entityMatch), name);
        return name;
      }
    }
    return entityId;
  }, [entities, entityId, entityMatch, setUrlName]);

  const url = makeUrl(version ? RouteConstants.ENTITY_PIPELINE_GRAPH_VERSION : RouteConstants.ENTITY_PIPELINE, {
    entityId,
    graphVersion: version,
  });

  const breadcrumbMenuItems =
    getBreadcrumbMenuItems(
      AppConstants.LIST_TYPES.ENTITY,
      entities || [],
      [RouteConstants.SYNC_STUDIO, '2', entityId || ''],
      {
        changed: pipelineChanged,
        setNavigatingTo: (url) => dispatch(setNavigatingTo(url)),
        showConfirmModal: (visible) => dispatch(showConfirmModal(visible)),
      }
    ) || [];

  if (!entityIdIsValid(entityId)) {
    return null;
  }

  return (
    <>
      <BreadcrumbSeparator />
      <BreadcrumbLink breadcrumbMenuItems={breadcrumbMenuItems} to={url}>
        {entityName}
      </BreadcrumbLink>

      <Suspense fallback={<RouteSpin />}>
        <Router className="sync-studio-breadcrum breadcrumb">
          <FieldPipelineBreadcrumb path="/field/:fieldId/pipeline/*" />
          <FieldPipelineBreadcrumb path="/field/:fieldId/pipeline/:version" />
        </Router>
      </Suspense>
    </>
  );
};
