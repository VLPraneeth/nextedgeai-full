import { Router } from '@reach/router';
import { Suspense } from 'react';

import RouteSpin from 'components/RouteSpin';
import { CustomSynapseBreadcrumb } from 'pages/connector/custom-synapse/CustomSynapseBreadcrumb';
import { DataQualityBreadcrumb } from 'pages/data-quality-studio/DataQualityBreadcrumb';
import { DataStudioBreadcrumb } from 'pages/data-studio-new/DataStudioBreadcrumb';
// import { ReferenceDataBreadcrumb } from 'pages/data-studio-new/ReferenceData/ReferenceDataBreadcrumb';
import { ImportedFilesBreadcrumb } from 'pages/imported-files/ImportedFilesBreadcrumb';
import { InsightStudioBreadcrumb } from 'pages/insights-studio/InsightStudioBreadcrumb';
import { InsightStudioTsBreadcrumb } from 'pages/insights-studio/InsightStudioTsBreadcrumb';
import { NotificationBreadcrumb } from 'pages/notification/NotificationBreadcrumb';
import { SchemaStudioBreadcrumb } from 'pages/schema-studio/SchemaStudioBreadcrumb';
import { SyncStudioBreadcrumb } from 'pages/sync-studio/SyncStudioBreadcrumb';

import { DefaultBreadcrumb } from './DefaultBreadcrumb';

import './Breadcrumbs.scss';

export const Breadcrumbs = () => {
  return (
    <Suspense fallback={<RouteSpin />}>
      <Router className="breadcrumbs">
        <InsightStudioBreadcrumb path="/insights-studio/:dashboardId/*" />
        <InsightStudioBreadcrumb path="/insights-studio/:dashboardId/:version/*" />
        <InsightStudioTsBreadcrumb path="/insights-studio/ts/:tab/*" />
        <SchemaStudioBreadcrumb path="/schema-studio/*" />
        <SyncStudioBreadcrumb path="/sync-studio/*" />
        {/* Data Studio Routes */}
        <DataStudioBreadcrumb path="/data-studio/*" type="data-studio" />
        {/* Reference Data Routes */}
        <DataStudioBreadcrumb path="/data-studio/reference-data/*" type="reference-data" />
        <DataQualityBreadcrumb path="/data-quality-studio/*" />
        <DataQualityBreadcrumb path="/data-quality-studio/:dashboardId" />
        <ImportedFilesBreadcrumb path="/imported-files/*" />
        <ImportedFilesBreadcrumb path="/imported-files/folder/:folderId" />
        <ImportedFilesBreadcrumb path="/imported-files/folder/:folderId/file/:fileId" />
        <NotificationBreadcrumb path="/settings/notifications/:type" />
        <NotificationBreadcrumb path="/settings/notifications/:type/:action" />
        <NotificationBreadcrumb path="/settings/notifications/:type/:id/:action" />

        <CustomSynapseBreadcrumb path="/synapses/custom-synapses/*" />
        <DefaultBreadcrumb default />
      </Router>
    </Suspense>
  );
};
