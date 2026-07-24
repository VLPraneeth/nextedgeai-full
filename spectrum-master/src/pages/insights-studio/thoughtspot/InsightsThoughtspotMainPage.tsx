import { Router, navigate } from '@reach/router';
import { Spin } from 'antd';
import { useEffect, useMemo, useState } from 'react';

import { useGetTSLiveboardsQuery } from 'store/insights-studio';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { InsightsThoughtSpotTabs, TSTabs } from '../components/insights-thought-spot-tabs/InsightsThoughtSpotTabs';
import { Dashboards } from './Dashboards';
import { Datacards } from './Datacards';
import { DatasetList } from './DatasetList';
import Search from './Search';
import './InsightsThoughtspotMainPage.scss';

const InsightsThoughtspotMainPage = ({ uri, location }: any) => {
  const [currentTsTab, setCurrentTsTab] = useState<TSTabs>();
  const { data: liveboards, isLoading } = useGetTSLiveboardsQuery();

  const liveboardsArray = useMemo(() => {
    return Object.keys(liveboards || []).map((key) => ({
      id: liveboards?.[key] || '',
      name: key,
    }));
  }, [liveboards]);

  useEffect(() => {
    if (!location || isLoading) {
      return;
    }

    if (location.pathname === RouteConstants.INSIGHTS_STUDIO) {
      setCurrentTsTab('dashboards');
      const firstDashboard = liveboardsArray?.[0]?.id;

      if (firstDashboard) {
        navigate(makeUrl(RouteConstants.INSIGHTS_STUDIO_TS_DASHBOARDS_ID, { dashboardId: firstDashboard }));
      } else {
        navigate(makeUrl(RouteConstants.INSIGHTS_STUDIO_TS_DASHBOARDS));
      }
    } else if (location.pathname.startsWith(RouteConstants.INSIGHTS_STUDIO_TS_DASHBOARDS)) {
      setCurrentTsTab('dashboards');
    } else if (location.pathname.startsWith(RouteConstants.INSIGHTS_STUDIO_TS_DATASETS)) {
      setCurrentTsTab('datasets');
    } else if (location.pathname.startsWith(RouteConstants.INSIGHTS_STUDIO_TS_SEARCH)) {
      setCurrentTsTab('search');
    } else if (location.pathname.startsWith(RouteConstants.INSIGHTS_STUDIO_TS_DATACARDS)) {
      setCurrentTsTab('datacards');
    }
  }, [location, liveboardsArray, isLoading]);
  const handleTsTabChange = (tab: string) => {
    if (!uri) {
      return;
    }

    setCurrentTsTab(tab as TSTabs);
    navigate(`${uri}/${tab}`);
  };

  return (
    <>
      <InsightsThoughtSpotTabs setCurrentTab={handleTsTabChange} currentTab={currentTsTab} />

      {isLoading && (
        <div className="no_dashboard">
          <Spin />
        </div>
      )}

      <Router>
        <Dashboards path="/ts/dashboards/*" />
        <DatasetList path="/ts/datasets/*" />
        <Search path="/ts/search" />
        <Datacards path="/ts/datacards/*" />
      </Router>
    </>
  );
};

export default InsightsThoughtspotMainPage;
