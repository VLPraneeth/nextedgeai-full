import { createContext, useCallback, useContext, useMemo } from 'react';

import usePersistedState from 'hooks/usePersistedState';

export type SidebarTab = 'datacards' | 'datasets';
export type DataCardListView = 'library' | 'studio';

export interface InsightsSidebarContextValue {
  sidebarOpen: boolean;
  toggleSidebar: () => void;

  sidebarTab: SidebarTab;
  setSidebarTab: (tab: SidebarTab) => void;
  openToTab: (tab: SidebarTab) => void;

  dataCardListView: DataCardListView;
  setDataCardListView: (view: DataCardListView) => void;
}

export type InsightsSidebarLocalStorageState = Pick<
  InsightsSidebarContextValue,
  'sidebarOpen' | 'sidebarTab' | 'dataCardListView'
>;

const InsightsSidebarContext = createContext<InsightsSidebarContextValue>({
  sidebarOpen: false,
  toggleSidebar: () => {},

  sidebarTab: 'datacards',
  setSidebarTab: () => {},
  openToTab: () => {},

  dataCardListView: 'library',
  setDataCardListView: () => {},
});

export const useInsightsSidebarContext = () => useContext(InsightsSidebarContext);

export const InsightsSidebarContextProvider = ({ children }: { children?: React.ReactNode }) => {
  const [
    { sidebarOpen, sidebarTab, dataCardListView },
    setSidebarState,
  ] = usePersistedState<InsightsSidebarLocalStorageState>('insights-sidebar-state', {
    sidebarOpen: true,
    sidebarTab: 'datacards',
    dataCardListView: 'library',
  });

  const openToTab = useCallback(
    (tab: SidebarTab) => {
      setSidebarState({
        dataCardListView,
        sidebarOpen: true,
        sidebarTab: tab,
      });
    },
    [setSidebarState, dataCardListView]
  );

  const toggleSidebar = useCallback(() => {
    setSidebarState({
      dataCardListView,
      sidebarTab,
      sidebarOpen: !sidebarOpen,
    });
  }, [setSidebarState, dataCardListView, sidebarTab, sidebarOpen]);

  const setSidebarTab = useCallback(
    (tab: SidebarTab) => {
      setSidebarState({
        dataCardListView,
        sidebarOpen,
        sidebarTab: tab,
      });
    },
    [setSidebarState, dataCardListView, sidebarOpen]
  );

  const setDataCardListView = useCallback(
    (view: DataCardListView) => {
      setSidebarState({
        sidebarOpen,
        sidebarTab,
        dataCardListView: view,
      });
    },
    [setSidebarState, sidebarOpen, sidebarTab]
  );

  const value = useMemo(() => {
    return {
      sidebarOpen,
      toggleSidebar,

      sidebarTab,
      openToTab,
      setSidebarTab,

      dataCardListView,
      setDataCardListView,
    };
  }, [sidebarOpen, toggleSidebar, sidebarTab, openToTab, setSidebarTab, dataCardListView, setDataCardListView]);

  return <InsightsSidebarContext.Provider value={value}>{children}</InsightsSidebarContext.Provider>;
};
