//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Icon } from 'antd';
import cx from 'classnames';
import { memo } from 'react';

import './CompositeOrder.less';

export interface CompositeOrderProps {
  /**
   * Number order it will be rendered.
   */
  order: number;

  /**
   * Extra classname that will be added to the fieldset.
   */
  className?: string;

  /**
   * Extra classname that will be added to the drag handle.
   */
  dragClassName?: string;

  /**
   * Handler the the up icon is clicked.
   */
  onClickUp: (index: number) => void;

  /**
   * Handler the the down icon is clicked.
   */
  onClickDown: (index: number) => void;

  /**
   * Hide the order number
   */
  hideOrderNumber?: boolean;
}

const CompositeOrder = memo(
  ({ order, className, dragClassName, onClickUp, onClickDown, hideOrderNumber }: CompositeOrderProps) => {
    return (
      <div
        className={cx('synri-composite-order-container', className, {
          'synri-composite-order-container--hidden-order': hideOrderNumber,
        })}>
        <div className="synri-composite-reorder-container">
          <Icon type="caret-up" onClick={() => onClickUp(order - 1)} />
          <Icon type="menu" className={cx('synri-composite-drag-handle', dragClassName)} />
          <Icon type="caret-down" onClick={() => onClickDown(order - 1)} />
        </div>
        {!hideOrderNumber && <div className={cx('synri-composite-order')}>{order}</div>}
      </div>
    );
  }
);

export default CompositeOrder;
