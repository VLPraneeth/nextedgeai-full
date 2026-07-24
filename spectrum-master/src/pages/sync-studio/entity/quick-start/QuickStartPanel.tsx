//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { useMatch, useParams } from '@reach/router';
import { useCallback, useEffect, useState } from 'react';

import { withI18n } from 'components/I18nProvider';
import { SkullConfig } from 'components/skull';
import { useEnhancedDispatch as useDispatch, useEnhancedSelector as useSelector } from 'hooks/redux';
import useNavigateTo from 'hooks/useNavigateTo';
import QuickStartWizard from 'pages/sync-studio/entity/quick-start/QuickStartWizard';
import { getEntities } from 'store/entity/actions';
import {
  useGetQuickStartAuthorConfigQuery,
  useGetQuickStartMarketplaceListQuery,
  useGetQuickStartSharedListQuery,
} from 'store/quick-start/api';
import { resetInstallStatus } from 'store/quick-start/slice';
import { QuickStart, QuickStartMode } from 'store/quick-start/types';
import AppConstants from 'utils/AppConstants';
import RouteConstants from 'utils/RouteConstants';
import { routeToMatch } from 'utils/StringUtil';
import { makeUrl } from 'utils/UrlUtil';

import { useCurrentSyncStudioRootTab } from '../SyncStudioRootTabs';
import QuickStartList from './QuickStartList';

const QuickStartPanel = () => {
  const { data: libraryQuickStarts } = useGetQuickStartMarketplaceListQuery({});
  const { data: sharedQuickStarts } = useGetQuickStartSharedListQuery({});
  const { data: quickStartAuthorConfig } = useGetQuickStartAuthorConfigQuery();
  const [quickStartVisible, setQuickStartVisible] = useState<boolean>(false);
  const [quickStartMode, setQuickStartMode] = useState<QuickStartMode>(QuickStartMode.AUTHOR);
  const [quickStartConfig, setQuickStartConfig] = useState<SkullConfig | null>(null);
  const [quickStart, setQuickStart] = useState<QuickStart | null>(null);
  const serverInstallStatus = useSelector((state) => state.quickStart.serverInstallStatus);
  const [entityNeedRefresh, setEntityNeedRefresh] = useState(false);
  const dispatch = useDispatch();
  const params = useParams();

  const [doneInitialRender, setDoneInitialRender] = useState(false);

  const quickStartRouteMatch = useMatch(routeToMatch(RouteConstants.QUICK_START));
  const quickStartAuthorMatch = useMatch(routeToMatch(RouteConstants.QUICK_START_AUTHOR));
  const quickStartInstallMatch = useMatch(routeToMatch(RouteConstants.QUICK_START_INSTALL));

  const { currentTab } = useCurrentSyncStudioRootTab();

  const navigate = useNavigateTo();

  const showAuthorQuickStart = useCallback(
    (quickStart: QuickStart | null) => {
      navigate(makeUrl(RouteConstants.QUICK_START_AUTHOR, { tabId: currentTab }));

      setQuickStart(quickStart);

      if (quickStartAuthorConfig) {
        setQuickStartConfig(quickStartAuthorConfig);
      }
    },
    [currentTab, navigate, quickStartAuthorConfig]
  );

  const showLibraryQuickStart = useCallback(
    (quickStart: QuickStart | null) => {
      navigate(makeUrl(RouteConstants.QUICK_START_INSTALL, { quickStartId: quickStart?.id, tabId: currentTab }));
      setQuickStart(quickStart);
    },
    [currentTab, navigate]
  );

  const closeQuickStartWizard = useCallback(() => {
    navigate(makeUrl(RouteConstants.QUICK_START, { tabId: currentTab }));
    if (quickStart) {
      if (entityNeedRefresh) {
        dispatch(getEntities());
        setEntityNeedRefresh((prev) => !prev);
      }
      dispatch(resetInstallStatus({ quickStartId: quickStart.id }));
    }
  }, [currentTab, dispatch, entityNeedRefresh, navigate, quickStart]);

  // Track the initial render
  useEffect(() => {
    if (!doneInitialRender) {
      setDoneInitialRender(true);
    }
  }, [doneInitialRender]);

  // Deep Linking
  useEffect(() => {
    if (quickStartInstallMatch) {
      // Route to Quick Start Installer
      const libraryItem = libraryQuickStarts?.find((item) => item.id === params?.quickStartId);
      const sharedItem = sharedQuickStarts?.find((item) => item.id === params?.quickStartId);

      if (!libraryItem && !sharedItem) {
        return;
      }

      setQuickStartVisible(true);
      setQuickStartMode(QuickStartMode.INSTALL);
      libraryItem ? setQuickStart(libraryItem) : sharedItem && setQuickStart(sharedItem);
    } else if (quickStartAuthorMatch) {
      // Route to Quick Start Authoring
      if (doneInitialRender) {
        setQuickStartVisible(true);
        setQuickStartMode(QuickStartMode.AUTHOR);
      } else {
        // Don't want to deep link back into author when the page is refreshed
        navigate(makeUrl(RouteConstants.QUICK_START, { tabId: currentTab }));
      }
    } else if (quickStartRouteMatch) {
      // Close Quick Start Panel
      setQuickStartVisible(false);
    }
  }, [
    currentTab,
    doneInitialRender,
    libraryQuickStarts,
    navigate,
    params?.quickStartId,
    quickStartAuthorMatch,
    quickStartInstallMatch,
    quickStartRouteMatch,
    sharedQuickStarts,
  ]);

  useEffect(() => {
    if (quickStart && serverInstallStatus[quickStart.id] === AppConstants.FETCH_STATUS.SUCCESS) {
      setEntityNeedRefresh(true);
    }
  }, [dispatch, quickStart, serverInstallStatus]);

  return (
    <>
      {quickStartVisible && (
        <QuickStartWizard
          key="config-wizard"
          visible={quickStartVisible}
          close={() => closeQuickStartWizard()}
          quickStart={quickStart}
          mode={quickStartMode}
          config={quickStartConfig}
        />
      )}
      <QuickStartList
        setQuickStartAuthorVisible={showAuthorQuickStart}
        setQuickStartInstallVisible={showLibraryQuickStart}
      />
    </>
  );
};

export default withI18n(QuickStartPanel, 'QuickStart');
