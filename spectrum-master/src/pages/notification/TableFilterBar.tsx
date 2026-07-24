// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Children } from 'react';

import './TableFilterBar.less';

function TableFilterBar({ children }) {
  return <div className="table-filter-bar">{children}</div>;
}

function TableFilters({ children }) {
  return (
    <div className="table-filters">
      {Children.map(children, (child) => (
        <div className="table-filter">{child}</div>
      ))}
    </div>
  );
}

function TableBulkActions({ children }) {
  return (
    <div className="table-bulk-actions">
      {Children.map(children, (child) => (
        <div className="bulk-action">{child}</div>
      ))}
    </div>
  );
}

TableFilterBar.Filters = TableFilters;
TableFilterBar.BulkActions = TableBulkActions;

if (process.env.NODE_ENV !== 'production') {
  const { any } = require('prop-types');

  TableFilterBar.propTypes = {
    children: any,
  };
}

export default TableFilterBar;
export { TableFilterBar, TableBulkActions, TableFilters };
