import { RouteComponentProps, useMatch } from '@reach/router';
import { useMemo } from 'react';

import { BreadcrumbLink } from 'components/breadcrumb/BreadcrumbLink';
import { BreadcrumbSeparator } from 'components/breadcrumb/BreadcrumbSeparator';
import { useEnhancedSelector } from 'hooks/redux';
import { useBreadcrumb } from 'pages/breadcrumbs/useBreadcrumb';
import { SchemaVersion } from 'store/schema/types';
import RouteConstants from 'utils/RouteConstants';
import { makeRouteConstantToRoute, makeUrl, VALID_SCHEMA_VERSIONS } from 'utils/UrlUtil';

export interface EntitySchemaBreadcrumbProps extends RouteComponentProps {
  children?: React.ReactNode;
  connectorId?: string;
  entityApiName?: string;
  version?: string;
}

export const EntitySchemaBreadcrumb = ({
  children,
  connectorId,
  entityApiName,
  version,
}: EntitySchemaBreadcrumbProps) => {
  const connectorSchemas = useEnhancedSelector((state) => state.schema.connectorSchemas);
  const { setUrlName } = useBreadcrumb();
  const synapseEntityMatch = useMatch(makeRouteConstantToRoute(RouteConstants.SCHEMA_STUDIO_SYNAPSE_ENTITY));

  const connectorName = useMemo(() => {
    if (version && connectorId && connectorSchemas && Object.keys(connectorSchemas).length > 0) {
      const selectedSchema = connectorSchemas?.[connectorId]?.data?.find(
        (schema) => schema.apiName.toLowerCase() === entityApiName?.toLowerCase()
      );
      const localVersion = version as SchemaVersion;
      if (
        version &&
        VALID_SCHEMA_VERSIONS.includes(localVersion) &&
        selectedSchema?.[localVersion]?.fields?.displayName
      ) {
        const name = selectedSchema[localVersion].fields.displayName;
        if (synapseEntityMatch) {
          setUrlName(makeUrl(RouteConstants.SCHEMA_STUDIO_SYNAPSE_ENTITY, synapseEntityMatch), name);
        }
        return name;
      }
    }
    return entityApiName;
  }, [connectorId, connectorSchemas, entityApiName, setUrlName, synapseEntityMatch, version]);

  const url = makeUrl(RouteConstants.SCHEMA_STUDIO_SYNAPSE_ENTITY, { connectorId, entityApiName, version });

  return (
    <>
      <BreadcrumbSeparator />
      <BreadcrumbLink to={url}>{connectorName}</BreadcrumbLink>
    </>
  );
};
