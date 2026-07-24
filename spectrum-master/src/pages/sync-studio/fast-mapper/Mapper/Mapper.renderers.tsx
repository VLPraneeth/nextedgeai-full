import { CellClassParams } from 'ag-grid-community';
import { ICellEditor, ICellEditorParams } from 'ag-grid-community';
import { Icon, Tooltip } from 'antd';
import Select, { SelectProps, OptionProps } from 'antd/lib/select';
import cx from 'classnames';
import { find } from 'lodash';
import { startsWith } from 'lodash';
import { forwardRef, useImperativeHandle, useMemo, useRef } from 'react';
import * as React from 'react';

import InlineSvg from 'components/icons/InlineSvg';
import { EntityItem, FieldItem } from 'components/inputs/FieldOptions';
import { Divider } from 'components/layout';
import { TextTag } from 'components/text-tag';
import { Text } from 'components/typography';
import { useConnectorIdToMetadataMap } from 'store/connectors';
import { connectorIsCustomDraft } from 'utils/ConnectorUtil';
import { tNamespaced } from 'utils/i18nUtil';

import { FastMapperMode } from '../FastMapperModal';
import { ConnectorOption, DirectionOption, EntityFieldOption } from '../types';
import { HEADER_KEY_PREFIX } from './Mapper.constants';
import { useMapper, useEditableCell } from './Mapper.hooks';
import { MapperFields, ValidColumnDefFieldId } from './Mapper.types';
import { getDirections } from './Mapper.utils';

import './Mapper.scss';

interface OptionsChildren {
  props: {
    apiName?: string;
    displayName?: string;
    title?: string;
  };
}

const tn = tNamespaced('CellRenderer');

export const CellAction = ({ data, colDef }: CellClassParams) => {
  const {
    tableDataHandlers: { deleteRow },
  } = useMapper(colDef?.cellRendererParams?.mode);

  let errorMessage = data.errorMessage;
  if (data.safeErrorMessage) {
    errorMessage = <Text beDangerous>{data.errorMessage}</Text>;
  }
  return (
    <div className="cell-action__container">
      {data.errorMessage && (
        <Tooltip mouseEnterDelay={0.5} title={errorMessage}>
          <Icon type="exclamation-circle" data-testid={`error-action`} theme="filled" />
        </Tooltip>
      )}
      {colDef?.cellRendererParams?.mode === FastMapperMode.ADD && (
        <Icon
          type="delete"
          theme="filled"
          data-testid={`delete-action`}
          onClick={() => {
            deleteRow({ id: data.id, externalUpdate: false });
          }}
        />
      )}
    </div>
  );
};

export const ConnectorRenderer = ({ value, data, colDef, context }: CellClassParams) => {
  const fieldId = colDef?.cellRendererParams?.fieldId;
  const showApiName = colDef?.cellRendererParams?.showApiName;
  const containerRef = useRef<HTMLDivElement>(null);
  const connectorMetadataMap = useConnectorIdToMetadataMap();

  const { fieldData, editNewField } = useMapper(
    colDef?.cellEditorParams?.mode,
    data,
    fieldId,
    colDef?.cellEditorParams?.initialMappings
  );

  return useMemo(() => {
    if (data[fieldId]) {
      const field = find(fieldData, { id: value });
      if (field) {
        if (fieldId === MapperFields.SYNAPSE_ID) {
          const { iconUri, name: displayName } = field as ConnectorOption;
          return (
            <CellItem
              showTooltip
              title={displayName}
              prefix={iconUri && displayName && <InlineSvg src={iconUri} title={displayName} />}
              showDraftTag={connectorIsCustomDraft(connectorMetadataMap[data.synapseId])}
            />
          );
        } else if (fieldId === MapperFields.SYNAPSE_ENTITY_ID) {
          const { displayName, apiName } = field as EntityFieldOption;
          return (
            <span className="synri-tooltip-container">
              <EntityItem displayName={displayName} apiName={apiName} showApiName={showApiName} enableTooltip />
            </span>
          );
        } else if (fieldId === MapperFields.SYNAPSE_FIELD_ID) {
          const { displayName, apiName, dataType } = field as EntityFieldOption;
          return (
            <span className="synri-tooltip-container">
              <FieldItem
                displayName={displayName}
                apiName={apiName}
                dataType={dataType}
                showApiName={showApiName}
                enableTooltip
              />{' '}
            </span>
          );
        } else if (fieldId === MapperFields.SYNC_DIRECTION_ID) {
          const { displayName } = (field as unknown) as DirectionOption;
          return <CellItem showTooltip title={displayName} />;
        } else if (fieldId === MapperFields.SYNCARI_ENTITY_FIELD_ID) {
          const { displayName, apiName, dataType, createNewSyncariField } = field as EntityFieldOption;

          return (
            <div ref={containerRef} className="cell-renderer">
              <span className="cell-renderer__tooltip-container">
                <FieldItem
                  displayName={displayName}
                  apiName={apiName}
                  dataType={dataType}
                  showApiName={showApiName}
                  enableTooltip
                />
              </span>
              {createNewSyncariField && (
                <div
                  onMouseDown={() => {
                    editNewField(containerRef);
                  }}>
                  <Icon type="edit" theme="filled" className="cell-renderer__icon" />
                </div>
              )}
            </div>
          );
        }
      }
    }
    return value ?? null;
    // React, understandably, doesn't like having a ref.current value in a
    // dependency array. Unfortunately, we need it in the list in order for
    // component to properly update the component ref.
    // eslint-disable-next-line
  }, [data, editNewField, fieldData, fieldId, showApiName, containerRef.current, value]);
};

