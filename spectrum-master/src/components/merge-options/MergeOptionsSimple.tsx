import { merge } from 'lodash';
import { useEffect } from 'react';

import Checkbox from 'components/Checkbox';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import Select from 'components/inputs/Select';
import { Spacer, Stack } from 'components/layout';
import { Text } from 'components/typography';
import useSetState from 'utils/useSetState';

import { useMergeOptionValues } from './MergeOptions.hooks';
import { MergeOptionValues, MergeOptionValuesSimple } from './MergeOptions.types';

export interface MergeOptionsState {
  autoArrange: boolean;
  installStrategy: MergeOptionValuesSimple;
}

export interface MergeOptionsProps {
  onChange: (update: MergeOptionsState) => void;
  defaultValue?: Partial<MergeOptionsState>;
}

const MergeOptions = ({ onChange, defaultValue }: MergeOptionsProps) => {
  const { tn } = useI18nContext();

  const [state, setState] = useSetState<MergeOptionsState>(() =>
    merge(
      {
        installStrategy: MergeOptionValues.REPLACE,
        autoArrange: false,
      },
      defaultValue
    )
  );

  // The source and destination options are ignored for V1
  const [, , simpleOptions] = useMergeOptionValues();

  useEffect(() => {
    onChange(state);
  }, [onChange, state]);

  return (
    <Stack className="synri-merge-options-container" spacing="xs">
      <Stack spacing="md">
        <Text color="gray-900">{tn('merge_settings')}</Text>
        <Select
          value={state.installStrategy}
          dropdownMatchSelectWidth={false}
          onChange={(newValue: MergeOptionValuesSimple) => {
            setState({ installStrategy: newValue });
          }}
          optionData={simpleOptions}
        />
      </Stack>
      <Spacer />
      <Checkbox checked={state.autoArrange} onChange={(evt) => setState({ autoArrange: evt.target.checked })}>
        {tn('auto_arrange_nodes')}
      </Checkbox>
    </Stack>
  );
};

export default withI18n(MergeOptions, 'MergeOptions');
