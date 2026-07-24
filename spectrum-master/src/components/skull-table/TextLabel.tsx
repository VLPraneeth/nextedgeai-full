import { ICellEditorParams } from 'ag-grid-community';

import { Text } from 'components/typography';

export interface TextLabelProps extends Omit<ICellEditorParams, 'value'> {
  value: { label: string; value: string };
}

const TextLabel = ({ value }: TextLabelProps) => {
  return <Text>{value.label}</Text>;
};

export default TextLabel;
