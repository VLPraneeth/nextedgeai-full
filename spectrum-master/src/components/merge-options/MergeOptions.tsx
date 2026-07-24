import produce from 'immer';
import { merge } from 'lodash';
import { useEffect } from 'react';

import Checkbox from 'components/Checkbox';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import Select from 'components/inputs/Select';
import { HStack, Spacer, Stack } from 'components/layout';
import { Text } from 'components/typography';
import useSetState from 'utils/useSetState';

import { useMergeOptionValues } from './MergeOptions.hooks';
import { MergeOptionValues } from './MergeOptions.types';

export interface MergeOptionsState {
  autoArrange: boolean;
  global: {
    source: MergeOptionValues;
    destination: MergeOptionValues;
  };
}

export interface MergeOptionsProps {
  onChange: (update: MergeOptionsState) => void;
  defaultValue?: Partial<MergeOptionsState>;
}

/**
 * This version of MergeOptions was created to support various options for both
 * source and destination sides of the pipeline. It was determined too difficult
 * to support all these options so we're currently using MergeOptionSimple.
 */
const MergeOptions = ({ onChange, defaultValue }: MergeOptionsProps) => {
  const { tn } = useI18nContext();

  const [state, setState] = useSetState<MergeOptionsState>(() =>
    merge(
      {
        autoArrange: true,
        global: {
          source: MergeOptionValues.REPLACE,
          destination: MergeOptionValues.REPLACE,
        },
      },
      defaultValue
    )
  );

  const [sourceOptions, destinationOptions] = useMergeOptionValues();

  useEffect(() => {
    onChange(state);
  }, [onChange, state]);

  return (
    <Stack className="synri-merge-options-container" spacing="xs">
      <HStack spacing="md">
        <Text> </Text>
        <Text color="gray-700" weight="bold">
          {tn('source_nodes')}
        </Text>
        <Text color="gray-700" weight="bold">
          {tn('destination_nodes')}
        </Text>
      </HStack>
      <HStack spacing="md" className="synri-merge-row-borders">
        <Text color="gray-900">{tn('default_merge_settings')}</Text>
        <Select
          value={state.global.source}
          dropdownMatchSelectWidth={false}
          onChange={(newValue: MergeOptionValues) => {
            setState((prev) =>
              produce(prev, (draft) => {
                draft.global.source = newValue;
              })
            );
          }}
          optionData={sourceOptions}
        />
        <Select
          value={state.global.destination}
          dropdownMatchSelectWidth={false}
          onChange={(newValue: MergeOptionValues) => {
            setState((prev) =>
              produce(prev, (draft) => {
                draft.global.destination = newValue;
              })
            );
          }}
          optionData={destinationOptions}
        />
      </HStack>
      <Spacer />
      <Checkbox checked={state.autoArrange} onChange={(evt) => setState({ autoArrange: evt.target.checked })}>
        {tn('auto_arrange_nodes')}
      </Checkbox>
    </Stack>
  );
};

export default withI18n(MergeOptions, 'MergeOptions');
