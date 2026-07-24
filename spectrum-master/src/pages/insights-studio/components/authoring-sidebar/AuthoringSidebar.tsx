import { useEffect } from 'react';

import DrawerPanel from 'components/DrawerPanel';
import Tabs, { Tab, TabPane } from 'components/Tabs';
import useQueryParams from 'hooks/useQueryParams';
import { useUserHasPermission } from 'hooks/useUserHasPermission';
import { SidebarTab, useInsightsSidebarContext } from 'pages/insights-studio/context/InsightsSidebarContext';
import { tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

import { DataCardPane } from './DataCardPane';
import { DatasetPane } from './DatasetPane';

import './AuthoringSidebar.scss';

const tn = tNamespaced('InsightsStudio');

export const AuthoringSidebar = () => {
  const { sidebarOpen, setSidebarTab, sidebarTab, openToTab } = useInsightsSidebarContext();
  const { userHasPermission } = useUserHasPermission();
  const [queryParams] = useQueryParams<{ datasetName?: string; datacardName?: string }>();

  // If there's a query string to filter, open the corresponding tab
  useEffect(() => {
    if (queryParams?.datasetName) {
      openToTab('datasets');
    } else if (queryParams?.datacardName) {
      openToTab('datacards');
    }
  }, [openToTab, queryParams?.datacardName, queryParams?.datasetName]);

  // Navigate to the dataset tab if the user is currently on the datacard tab and
  // does not have the permission to do so.
  useEffect(() => {
    if (!userHasPermission(AllPermissions.VIEW_DATACARD) && sidebarTab === 'datacards') {
      openToTab('datasets');
    }
  }, [openToTab, sidebarTab, userHasPermission]);

  return (
    <DrawerPanel
      className="authoring-sidebar"
      closable={false}
      visible={sidebarOpen}
      useLandingZone
      noPadding
      zIndex={10}>
      <Tabs activeKey={sidebarTab} onChange={(clickedTabKey) => setSidebarTab(clickedTabKey as SidebarTab)}>
        {userHasPermission(AllPermissions.VIEW_DATACARD) && (
          <TabPane key="datacards" tab={<Tab>{tn('data_cards')}</Tab>}>
            <DataCardPane />
          </TabPane>
        )}
        <TabPane key="datasets" tab={<Tab>{tn('datasets')}</Tab>}>
          <DatasetPane />
        </TabPane>
      </Tabs>
    </DrawerPanel>
  );
};
