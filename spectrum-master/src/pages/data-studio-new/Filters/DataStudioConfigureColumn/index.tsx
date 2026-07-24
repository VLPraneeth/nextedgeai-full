import { Checkbox, Tooltip } from 'antd';
import { CheckboxChangeEvent } from 'antd/lib/checkbox';
import cx from 'classnames';
import { useCallback, useMemo } from 'react';
import { Draggable, Droppable } from 'react-beautiful-dnd';

import { ReactComponent as DragIcon } from 'assets/icons/drag-icon.svg';
import { ReactComponent as MoveBottom } from 'assets/icons/move-bottom.svg';
import { ReactComponent as MoveTop } from 'assets/icons/move-top.svg';
import FieldTypeBadge from 'components/FieldTypeBadge';
import { HStack } from 'components/layout';
import { FieldDataType } from 'components/types';
import { tNamespaced } from 'utils/i18nUtil';
import { humanize } from 'utils/StringUtil';

export interface ColumnItem {
  columnName: string;
  isSelected: boolean;
}

const tn = tNamespaced('ConfigureTableColumns');

export interface ColumnListProps {
  /* unique id for the Droppable */
  id: string;
  allItems: ColumnItem[];
  handleSelectedItemChange: (columnName: string, checked: boolean, index: number) => void;
  filterString: string;
  emptyColumnContent?: string | React.ReactElement;
  moveTo: (currentIndex: number, destinationIndex: number) => void;
  labelForColumn: (columnName: string) => string;
  dataTypeForColumn: (columnName: string) => string;
  hideDisabled: boolean;
}

export const ColumnList = ({
  id,
  allItems,
  handleSelectedItemChange,
  filterString,
  moveTo,
  labelForColumn,
  dataTypeForColumn,
  hideDisabled,
}: ColumnListProps) => {
  const filteredItems = useMemo(() => {
    const initialFilteredItems = hideDisabled ? allItems.filter((item) => item.isSelected) : allItems;
    return initialFilteredItems.filter((item) =>
      item.columnName.toLowerCase().includes(filterString.toLocaleLowerCase())
    );
  }, [hideDisabled, allItems, filterString]);

  const isDragDisabled = useMemo(() => hideDisabled || !!filterString.length, [hideDisabled, filterString]);

  //   const moveOneUp = useCallback(
  //     (index: number) => {
  //       if (index <= 0 || isDragDisabled) {
  //         return;
  //       }
  //       moveTo(index, index - 1);
  //     },
  //     [moveTo, isDragDisabled]
  //   );

  //   const moveOneDown = useCallback(
  //     (index: number) => {
  //       if (index >= allItems.length - 1 || isDragDisabled) {
  //         return;
  //       }
  //       moveTo(index, index + 1);
  //     },
  //     [moveTo, isDragDisabled, allItems]
  //   );

  return (
    <div className="column-list">
      <Droppable droppableId={id}>
        {(droppable, droppableSnapshot) => (
          <>
            <ul
              key={id}
              className={cx('column-list-droppable', {
                'drag-over': droppableSnapshot.isDraggingOver,
              })}
              ref={droppable.innerRef}
              {...droppable.droppableProps}>
              {filteredItems.length > 0 &&
                filteredItems.map((column, index) => {
                  const actualIndex = allItems.findIndex((item) => item.columnName === column.columnName);
                  return (
                    <Draggable
                      key={column.columnName as string}
                      draggableId={(column.columnName as unknown) as string}
                      index={index}
                      isDragDisabled={isDragDisabled}>
                      {(draggable, draggableSnapshot) => {
                        const dataType = dataTypeForColumn(column.columnName) as FieldDataType;
                        return (
                          <li
                            className={cx('column-list-draggable', {
                              dragging: draggableSnapshot.isDragging,
                              'is-hidden': !draggableSnapshot.isDragging,
                              'drag-disabled': isDragDisabled,
                            })}
                            ref={draggable.innerRef}
                            {...draggable.draggableProps}
                            {...draggable.dragHandleProps}>
                            <HStack justify="space-between">
                              <HStack>
                                <div className={cx('drag-handle', isDragDisabled && 'disabled')}>
                                  <DragIcon />
                                </div>
                                <Checkbox
                                  checked={column.isSelected}
                                  onChange={(event: CheckboxChangeEvent) => {
                                    handleSelectedItemChange(column.columnName, event.target.checked, index);
                                  }}
                                />
                                <FieldTypeBadge dataType={dataType} description={humanize(dataType)} size="small" />
                                <div className="column-label">
                                  {labelForColumn(column.columnName)}{' '}
                                  <span className="column-api-name">[{column.columnName}]</span>
                                </div>
                              </HStack>

                              <HStack className="actions">
                                <Tooltip title={tn('send_to_top')}>
                                  <MoveTop className="send_to_top_icon" onClick={() => moveTo(actualIndex, 0)} />
                                </Tooltip>
                                <Tooltip title={tn('send_to_bottom')}>
                                  <MoveBottom
                                    className="send_to_bottom_icon"
                                    onClick={() => moveTo(actualIndex, allItems.length - 1)}
                                  />
                                </Tooltip>
                              </HStack>
                            </HStack>
                          </li>
                        );
                      }}
                    </Draggable>
                  );
                })}
            </ul>
          </>
        )}
      </Droppable>
    </div>
  );
};
