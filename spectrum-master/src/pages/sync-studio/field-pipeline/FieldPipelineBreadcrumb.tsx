import { RouteComponentProps, useMatch } from '@reach/router';
import { find } from 'lodash';
import { useMemo } from 'react';

import { BreadcrumbLink } from 'components/breadcrumb/BreadcrumbLink';
import { BreadcrumbSeparator } from 'components/breadcrumb/BreadcrumbSeparator';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { useBreadcrumb } from 'pages/breadcrumbs/useBreadcrumb';
import { getBreadcrumbMenuItems } from 'pages/MainHeaderBreadcrumbs.utils';
import { setNavigatingTo } from 'store/app/actions';
import { showUnsavedConfirmModal as showConfirmModal } from 'store/pipeline/actions';
import AppConstants from 'utils/AppConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeRouteConstantToRoute, makeUrl } from 'utils/UrlUtil';

export interface FieldPipelineBreadcrumbProps extends RouteComponentProps {
  children?: React.ReactNode;
  entityId?: string;
  fieldId?: string;
  version?: string;
}

export const FieldPipelineBreadcrumb = ({
  children,
  entityId,
  fieldId,
  version,
  ...rest
}: FieldPipelineBreadcrumbProps) => {
  const entities = useEnhancedSelector((state) => state.entity.entities);
  const dispatch = useEnhancedDispatch();
  const pipelineChanged = useEnhancedSelector((state) => state.pipeline.changed);
  const { setUrlName } = useBreadcrumb();
  const fieldMatch = useMatch(makeRouteConstantToRoute(RouteConstants.FIELD_PIPELINE_GRAPH_VERSION));

  const fieldName = useMemo(() => {
    if (entities) {
      let displayName = fieldId;
      find(entities, (entity) => {
        let found = false;
        const field = find(entity.fields, (field) => {
          return field.id === fieldId;
        });
        if (field) {
          displayName = field.displayName;
          if (fieldMatch) {
            setUrlName(makeUrl(RouteConstants.FIELD_PIPELINE_GRAPH_VERSION, fieldMatch), displayName);
          }
          found = true;
        }
        return found;
      });
      return displayName;
    }
  }, [entities, fieldId, fieldMatch, setUrlName]);

  const url = makeUrl(version ? RouteConstants.FIELD_PIPELINE_GRAPH_VERSION : RouteConstants.FIELD_PIPELINE, {
    entityId,
    fieldId,
    graphVersion: version,
  });

  const breadcrumbMenuItems =
    getBreadcrumbMenuItems(
      AppConstants.LIST_TYPES.FIELD,
      entities || [],
      [RouteConstants.SYNC_STUDIO, '2', entityId || '', '3', fieldId || ''],
      {
        changed: pipelineChanged,
        setNavigatingTo: (url) => dispatch(setNavigatingTo(url)),
        showConfirmModal: (visible) => dispatch(showConfirmModal(visible)),
      }
    ) || [];

  return (
    <>
      <BreadcrumbSeparator />
      <BreadcrumbLink breadcrumbMenuItems={breadcrumbMenuItems} to={url}>
        {fieldName}
      </BreadcrumbLink>
    </>
  );
};
