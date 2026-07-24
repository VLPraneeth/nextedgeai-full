import { Router, navigate, useMatch } from '@reach/router';
import { Suspense, useState } from 'react';

import { InlineTab, InlineTabs } from 'components/InlineTabs';
import RouteSpin from 'components/RouteSpin';
import { TranslatedText } from 'components/typography';
import { EnhancedReactLazy } from 'utils/ModuleUtils';
import { isValidGraphVersion } from 'utils/PipelineUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import RestoreVersionModal from '../entity-pipeline/RestoreVersionModal';
import { usePipelineEditorV2Enabled } from '../utils/usePipelineEditorV2Enabled';
import { DataQuality } from './data-quality/DataQuality';
import VersionDetailPage from './VersionDetailPage';

import './EntityTabs.scss';

const PipelineEditor = EnhancedReactLazy(() => import('./PipelineEditor'));
const PipelineEditorV2 = EnhancedReactLazy(() => import('./v2/PipelineEditorV2'));
const PipelineVersions = EnhancedReactLazy(() => import('./PipelineVersions'));
const PipelineLogs = EnhancedReactLazy(() => import('./pipeline-logs/PipelineLogs'));
const PipelineDocumentation = EnhancedReactLazy(() => import('./pipeline-documentation/PipelineDocumentation'));
const Error404 = EnhancedReactLazy(() => import('pages/errors/Error404'), { loadConcurrently: true });

const EntityTabs = (props: any) => {
  const { entityId } = props;

  const match = useMatch('/sync-studio/entity/:entityId/:activeTab/:graphVersion/*');
  const noVersionMatch = useMatch('/sync-studio/entity/:entityId/:activeTab/*');
  const activeTab = match?.activeTab || noVersionMatch?.activeTab || 'pipeline';
  const [lastGraphVersion, setLastGraphVersion] = useState('draft');

  const pipelineV2Enabled = usePipelineEditorV2Enabled();

  function handleTabChange(tabId: string) {
    const urlGraphVersion = match?.graphVersion || lastGraphVersion || 'draft';
    const graphVersion = isValidGraphVersion(urlGraphVersion) ? urlGraphVersion : lastGraphVersion;
    setLastGraphVersion(urlGraphVersion);

    if (tabId === 'documentation') {
      navigate(
        makeUrl(RouteConstants.ENTITY_DOCUMENTATION_VERSION, {
          entityId,
          graphVersion,
        })
      );
    } else if (tabId === 'pipeline-logs') {
      navigate(makeUrl(RouteConstants.ENTITY_LOGS, { entityId, tabId, graphVersion }));
    } else if (tabId === 'pipeline') {
      navigate(makeUrl(RouteConstants.ENTITY_PIPELINE_GRAPH_VERSION, { entityId, tabId, graphVersion }));
    } else if (tabId === 'versions') {
      navigate(makeUrl(RouteConstants.ENTITY_VERSIONS, { entityId }));
    } else if (tabId === 'data-quality') {
      navigate(makeUrl(RouteConstants.DATA_QUALITY, { entityId, tabId, graphVersion }));
    } else {
      navigate(makeUrl(RouteConstants.ENTITY_DETAIL, { entityId, tabId }));
    }
  }

  const PipelineComponent = pipelineV2Enabled ? PipelineEditorV2 : PipelineEditor;

  return (
    <div className="entity-pipeline">
      <InlineTabs selectedTab={activeTab} onChange={handleTabChange}>
        <InlineTab id="pipeline">
          <TranslatedText text="pipeline" />
        </InlineTab>
        <InlineTab id="documentation">
          <TranslatedText text="documentation" />
        </InlineTab>
        <InlineTab id="data-quality">
          <TranslatedText text="data_quality" />
        </InlineTab>
        <InlineTab id="pipeline-logs">
          <TranslatedText text="logs" />
        </InlineTab>
        <InlineTab id="versions">
          <TranslatedText text="versions" />
        </InlineTab>
      </InlineTabs>
      <Suspense fallback={<RouteSpin />}>
        <Router className="entity-pipeline__content">
          <PipelineComponent key="entity-pipeline-editor" path="/pipeline" entityId={entityId} />
          <PipelineComponent key="entity-pipeline-editor" path="/pipeline/:graphVersion" entityId={entityId} />
          <PipelineComponent
            key="entity-pipeline-editor"
            path="/pipeline/:graphVersion/validation"
            entityId={entityId}
          />
          <PipelineComponent
            key="entity-pipeline-editor"
            path="/pipeline/:graphVersion/pipeline-error"
            entityId={entityId}
          />
          <PipelineVersions key="entity-versions" path="/versions" entityId={entityId} />
          <PipelineDocumentation key="entity-documentation" path="/documentation/:graphVersion" entityId={entityId} />
          <PipelineLogs key="pipeline-logs" path="/pipeline-logs/:graphVersion" entityId={entityId} />
          <DataQuality key="data-quality" path="/data-quality/*" entityId={entityId} />
          <VersionDetailPage path="/versions/:versionOneId/compare" entityId={entityId} />
          <VersionDetailPage path="/versions/:versionOneId/compare/:versionTwoId" entityId={entityId} />
          <Error404 default />
        </Router>
      </Suspense>
      <RestoreVersionModal entityId={entityId} />
    </div>
  );
};

export default EntityTabs;
