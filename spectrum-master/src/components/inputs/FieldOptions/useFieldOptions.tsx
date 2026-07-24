import { Tooltip } from 'antd';
import cx from 'classnames';
import { sortBy } from 'lodash';
import { useMemo } from 'react';

import FieldTypeBadge from 'components/FieldTypeBadge';
import { TextTag } from 'components/text-tag';
import { EntityField } from 'store/entity/types';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { humanize } from 'utils/StringUtil';

import { Option, OptionProps } from '../Select';

import './FieldOption.less';

const tn = tNamespaced('CellRenderer');

export type FieldItemProps = Pick<EntityField, 'dataType' | 'displayName' | 'apiName' | 'enableTooltip'> & {
  showApiName?: boolean;
  disabled?: boolean;
  disabledTooltip?: string;
  readOnly?: boolean;
};
export interface FieldOptionProps extends FieldItemProps {
  id: string;
  title?: string;
}

/**
 * Pass the field list here to generatea the optionFilter function to pass to <Select />
 */
export const fieldOptionFilter = (fieldList: FieldOptionProps[]) => (
  inputValue: string,
  option: React.ReactElement<OptionProps>
): boolean => {
  const lowerCaseInput = inputValue.toLowerCase();

  const field = fieldList.find((field) => field.id === option.props.value);

  return (
    !!field?.displayName.toLowerCase().includes(lowerCaseInput) ||
    !!field?.apiName.toLowerCase().includes(lowerCaseInput)
  );
};

export interface EntityItemProps extends Omit<FieldItemProps, 'dataType'> {
  postfix?: React.ReactElement;
  prefix?: React.ReactElement; // a tooltip element used to display datatype
  enableTooltip?: boolean; // shows tooltip displaying "displayName (apiName)" on apiName hover if true
  showApiName?: boolean;
}

export const EntityItem = ({
  displayName,
  apiName,
  enableTooltip,
  disabled,
  disabledTooltip,
  postfix,
  prefix,
  showApiName = true,
}: EntityItemProps) => {
  const shouldShowApiName = apiName && showApiName;
  const tooltip = tn('display_api_name', { apiName, displayName });

  return (
    <div className={cx('entity-item', disabled && 'entity-item--disabled')}>
      {prefix}
      {enableTooltip || disabledTooltip ? (
        <Tooltip mouseEnterDelay={0.5} title={disabledTooltip ?? tooltip}>
          <div className="entity-item__tooltip">
            <span className="entity-item__display-name">{displayName}</span>
            {shouldShowApiName && <span className="entity-item__api-name">({apiName})</span>}
          </div>
        </Tooltip>
      ) : (
        <>
          <span className="entity-item__display-name">{displayName}</span>
          {shouldShowApiName && <span className="entity-item__api-name">({apiName})</span>}
        </>
      )}
      {postfix}
      {/* TODO: truncate the middle of the API name if it extends passed the container.Currently just truncating the end. We should also show a tooltip if the text is truncated. */}
    </div>
  );
};

export const FieldItem = ({
  dataType,
  displayName,
  apiName,
  disabled,
  disabledTooltip,
  enableTooltip,
  showApiName,
  readOnly,
}: FieldItemProps) => {
  return (
    <EntityItem
      prefix={
        <Tooltip mouseEnterDelay={1} title={humanize(dataType)}>
          {/* This span is required to make the tooltip show */}
          <span className="entity-item__datatype-tooltip">
            <FieldTypeBadge dataType={dataType} description={dataType} disableTooltip size="small" />
          </span>
        </Tooltip>
      }
      postfix={
        readOnly ? (
          <span className="entity-item__readonly-tag">
            <TextTag text={tc('read_only')} color="gray" size="xs" />
          </span>
        ) : undefined
      }
      showApiName={showApiName}
      displayName={displayName}
      apiName={apiName}
      enableTooltip={enableTooltip}
      disabled={disabled}
      disabledTooltip={disabledTooltip}
    />
  );
};

/**
 * This is defined as a function instead of a component because the Select
 * component has to take a Select.Option directly as children.
 */
export const makeFieldOption = ({ id, title, disabled, ...rest }: FieldOptionProps) => {
  return (
    <Option key={id} value={id} title={title} disabled={disabled}>
      <FieldItem {...rest} disabled={disabled} />
    </Option>
  );
};

/**
 * useFieldOptions takes an array of EntityFields and return the options and
 * filterOption that can be passed directly to <Select />
 */
const useFieldOptions = (fieldList: FieldOptionProps[]) => {
  const sortedFields = useMemo(() => sortBy(fieldList, 'displayName'), [fieldList]);

  const fieldOptions = useMemo(() => sortedFields.map(makeFieldOption), [sortedFields]);
  const fieldFilterOptions = useMemo(() => fieldOptionFilter(sortedFields || []), [sortedFields]);

  return {
    options: fieldOptions,
    filterOption: fieldFilterOptions,
  };
};

export default useFieldOptions;
