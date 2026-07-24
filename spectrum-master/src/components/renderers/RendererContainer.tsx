//
// Copyright (c) 2019-Present Syncari - All rights reserved.
// Container/Factory for the table column renderer
//

import { createElement } from 'react';

import TransactionChangesRenderer from 'pages/logs/TransactionChangesRenderer';
import AppConstants from 'utils/AppConstants';

import CheckRenderer from './CheckRenderer';
import ConnectorStatus from './ConnectorStatus';
import DateTimeRenderer from './DateTimeRenderer';
import LinkRenderer from './LinkRenderer';
import ListItemRenderer from './ListItemRenderer';
import TransactionDate from './TransactionDate';
import TransactionOriginalSources from './TransactionOriginalSources';
import TransactionRecordIdRenderer from './TransactionRecordIdRenderer';

// This map is most useful for renderers used across tables.
//
// If you have a renderer that is only specific to your table,
// use `render` in the column definition instead.
//
// Example:
//
// render: (text, record, index) => <div>{capitalize(text)}</div>,
//
const RENDERERS = {
  [AppConstants.RENDERER.LINK]: LinkRenderer,
  [AppConstants.RENDERER.CHECK]: CheckRenderer,
  [AppConstants.RENDERER.LIST_ITEM]: ListItemRenderer,
  [AppConstants.RENDERER.DATE_TIME]: DateTimeRenderer,
  [AppConstants.RENDERER.TRANSACTION_DATE]: TransactionDate,
  [AppConstants.RENDERER.TRANSACTION_CHANGES]: TransactionChangesRenderer,
  [AppConstants.RENDERER.TRANSACTION_ORIGINAL_SOURCES]: TransactionOriginalSources,
  [AppConstants.RENDERER.TRANSACTION_RECORD_ID]: TransactionRecordIdRenderer,
  [AppConstants.RENDERER.CONNECTOR_STATUS]: ConnectorStatus,
};

export default function RendererContainer(type: keyof typeof RENDERERS) {
  return (text: string, record: any, index: number) => {
    const comp = RENDERERS[type];
    if (comp) {
      return createElement(comp as any, { text, record, index });
    }
    return text;
  };
}
