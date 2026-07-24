import { RouteComponentProps } from '@reach/router';
import { Router } from '@reach/router';
import { Suspense, useEffect } from 'react';

import { BreadcrumbLink } from 'components/breadcrumb/BreadcrumbLink';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import RouteSpin from 'components/RouteSpin';
import { useBreadcrumb } from 'pages/breadcrumbs/useBreadcrumb';
import { t } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';

import { ConnectorSchemaBreadcrumb } from './ConnectorSchemaBreadcrumb';

export const SchemaStudioBreadcrumb = withI18n(({ path }: RouteComponentProps) => {
  const { setUrlName } = useBreadcrumb();
  const { tn } = useI18nContext();

  useEffect(() => {
    setUrlName(RouteConstants.SCHEMA_STUDIO_ROOT, tn('title'));
  }, [setUrlName, tn]);

  return (
    <>
      <BreadcrumbLink to={RouteConstants.SCHEMA_STUDIO_ROOT}>{t('SchemaStudio.title')}</BreadcrumbLink>
      <Suspense fallback={<RouteSpin />}>
        <Router className="schema-studio-breadcrum breadcrumb">
          <ConnectorSchemaBreadcrumb path="/synapse/:connectorId" />
          <ConnectorSchemaBreadcrumb path="/synapse/:connectorId/*" />
        </Router>
      </Suspense>
    </>
  );
}, 'SchemaStudio');
