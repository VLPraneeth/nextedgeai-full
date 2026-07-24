import { RouteComponentProps, useLocation } from '@reach/router';
import { ReactNode, useEffect, useState } from 'react';

import Tabs, { Tab, TabPane } from 'components/Tabs';
import { TranslatedText } from 'components/typography';
import useNavigateTo from 'hooks/useNavigateTo';
import { useUserHasPermission } from 'hooks/useUserHasPermission';
import { useUserRolesForCurrentInstance } from 'store/user/hooks';
import { RoleGroup } from 'utils/CapConstants';
import { FeatureFlagName, isFeatureEnabled } from 'utils/FeatureFlagUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import EntityEditorConnectorPanel, { ExtendedConnector } from './EntityEditorConnectorPanel';
import { QuickStartPanel } from './quick-start';
import QuickStartHistoryLegacy from './quick-start-legacy/QuickStartHistory';
import QuickStartListLegacy from './quick-start-legacy/QuickStartList';
import { useCurrentSyncStudioRootTab } from './SyncStudioRootTabs';

export enum SyncStudioPanelKeys {
  Entities = 'entities',
  QuickStartV2 = 'quick-start',
  QuickStart = 'quick-start-legacy',
}

export interface SyncStudioTabsPanelProps extends RouteComponentProps {
  connectors: ExtendedConnector[];
  synapsesTab?: ReactNode;
}

const SyncStudioTabsPanel = ({ connectors, synapsesTab }: SyncStudioTabsPanelProps) => {
  const location = useLocation();
  const navigate = useNavigateTo();
  const { currentTab } = useCurrentSyncStudioRootTab();

  const [activeTab, setActiveTab] = useState(() => {
    if (location.href.includes(SyncStudioPanelKeys.QuickStartV2)) {
      return SyncStudioPanelKeys.QuickStartV2;
    }
    return SyncStudioPanelKeys.Entities;
  });
  const [quickStartHistoryName, setQuickStartHistoryName] = useState<string | null>(null);
  const { userCan } = useUserRolesForCurrentInstance();
  const { userHasPermission } = useUserHasPermission();

  const shouldQSTabRender = userCan(RoleGroup.ADMIN_SUPER_GHOST) || userHasPermission([AllPermissions.WRITE_STUDIO]);

  useEffect(() => {
    if (location.href.includes(SyncStudioPanelKeys.QuickStartV2)) {
      setActiveTab(SyncStudioPanelKeys.QuickStartV2);
    }
  }, [location]);

  const onTabChange = (key: SyncStudioPanelKeys) => {
    setActiveTab(key as SyncStudioPanelKeys);
    if (key === SyncStudioPanelKeys.QuickStartV2) {
      navigate(makeUrl(RouteConstants.QUICK_START, { tabId: currentTab }));
    } else {
      navigate(makeUrl(RouteConstants.ENTITIES, { tabId: currentTab }));
    }
  };

  return (
    <>
      <QuickStartHistoryLegacy name={quickStartHistoryName} onClose={() => setQuickStartHistoryName(null)} />
      <Tabs activeKey={activeTab} onChange={(key) => onTabChange(key as SyncStudioPanelKeys)}>
        <TabPane
          key={SyncStudioPanelKeys.Entities}
          tab={
            <Tab className="synri-sync-tab">
              <TranslatedText text="synapses" />
            </Tab>
          }>
          {synapsesTab ?? <EntityEditorConnectorPanel connectors={connectors} />}
        </TabPane>
        {shouldQSTabRender && (
          <TabPane
            key={SyncStudioPanelKeys.QuickStartV2}
            tab={
              <Tab className="synri-sync-tab">
                <TranslatedText text="quick_starts" />
              </Tab>
            }>
            <QuickStartPanel />
          </TabPane>
        )}
        {/* Legacy quick starts - only used internally */}
        {isFeatureEnabled(FeatureFlagName.QUICK_START) && (
          <TabPane
            key={SyncStudioPanelKeys.QuickStart}
            tab={
              <Tab className="synri-sync-tab">
                <TranslatedText text="quick_start" />
              </Tab>
            }>
            <QuickStartListLegacy setQuickStartHistoryName={setQuickStartHistoryName} />
          </TabPane>
        )}
      </Tabs>
    </>
  );
};

export default SyncStudioTabsPanel;
