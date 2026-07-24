//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Table } from 'antd';
import classNames from 'classnames';
import { map } from 'lodash';
import { Component } from 'react';

import { default as Renderer } from 'components/renderers/RendererContainer';

import './Table.less';

class sTable extends Component<any> {
  insertRenderer = (columns: any[]) => {
    const newColumns = map(columns, (column) => {
      const { renderer, ...rest } = column;
      if (renderer) {
        rest.render = Renderer(renderer);
      }
      return {
        ...rest,
      };
    });
    return newColumns;
  };

  render() {
    const { className, flat, columns, ...moreProps } = this.props;
    const cls = classNames('synri-table', className, {
      flat,
    });
    const updatedColumns = this.insertRenderer(columns);
    return <Table className={cls} columns={updatedColumns} {...moreProps} />;
  }
}

export default sTable;
