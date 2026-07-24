import { Tooltip } from 'antd';
import classNames from 'classnames';
import { DragEvent, ReactNode } from 'react';
import './DraggablePanelItem.less';

import { ReactComponent as DataCardIcon } from 'assets/icons/dashboard.svg';
import KebabMenu from 'components/KebabMenu';
import { useIsTextTruncated } from 'components/typography';

export interface DraggablePanelItemProps {
  disableDrag?: boolean;
  icon?: ReactNode;
  id: string;
  menuItems?: ReactNode[];
  subtitle?: ReactNode;
  title: string;
  tooltip?: ReactNode;
  draggedType: string;
}

export interface DragData {
  id: string;
  draggedType: 'dataset' | 'datacard';
}

export const DraggablePanelItem = ({
  disableDrag,
  icon,
  id,
  menuItems,
  subtitle,
  title,
  tooltip,
  draggedType,
}: DraggablePanelItemProps) => {
  // https://github.com/react-grid-layout/react-grid-layout/issues/1405
  const setDraggedCardId = (e: DragEvent) => {
    e.dataTransfer.setData('dragData', JSON.stringify({ id, draggedType }));
  };

  const [measuredElement, isTruncated] = useIsTextTruncated<HTMLDivElement>();

  const tooltipContents =
    // If tooltip node is provided, show it
    tooltip ??
    // If no tooltip node, show title as tooltip if the text is truncated
    (isTruncated ? title : '');

  return (
    <div
      className={classNames('draggable-panel-item', { 'draggable-panel-item--draggable': !disableDrag })}
      draggable={!disableDrag}
      id={id}
      onDragStart={setDraggedCardId}>
      <div className="draggable-panel-item__icon">{icon ?? <DataCardIcon />}</div>
      <div className="draggable-panel-item__main">
        <Tooltip title={tooltipContents} mouseEnterDelay={1}>
          <div className="draggable-panel-item__title" ref={measuredElement}>
            {title}
          </div>
          <div className="draggable-panel-item__subtitle">{subtitle}</div>
        </Tooltip>
      </div>
      {menuItems?.length ? (
        <div className="draggable-panel-item__menu">
          <KebabMenu menuItems={menuItems} />
        </div>
      ) : null}
    </div>
  );
};
