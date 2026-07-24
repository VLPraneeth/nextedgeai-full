//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Router } from '@reach/router';
import * as React from 'react';

import { withI18n } from 'components/I18nProvider';
import RouteSpin from 'components/RouteSpin';
import { useForbiddenRedirect } from 'hooks/useForbiddenRedirect';
import { EnhancedReactLazy } from 'utils/ModuleUtils';
import { AllPermissions } from 'utils/PermissionsConstants';

import { usePipelineEditorV2Enabled } from './utils/usePipelineEditorV2Enabled';

import './SyncStudio.less';

// load EntityEditor concurrently with the current route because it's most likely
// the very next component we'll need. We'll actually lazy load in the EntityTabs and
// FieldPipeline editors
const EntityEditor = EnhancedReactLazy(() => import('./entity/EntityEditor'), { loadConcurrently: true });
const Error404 = EnhancedReactLazy(() => import('pages/errors/Error404'), { loadConcurrently: true });

const EntityTabs = EnhancedReactLazy(() => import('./pipeline/EntityTabs'));
const PipelineEditor = EnhancedReactLazy(() => import('./pipeline/PipelineEditor'));
const PipelineEditorV2 = EnhancedReactLazy(() => import('./pipeline/v2/PipelineEditorV2'));

const SyncStudio = () => {
  const Error403 = useForbiddenRedirect({
    studioPermissions: AllPermissions.READ_STUDIO,
  });

  const pipelineV2Enabled = usePipelineEditorV2Enabled();

  /**
    // Entity Editor
    /sync-studio/*
    /sync-studio/entity/:entityId

    // Entity Pipeline Editor
    /sync-studio/entity/pipeline/:entityId

    // Field Pipeline Editor
    /sync-studio/entity/:entityId/pipeline/:fieldId
  */

  const PipelineComponent = pipelineV2Enabled ? PipelineEditorV2 : PipelineEditor;

  return (
    Error403 ?? (
      <React.Suspense fallback={<RouteSpin />}>
        <Router>
          {/*
           * NOTE: there are duplicate keys below as a workaround to a change in Reach Router.
           * Reach Router made a change in how Router renders children, now it's using React.Children.toArray
           * which gives each route a unique key - it's index in this children array. This is different than before
           * where the rendered route wouldn't have a key. Because of this, React will now forcefully unmount and
           * then mount a new instance of the component even if the componentType is the same.
           *
           * Because Router is essentially a switch statement, duplicate keys won't appear in the tree at
           * runtime. I'm using the same key for each unique component, not for each route.
           */}
          <EntityEditor key="entity-editor" path="/entity" />
          <EntityEditor key="entity-editor" path="/:tabId" />
          <EntityEditor key="entity-editor" path="/:tabId/:entityId" />

          <EntityEditor key="entity-editor" path="/:tabId/quick-start/:quickStartId" />
          <EntityEditor key="entity-editor" path="/:tabId/quick-start/:quickStartId/install" />

          <EntityTabs key="entity-pipeline-editor" path="/entity/:entityId/*" />

          <PipelineComponent key="field-pipeline-editor" path="/entity/:entityId/field/:fieldId/pipeline" />
          <PipelineComponent
            key="field-pipeline-editor"
            path="/entity/:entityId/field/:fieldId/pipeline/:graphVersion"
          />
          <PipelineComponent
            key="field-pipeline-editor"
            path="/entity/:entityId/field/:fieldId/pipeline/:graphVersion/validation"
          />
          <PipelineComponent
            key="field-pipeline-editor"
            path="/entity/:entityId/field/:fieldId/pipeline/:graphVersion/pipeline-error"
          />
          <Error404 default />
        </Router>
      </React.Suspense>
    )
  );
};

export default withI18n(SyncStudio, 'SyncStudio');
