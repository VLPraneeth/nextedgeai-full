import { Icon, Tooltip } from 'antd';
import { ChangeEvent, useState } from 'react';

import Input from 'components/inputs/Input';
import { HStack } from 'components/layout';
import { tNamespaced } from 'utils/i18nUtil';

export interface DatasetAliasInputProps {
  datasetId: string;
  updateSelectedItem: (datasetId: string, oldAlias: string | undefined, newAlias: string | undefined) => void;
  alias: string | undefined;
}

const tn = tNamespaced('InsightsStudio');

export const DatasetAliasInput = ({ datasetId, updateSelectedItem, alias: aliasFromProps }: DatasetAliasInputProps) => {
  const [editingAlias, setEditingAlias] = useState(false);
  const [alias, setAlias] = useState(aliasFromProps);

  const updateItem = () => {
    setEditingAlias(false);
    updateSelectedItem(datasetId, aliasFromProps, alias);
  };

  return (
    <HStack>
      {editingAlias ? (
        <Input
          size="small"
          defaultValue={aliasFromProps || ''}
          placeholder={tn('alias_placeholder')}
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
          {aliasFromProps && (
            <>
              <Icon style={{ fontSize: 10, paddingRight: 4 }} type="arrow-right" />
              {` ${aliasFromProps}`}
            </>
          )}
        </span>
      )}
      {!editingAlias && (
        <Tooltip title={aliasFromProps ? tn('edit_alias') : tn('add_alias')}>
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
