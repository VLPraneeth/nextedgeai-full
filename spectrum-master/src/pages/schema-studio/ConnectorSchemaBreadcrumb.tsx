import { RouteComponentProps, Router, useMatch } from '@reach/router';
import { Suspense, useMemo } from 'react';

import { BreadcrumbLink } from 'components/breadcrumb/BreadcrumbLink';
import { BreadcrumbSeparator } from 'components/breadcrumb/BreadcrumbSeparator';
import RouteSpin from 'components/RouteSpin';
import { useEnhancedSelector } from 'hooks/redux';
import { useBreadcrumb } from 'pages/breadcrumbs/useBreadcrumb';
import { selectAllConnectors } from 'selectors/connectorSelectors';
import AppConstants from 'utils/AppConstants';
import { capitalize } from 'utils/Fp';
import RouteContants from 'utils/RouteConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeRouteConstantToRoute, makeUrl } from 'utils/UrlUtil';

import { EntitySchemaBreadcrumb } from './EntitySchemaBreadcrumb';

export interface ConnectorSchemaBreadcrumbProps extends RouteComponentProps {
  connectorId?: string;
}

export const ConnectorSchemaBreadcrumb = ({ connectorId }: ConnectorSchemaBreadcrumbProps) => {
  const connectors = useEnhancedSelector(selectAllConnectors);
  const { setUrlName } = useBreadcrumb();
  const schemaStudioMatch = useMatch(`${makeRouteConstantToRoute(RouteConstants.SCHEMA_STUDIO_SYNAPSE)}/*`);

  const connectorName = useMemo(() => {
    const connector = connectors?.find((connector) => connector.id === connectorId);
    if (connector?.name) {
      const name = connector.name === AppConstants.SYNCARI_CONNECTOR_NAME ? capitalize(connector.name) : connector.name;
      if (schemaStudioMatch) {
        setUrlName(makeUrl(RouteConstants.SCHEMA_STUDIO_SYNAPSE, schemaStudioMatch), name);
      }
      return name;
    }
    return connectorId;
  }, [connectorId, connectors, schemaStudioMatch, setUrlName]);

  return (
    <>
      <BreadcrumbSeparator />
      <BreadcrumbLink to={makeUrl(RouteContants.SCHEMA_STUDIO_SYNAPSE, { connectorId })}>
        {connectorName}
      </BreadcrumbLink>
      <Suspense fallback={<RouteSpin />}>
        <Router className="sync-studio-breadcrumb breadcrumb">
          <EntitySchemaBreadcrumb path="/entity/:entityApiName/:version" connectorId={connectorId} />
        </Router>
      </Suspense>
    </>
  );
};
