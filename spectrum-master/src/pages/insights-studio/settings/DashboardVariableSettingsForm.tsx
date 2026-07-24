//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { withI18n } from 'components/I18nProvider';
import { Stack } from 'components/layout';
import { TranslatedText } from 'components/typography';
import { DatasetVariable } from 'store/insights-studio/types';

import MultiVariableMapping, { VariableMapping } from './MultiVariableMapping';

export interface DashboardVariableSettingsFormProps {
  dashboardVariable?: Record<string, DatasetVariable>;
  onChange?: (values: VariableMapping[]) => void;
  value?: VariableMapping[];
}

const DashboardVariableSettingsForm = ({ dashboardVariable, onChange, value }: DashboardVariableSettingsFormProps) => {
  if (dashboardVariable && !Object.keys(dashboardVariable)?.length) {
    return <TranslatedText text="unexpected_configuration" />;
  }

  return (
    <Stack>
      <MultiVariableMapping
        name="variableMapping"
        dashboardVariable={dashboardVariable}
        onChange={onChange}
        value={value}
      />
    </Stack>
  );
};

export default withI18n(DashboardVariableSettingsForm, 'InsightsStudio.Settings');
