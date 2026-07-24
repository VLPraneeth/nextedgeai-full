import { useCallback } from 'react';

import InputWithLabel from 'components/inputs/InputWithLabel';
import { Stack } from 'components/layout';
import { SkullInput } from 'components/skull';
import AppConstants from 'utils/AppConstants';
import { isNotNullOrUndefined } from 'utils/TypeUtils';

const DatatypeOverride: Record<string, string> = {
  [AppConstants.INPUT_TYPE.BOOLEAN]: AppConstants.INPUT_TYPE.CHECKBOX,
} as const;

export type SkullColumnsType = {
  span: number;
  items: SkullInput[];
}[];

export interface SkullPanelProps {
  configurations: SkullInput[];
  value: Record<string, string | boolean>;
  onChange: (value: Record<string, string | boolean>) => void;
}

const SkullPanel = ({ configurations, value = {}, onChange, ...props }: SkullPanelProps) => {
  const onChangeHandler = useCallback(
    (name, evt) =>
      onChange({
        ...value,
        [name]: getValue(evt),
      }),
    [onChange, value]
  );

  return (
    <Stack>
      {configurations.map((configuration) => {
        const datatype =
          configuration.datatype && DatatypeOverride[configuration.datatype]
            ? DatatypeOverride[configuration.datatype]
            : configuration.datatype || 'string';
        return (
          <InputWithLabel
            {...configuration}
            datatype={datatype}
            defaultValue={value[configuration.name]}
            onChange={(evt: string | React.ChangeEvent<HTMLInputElement>) => {
              onChangeHandler(configuration.name, evt);
            }}
          />
        );
      })}
    </Stack>
  );
};

const getValue = (value: string | React.ChangeEvent<HTMLInputElement>) => {
  if (typeof value === 'string') {
    return value;
  } else if (isNotNullOrUndefined(value?.currentTarget?.value)) {
    return value.currentTarget.value;
  } else if (typeof value?.target?.checked === 'boolean') {
    return value.target.checked;
  } else if (value?.target?.value) {
    return value.target.value;
  }
  return value;
};

export default SkullPanel;
