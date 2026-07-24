import { Form, Tooltip } from 'antd';

import { ReactComponent as TrashIcon } from 'assets/icons/Trash.svg';
import Button from 'components/Button';
import InputWithLabel from 'components/inputs/InputWithLabel';
import Select from 'components/inputs/Select';
import { HStack } from 'components/layout';
import { SelectInputProps } from 'components/SelectInput';
import { VizerDisplayFormat } from 'components/vizer/types';
import { DataColumn } from 'store/insights-studio/types';
import { tc, tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('InsightsStudio');

const displayFormatOptions = [
  { value: 'currency', label: 'Currency' },
  { value: 'number', label: 'Number' },
  { value: 'percent', label: 'Percent' },
  { value: 'text', label: 'Text' },
];

interface ColumnInputProps {
  allowRemove: boolean;
  column: DataColumn;
  columnIndex: number;
  columnOptions: SelectInputProps['options'];
  removeColumn?: (columnIndex: number) => void;
  updateColumn: (value: string, columnIndex: number) => void;
  updateFormat: (value: VizerDisplayFormat, columnIndex: number) => void;
}
export const ColumnInput = ({
  allowRemove,
  column,
  columnIndex,
  columnOptions,
  removeColumn,
  updateColumn,
  updateFormat,
}: ColumnInputProps) => {
  return (
    <HStack align="center" justify={'space-between'} grow className="data-card-config-step__input-group">
      <InputWithLabel
        label={tc('column')}
        tooltip={tn('Tooltips.column')}
        style={{
          flex: 1,
          minWidth: '0',
        }}
        input={
          <Form.Item>
            <Select
              value={column.name}
              id="columns-input"
              optionData={columnOptions}
              onChange={(value: string) => {
                updateColumn(value, columnIndex);
              }}
              dropdownMatchSelectWidth={false}
            />
          </Form.Item>
        }
      />

      <InputWithLabel
        label={tc('display_format')}
        tooltip={tn('Tooltips.display_format')}
        style={{ flex: 1 }}
        input={
          <Form.Item>
            <Select
              id="columns-input"
              value={column.displayFormat}
              optionData={displayFormatOptions}
              onChange={(value: VizerDisplayFormat) => {
                updateFormat(value, columnIndex);
              }}
            />
          </Form.Item>
        }
      />

      {allowRemove && removeColumn && (
        <Tooltip title={tn('data_card_remove_column')}>
          <Button type="link" onClick={() => removeColumn(columnIndex)}>
            <TrashIcon className="trash-icon" />
          </Button>
        </Tooltip>
      )}
    </HStack>
  );
};
