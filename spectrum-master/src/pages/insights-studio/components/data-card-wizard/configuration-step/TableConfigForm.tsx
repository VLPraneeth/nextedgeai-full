import { cloneDeep } from 'lodash';
import { useEffect, useState } from 'react';

import Button from 'components/Button';
import { InlineTab, InlineTabs } from 'components/InlineTabs';
import { VizerDisplayFormat } from 'components/vizer/types';
import { tc } from 'utils/i18nUtil';

import { ColumnInput } from './ColumnInput';
import { VizTypeConfigFormProps } from './DataCardConfigStep';
import { VariablesConfigForm } from './VariablesConfigForm';

export const TableConfigForm = ({ vizConfig, updateConfig, columnOptions }: VizTypeConfigFormProps) => {
  const localConfig = cloneDeep(vizConfig);
  const [activeTab, setActiveTab] = useState('setup');

  useEffect(() => {
    if (!localConfig.columns || localConfig.columns.length < 1) {
      localConfig.columns = [{ name: '', displayName: '' }];
      updateConfig(localConfig);
    }
  });

  const updateColumn = (columnName: string, columnIndex: number) => {
    const colToUpdate = localConfig.columns?.[columnIndex];
    const selectedOption = columnOptions.find((opt) => opt.value === columnName);
    if (colToUpdate) {
      colToUpdate.name = columnName;
      colToUpdate.displayName = columnName;
      colToUpdate.displayFormat =
        selectedOption?.dataType === 'integer' || selectedOption?.dataType === 'double' ? 'number' : 'text';
      updateConfig(localConfig);
    }
  };

  const updateFormat = (format: VizerDisplayFormat, columnIndex: number) => {
    const colToUpdate = localConfig.columns?.[columnIndex];
    if (colToUpdate) {
      colToUpdate.displayFormat = format;
      updateConfig(localConfig);
    }
  };

  const addColumn = () => {
    localConfig.columns?.push({ name: '', displayFormat: 'text', displayName: '' });
    updateConfig(localConfig);
  };

  const removeColumn = (index: number) => {
    localConfig.columns?.splice(index, 1);
    updateConfig(localConfig);
  };

  return (
    <>
      <div className="data-card-config-step__tabs">
        <InlineTabs
          selectedTab={activeTab}
          onChange={(clickedTab) => {
            setActiveTab(clickedTab);
          }}>
          <InlineTab id="setup">{tc('setup')}</InlineTab>
          <InlineTab id="vars">{tc('variables')}</InlineTab>
        </InlineTabs>
      </div>
      {activeTab === 'setup' && (
        <>
          {vizConfig.columns?.map((column, i) => {
            return (
              <ColumnInput
                columnIndex={i}
                updateFormat={updateFormat}
                removeColumn={removeColumn}
                column={column}
                columnOptions={columnOptions}
                updateColumn={updateColumn}
                allowRemove
              />
            );
          })}
          <Button type="link" onClick={addColumn}>
            {tc('plus_add')} {tc('column')}
          </Button>
        </>
      )}
      {activeTab === 'vars' && (
        <VariablesConfigForm
          dataCardVariables={vizConfig.variablesMap}
          setVariables={(variables) => {
            localConfig.variablesMap = variables;
            updateConfig(localConfig);
          }}
        />
      )}
    </>
  );
};
