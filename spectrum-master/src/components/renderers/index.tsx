import { ICellRendererParams } from 'ag-grid-community';
import Tooltip from 'antd/lib/tooltip';
import { capitalize } from 'lodash';
import * as React from 'react';

import { CopyToClipboardTooltip } from 'components/copy-to-clipboard/CopyToClipboardTooltip';
import { EMPTY_ARRAY } from 'store/constants';
import { t } from 'utils/i18nUtil';

import BooleanCellRenderer from './BooleanCellRenderer';
import DataTypeCellRenderer from './DataTypeCellRenderer';
import DateCellRenderer, { LastUpdatedDateCellRenderer } from './DateCellRenderer';
import IdMappingCellRenderer from './IdMappingCellRenderer';
import RulesCellRenderer from './RulesRenderer';
import ScoreCellRenderer from './ScoreCellRenderer';
import StatusCellRenderer from './StatusCellRenderer';
import { FieldMetadata } from './types';
import UsedInRenderer from './UsedInRenderer';

import './TruncatedTextCell.less';

export interface TruncatedTextCellProps {
  children: React.ReactNode;
}

const TruncatedTextCell = ({ children }: TruncatedTextCellProps) => {
  return (
    <Tooltip title={children} placement="topLeft" mouseEnterDelay={0.5}>
      <span className="synri-truncated-text-cell">{children}</span>
    </Tooltip>
  );
};

export interface TruncatedTextCopyCellProps extends TruncatedTextCellProps {
  textToCopy: string;
}

export const TruncatedTextCopyCell = ({ children, textToCopy }: TruncatedTextCopyCellProps) => {
  return (
    <CopyToClipboardTooltip textToCopy={textToCopy}>
      <span className="synri-truncated-text-cell">{children}</span>
    </CopyToClipboardTooltip>
  );
};

/* utility renderers */
export const BooleanFieldRenderer = (flag: boolean): string => capitalize(JSON.stringify(flag));
export const CapitalizedStringRenderer = (text: string) => capitalize(text);
export const makeDelimitedListRenderer = (delimiter: string) => (items?: string[]) =>
  (Array.isArray(items) ? items : EMPTY_ARRAY).join(delimiter);
// Ignoring TS check here since t is accepting pieces of the i18n key.
// @ts-ignore
export const I18nStringRenderer = (i18nPrefix: string) => (key: string) => t(`${i18nPrefix}.${key}`);
export const truncatedTextRenderer = (baseRendererFn: RendererFn): RendererFn => (text, record, index) => (
  <TruncatedTextCell>{baseRendererFn(text, record, index)}</TruncatedTextCell>
);

export const truncatedTextCopyRenderer = (baseRendererFn: RendererFn): RendererFn => (text, record, index) => {
  return <TruncatedTextCopyCell textToCopy={text}>{baseRendererFn(text, record, index)}</TruncatedTextCopyCell>;
};

export type RendererFn<T = any, R = any> = (text: T, record: R, index: number) => React.ReactNode;
export type RendererMap = Record<string, RendererFn>;

export const defaultRendererMap: RendererMap = {
  // specific columns
  dataType: DataTypeCellRenderer,
  lastUpdated: LastUpdatedDateCellRenderer,
  Rules: RulesCellRenderer,
  status: StatusCellRenderer,
  tags: makeDelimitedListRenderer(', '),
  type: CapitalizedStringRenderer,
  usedIn: UsedInRenderer,

  // types
  boolean: BooleanCellRenderer,
  datetime: DateCellRenderer,
  idMapping: IdMappingCellRenderer,
  picklistValues: truncatedTextRenderer(makeDelimitedListRenderer(', ')),
  score: ScoreCellRenderer,
};

/** enhances Ag Grid typing to be more specific */
export interface EnhancedAgCellRendererParams<Value extends any = any, Data extends any = any>
  extends Omit<ICellRendererParams, 'value' | 'formatValue' | 'getValue' | 'valueFormatted' | 'setValue' | 'data'> {
  data: Data;
  formatValue: (value: Value) => string;
  getValue: () => Value;
  setValue: (value: Value) => void;
  value: Value;
  valueFormatted: string;
}

/** wraps an Ant compatible renderer so we can use the Renderer with Ag-Grid */
export const withAntRenderer = <Value extends any, Data extends any>(renderer: RendererFn<Value, Data>) => (
  item: EnhancedAgCellRendererParams<Value, Data>
) => renderer(item.value as Value, item.data as Data, item.rowIndex);

/**
 * Used with Ant Tables
 * returns a renderer for a given field, or it's dataType. If none is found, 'undefined' is
 * is returned so that the default renderer will be used.
 *
 */
export const getRendererFromMetadata = (
  { label, dataType }: FieldMetadata,
  rendererMap: typeof defaultRendererMap = defaultRendererMap
) => (dataType in rendererMap ? rendererMap[dataType] : label in rendererMap ? rendererMap[label] : undefined);

/**
 * Used with AgGrid / AgTable
 * returns a map of `frameworkComponents` from the rendererMap for the given metadata
 *
 */
export const agFrameworkComponentsFromRendererMap = (rendererMap: RendererMap = defaultRendererMap) =>
  Object.fromEntries(Object.entries(rendererMap).map(([key, renderer]) => [key, withAntRenderer(renderer)]));

export const defaultAgFrameworkComponents = agFrameworkComponentsFromRendererMap();