export const FieldRenderer = ({ data, colDef }: CellClassParams) => {
  if (colDef) {
    const { cellRendererParams: cellParams } = colDef;
    const dataType = data?.[cellParams.dataType];
    const displayName = data?.[cellParams.displayNameKey];
    const apiName = data?.[cellParams.apiNameKey];
    const showApiName = cellParams.showApiName;

    return (
      <>
        {dataType && (
          <Tooltip mouseEnterDelay={0.5} placement="topLeft" title={tn('api_name', { apiName })}>
            <span className="synri-tooltip-container">
              <FieldItem displayName={displayName} apiName={apiName} dataType={dataType} showApiName={showApiName} />
            </span>
          </Tooltip>
        )}
        {!dataType && apiName && displayName && (
          <Tooltip mouseEnterDelay={0.5} placement="topLeft" title={tn('api_name', { apiName })}>
            <span className="synri-tooltip-container">
              <EntityItem displayName={displayName} apiName={apiName} showApiName={showApiName} />
            </span>
          </Tooltip>
        )}
      </>
    );
  }
  return null;
};

export const SynapseRenderer = ({ data, colDef }: CellClassParams) => {
  const connectorMetadataMap = useConnectorIdToMetadataMap();
  if (colDef) {
    return (
      <CellItem
        showTooltip
        title={data.synapseName}
        showDraftTag={connectorIsCustomDraft(connectorMetadataMap[data.synapseId])}
      />
    );
  }
  return null;
};

export const DirectionRenderer = ({ value, data }: CellClassParams) => {
  return useMemo(() => {
    const direction = find(getDirections(), { id: data[MapperFields.SYNC_DIRECTION_ID] });
    return direction ? <CellItem showTooltip title={direction.displayName} /> : value ?? null;
  }, [data, value]);
};

export interface CellItemProps {
  className?: string;
  title?: string | React.ReactElement;
  showTooltip?: boolean;
  showDraftTag?: boolean;
  prefix?: React.ReactElement | string | null;
  children?: React.ReactElement | string;
}

export const CellItem = ({ className, prefix, title, showTooltip, showDraftTag }: CellItemProps) => {
  return (
    <Tooltip mouseEnterDelay={0.5} title={showTooltip && title}>
      <div className={cx('mapping-options', className)}>
        {prefix}
        <span className="mapping-options__option">{title}</span>
        {showDraftTag && <TextTag text="Draft" color="orange" />}
      </div>
    </Tooltip>
  );
};

export interface CellPicklistProps extends Omit<SelectProps<string>, 'onChange'> {
  options: React.ReactElement[];
  onChange: (value: string) => void;
  stickyItems: React.ReactElement | null;
  createFieldVisible?: boolean;
}

const CellPicklist = ({ options, onChange, defaultValue, loading, stickyItems, defaultOpen }: CellPicklistProps) => {
  const divRef = React.useRef<HTMLDivElement>(null);
  React.useEffect(() => {
    const focusAndOpenDropdown = () => {
      if (!defaultOpen) {
        return;
      }
      const arrowIcon = divRef.current?.querySelector('.ant-select-arrow') as HTMLSpanElement;
      if (arrowIcon) {
        arrowIcon.click();
      }
    };

    const timer = setTimeout(focusAndOpenDropdown, 100);
    return () => clearTimeout(timer);
  }, [defaultOpen]);

  const dropdownRender = useMemo(() => {
    return stickyItems
      ? (menu: React.ReactNode) => (
          <div>
            {stickyItems}
            <Divider y="z" />
            {menu}
          </div>
        )
      : undefined;
  }, [stickyItems]);

  return (
    <div ref={divRef} className="synri-cell-picklist">
      <Select
        className="synri-cell-picklist"
        value={defaultValue}
        onChange={onChange}
        showSearch
        loading={loading}
        dropdownMatchSelectWidth
        dropdownRender={dropdownRender}
        filterOption={(inputValue: string, option: React.ReactElement<OptionProps>) => {
          const containsInputValue = getOptionTextValue(option as OptionsChildren)
            ?.toLowerCase()
            ?.includes(inputValue.toLowerCase());

          const isSectionHeader = startsWith(option.key as string, HEADER_KEY_PREFIX);

          return containsInputValue || isSectionHeader;
        }}
        optionFilterProp="title">
        {options}
      </Select>
    </div>
  );
};

export interface ConnectorEntityEditorRef extends Omit<ICellEditor, 'getValue'> {
  getValue: () => string;
}

export interface CellEditorParams extends Omit<ICellEditorParams, 'value'> {
  value: string;
}

export const ConnectorEntityEditor = forwardRef<ConnectorEntityEditorRef, CellEditorParams>(
  ({ value, data, colDef }, ref) => {
    const { options, loading, onChange, stickyItems, row } = useEditableCell(
      colDef?.cellEditorParams?.fieldId,
      data.id,
      colDef?.cellEditorParams?.mode,
      value
    );
    const fieldId: ValidColumnDefFieldId = colDef?.cellEditorParams?.fieldId;
    const cellValue = row?.[fieldId];

    useImperativeHandle(ref, () => ({
      getValue: () => {
        return cellValue || '';
      },
    }));

    const defaultOpen = colDef?.cellEditorParams?.focusedField === fieldId;

    return (
      <CellPicklist
        key={`${data.id}${colDef?.field}`}
        onChange={onChange}
        stickyItems={stickyItems}
        loading={loading}
        options={options}
        defaultValue={cellValue}
        defaultOpen={defaultOpen}
      />
    );
  }
);

const getOptionTextValue = (option: OptionsChildren) =>
  `${option?.props?.apiName}${option?.props?.displayName}${option?.props?.title}`;
