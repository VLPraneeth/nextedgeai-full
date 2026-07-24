import { Icon, Tooltip } from 'antd';
import { ChangeEvent, useState } from 'react';

import Input from 'components/inputs/Input';
import { HStack } from 'components/layout';

import { PipelinePickerEntity, PipelinePickerEntityField } from './PipelinePicker.types';

export interface FieldAliasInputProps {
  field: PipelinePickerEntityField;
  entity: PipelinePickerEntity;
  updateSelectedItem: (entityId: string, fieldId?: string, fieldAlias?: string) => void;
}

export const FieldAliasInput = ({ entity, field, updateSelectedItem }: FieldAliasInputProps) => {
  const [editingAlias, setEditingAlias] = useState(false);
  const [alias, setAlias] = useState('');

  const updateItem = () => {
    setEditingAlias(false);
    updateSelectedItem(entity.id, field.id, alias);
  };

  return (
    <HStack>
      {editingAlias ? (
        <Input
          size="small"
          defaultValue={field.fieldAlias || ''}
          placeholder="Alias..."
          onChange={(evt: ChangeEvent<HTMLInputElement>) => setAlias(evt.target.value)}
          onKeyDown={(evt) => evt.key === 'Enter' && updateItem()}
          onBlur={() => updateItem()}
        />
      ) : (
        <span
          onClick={(evt) => {
            evt.preventDefault();
            setEditingAlias(true);
          }}>
          {field.fieldAlias && (
            <>
              <Icon style={{ fontSize: 10, paddingRight: 4 }} type="arrow-right" />
              {` ${field.fieldAlias}`}
            </>
          )}
        </span>
      )}
      {!editingAlias && (
        <Tooltip title={field.fieldAlias ? 'Edit alias' : 'Add alias'}>
          <Icon
            type="edit"
            onClick={(e) => {
              e.preventDefault();
              setEditingAlias(true);
            }}
          />
        </Tooltip>
      )}
    </HStack>
  );
};
