import { Radio as AntRadio } from 'antd';

import { Stack } from 'components/layout';

export interface RadioProps {
  value?: string;
  defaultValue?: string;
  optionData?: { value: string; label: string }[];
  readOnly?: boolean;
}

export function Radio({ defaultValue, value, optionData, readOnly, ...rest }: RadioProps) {
  return (
    <AntRadio.Group defaultValue={defaultValue} value={value} disabled={readOnly} {...rest}>
      <Stack spacing="xs">
        {optionData?.map((option) => {
          return (
            <AntRadio key={option.value} value={option.value} disabled={readOnly}>
              {option.label}
            </AntRadio>
          );
        })}
      </Stack>
    </AntRadio.Group>
  );
}
