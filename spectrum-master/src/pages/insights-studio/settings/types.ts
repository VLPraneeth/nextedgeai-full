//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { ReactNode, Dispatch, SetStateAction } from 'react';

import { DataCardWithData, DataCardSettingsValue } from 'store/insights-studio/types';

export interface DataCardSettingsOptions {
  dataCard?: DataCardWithData;
  dashboardId?: string;
}

export interface ConfigureDashboardVariableOptions {
  dashboardId?: string;
}

export interface DataCardSettingsContextProps {
  showSettings: (show?: boolean, options?: DataCardSettingsOptions) => void;
  showConfigureDashboardVariable: (show?: boolean, options?: ConfigureDashboardVariableOptions) => void;
  showDashboardVariableSettings: (show?: boolean, options?: ConfigureDashboardVariableOptions) => void;
  setCurrentValue: Dispatch<SetStateAction<DataCardSettingsValue>>;
  mergeCurrentValue: (newValue: DataCardSettingsValue) => void;
  currentValue: DataCardSettingsValue;
  settingsVisible: boolean;
  dashboardSettingsVisible: boolean;
  dashboardVariableSettingsVisible: boolean;
  configureDashboardVariableOptions: ConfigureDashboardVariableOptions;
  dashboardVariableSettingsOptions: ConfigureDashboardVariableOptions;
  settingsOptions?: DataCardSettingsOptions;
}

export interface DataCardSettingsContextProviderProps {
  children: ReactNode;
  value?: Partial<DataCardSettingsContextProps>;
}
