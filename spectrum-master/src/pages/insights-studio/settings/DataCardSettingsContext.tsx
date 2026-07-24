//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

import { useEnhancedSelector } from 'hooks/redux';
import { EMPTY_OBJECT } from 'store/constants';
import { DataCardSettingsValue } from 'store/insights-studio/types';
import { makeUserDataCardConfigKey } from 'store/insights-studio/util';

import {
  ConfigureDashboardVariableOptions,
  DataCardSettingsContextProps,
  DataCardSettingsContextProviderProps,
  DataCardSettingsOptions,
} from './types';

const DataCardSettingsContext = createContext<DataCardSettingsContextProps>({
  showSettings: (show = true, options = {}) => {},
  showConfigureDashboardVariable: (show = true, options = {}) => {},
  showDashboardVariableSettings: (show = true, options = {}) => {},
  settingsVisible: false,
  dashboardSettingsVisible: false,
  dashboardVariableSettingsVisible: false,
  setCurrentValue: () => {},
  mergeCurrentValue: (newValue: DataCardSettingsValue) => {},
  settingsOptions: {},
  configureDashboardVariableOptions: {},
  dashboardVariableSettingsOptions: {},
  currentValue: {},
});

export const useDataCardSettingsContext = () => useContext(DataCardSettingsContext);

export const DataCardSettingsContextProvider = ({ children, value }: DataCardSettingsContextProviderProps) => {
  const [settingsVisible, setSettingsVisible] = useState<boolean>(false);
  const [settingsOptions, setSettingsOptions] = useState<DataCardSettingsOptions>({});
  const [currentValue, setCurrentValue] = useState<DataCardSettingsValue>({});
  const [dashboardSettingsVisible, setDashboardSettingsVisible] = useState<boolean>(false);
  const [
    configureDashboardVariableOptions,
    setConfigureDashboardVariableOptions,
  ] = useState<ConfigureDashboardVariableOptions>({});
  const [dashboardVariableSettingsVisible, setDashboardVariableSettingsVisible] = useState<boolean>(false);
  const [
    dashboardVariableSettingsOptions,
    setDashboardVariableSettingsOptions,
  ] = useState<ConfigureDashboardVariableOptions>({});

  const userDataCardConfig = useEnhancedSelector((state) => state.insightsStudio.userDataCardConfig);

  // Default to true
  const showSettings = useCallback((visible: any, options: any) => {
    setSettingsVisible(!visible === false);
    setSettingsOptions(options);
  }, []);

  const showConfigureDashboardVariable = useCallback((visible: any, options: any) => {
    setDashboardSettingsVisible(visible !== false);
    setConfigureDashboardVariableOptions(options);
  }, []);

  const mergeCurrentValue = useCallback(
    (newValue: DataCardSettingsValue) => setCurrentValue((current) => ({ ...current, ...newValue })),
    []
  );

  const showDashboardVariableSettings = useCallback((visible: any, options: any) => {
    setDashboardVariableSettingsVisible(visible !== false);
    setDashboardVariableSettingsOptions(options);
  }, []);

  useEffect(() => {
    if (settingsOptions?.dashboardId && settingsOptions?.dataCard?.id) {
      const userConfig =
        userDataCardConfig[makeUserDataCardConfigKey(settingsOptions.dashboardId, settingsOptions.dataCard.id)] || {};
      setCurrentValue((current) => {
        return { ...current, ...(userConfig.configuration || {}) };
      });
    }
  }, [settingsOptions?.dashboardId, settingsOptions?.dataCard?.id, userDataCardConfig]);

  const contextValue = useMemo(() => {
    return {
      ...{
        showSettings,
        showConfigureDashboardVariable,
        showDashboardVariableSettings,
        setCurrentValue,
        mergeCurrentValue,
        currentValue,
        settingsVisible,
        dashboardSettingsVisible,
        dashboardVariableSettingsOptions,
        dashboardVariableSettingsVisible,
        configureDashboardVariableOptions,
        settingsOptions,
      },
      ...(value || EMPTY_OBJECT),
    };
  }, [
    configureDashboardVariableOptions,
    currentValue,
    dashboardSettingsVisible,
    dashboardVariableSettingsOptions,
    dashboardVariableSettingsVisible,
    mergeCurrentValue,
    settingsOptions,
    settingsVisible,
    showConfigureDashboardVariable,
    showDashboardVariableSettings,
    showSettings,
    value,
  ]);

  return <DataCardSettingsContext.Provider value={contextValue}>{children}</DataCardSettingsContext.Provider>;
};
