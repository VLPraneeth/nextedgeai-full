//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { useMemo, useEffect } from 'react';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { Stack } from 'components/layout';
import { TranslatedText } from 'components/typography';
import { DataCardSettingsValue } from 'store/insights-studio/types';

import DatasetVariableValue from '../dataset/variable/DatasetVariableValue';
import { useDataCardSettingsContext } from './DataCardSettingsContext';
import { ResetButton } from './ResetButton';

const SettingsForm = () => {
  const { settingsOptions, setCurrentValue, currentValue, mergeCurrentValue } = useDataCardSettingsContext();
  const dataCard = settingsOptions?.dataCard;
  const { tn } = useI18nContext();

  useEffect(() => {
    mergeCurrentValue({ ...dataCard?.configuration });
  }, [dataCard, mergeCurrentValue]);

  const configurationMeta = useMemo(
    () => (dataCard && Array.isArray(dataCard?.configurationMeta) ? dataCard.configurationMeta : []),
    [dataCard]
  );

  if (!dataCard || !configurationMeta) {
    return <TranslatedText text="unexpected_configuration" />;
  }

  return (
    <Stack>
      {configurationMeta.map((meta) => (
        <InputWithLabel
          {...meta}
          required
          key={meta.name}
          label={meta.displayName}
          tooltip={meta.helpSummary}
          input={
            <DatasetVariableValue
              {...meta}
              defaultValue={currentValue[meta.name] ?? dataCard?.configuration?.[meta.name]}
              onChange={(value) => setCurrentValue({ ...currentValue, [meta.name]: value })}
            />
          }
        />
      ))}
      <ResetButton
        onClick={() => {
          const defaultValues: DataCardSettingsValue = {};
          dataCard?.contents?.configuration?.variablesMap &&
            Object.values(dataCard.contents.configuration.variablesMap).forEach((variable) => {
              if (variable.apiName && variable.variableDefaultValue) {
                defaultValues[variable.apiName] = variable.variableDefaultValue;
              }
            });
          mergeCurrentValue({ ...defaultValues });
        }}>
        {tn('reset_all')}
      </ResetButton>
    </Stack>
  );
};

export default withI18n(SettingsForm, 'InsightsStudio.Settings');
