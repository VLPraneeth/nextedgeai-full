//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Button } from 'antd';
import ObjectID from 'bson-objectid';
import { cloneDeep, isObject } from 'lodash';
import { useEffect, useState } from 'react';
import { DragDropContext, Droppable } from 'react-beautiful-dnd';

import AppConstants from 'utils/AppConstants';
import { phoneHome } from 'utils/ErrorUtils';
import { tNamespaced } from 'utils/i18nUtil';

import CompositeGroup from './CompositeGroup';
import CompositeReadOnly from './CompositeReadOnly';

import './index.less';

const tn = tNamespaced('Composite');

const { READONLY } = AppConstants.INPUT_DISPLAY_MODE;

function Composite({ displayMode, ...rest }: any) {
  return displayMode === READONLY ? <CompositeReadOnly {...rest} /> : <CompositeEdit {...rest} />;
}

function CompositeEdit({
  configuration,
  layout,
  name,
  repeatable,
  onChange,
  defaultValue = {},
  fetchPicklistValues,
  picklistValues,
  hideOrderNumber,
  hideDelete,
  addText = tn('add_condition'),
  ...rest
}: any) {
  const [values, setValues] = useState(defaultValue?.compositeValues || [{ repeatId: ObjectID.generate() }]);

  useEffect(() => {
    if (defaultValue?.compositeValues) {
      setValues(defaultValue?.compositeValues);
    }
  }, [defaultValue]);

  const onAdd = () => {
    const compositeValues = [...values, { repeatId: ObjectID.generate() }];
    setValues(compositeValues);
    onChange({ name, compositeValues });
  };

  const onCompositeChange = (repeatId: string, subValue: any) => {
    let compositeValues;
    if (!values.length) {
      compositeValues = [
        {
          repeatId,
          [subValue.name]: subValue,
        },
      ];
    } else {
      compositeValues = cloneDeep(values);
      const fv = compositeValues.find((lv: any) => lv.repeatId === repeatId);
      if (fv) {
        if (isObject(fv)) {
          (fv as any)[subValue.name] = subValue;
        } else {
          // If we get into this block there's a problem. This phone home
          // doesn't crash the browser but will send details to the dev team.
          // See SYN-8253 for more details.
          phoneHome({
            error: new Error(
              JSON.stringify({
                message: 'Composite field value is not an object. ',
                fv,
                repeatId,
                subValue,
                compositeValues,
                name,
              })
            ),
          });
        }
      } else {
        compositeValues.push({
          repeatId,
          [subValue.name]: subValue,
        });
      }
    }
    setValues(compositeValues);
    onChange({ name, compositeValues });
  };

  const onDelete = (repeatId: string) => {
    const compositeValues = values.filter((value: any) => value.repeatId !== repeatId);
    setValues(compositeValues);
    onChange({ name, compositeValues });
  };

  /**
   * Handler when the user dropped an item
   * @param {Object} result - resulting drooped object
   *  result = {
   *    draggableId: '',
   *    type: '',
   *    reason: 'DROP',
   *    source: {
   *      droppableId: ''
   *      index: 0,
   *    },
   *    destination: {
   *      droppableId: '',
   *      index: 1,
   *    }
   *  }
   */
  const onDragEnd = ({ source, destination }: any) => {
    if (!destination) {
      return;
    }
    if (destination.droppableId === source.droppableId && destination.index === source.index) {
      return;
    }

    // Reorder the values
    const compositeValues = arrayMove(values, source.index, destination.index);
    setValues(compositeValues);
    onChange({ name, compositeValues });
  };

  function arrayMove(arr: any, oldIndex: any, newIndex: any) {
    const newArr = cloneDeep(arr);
    if (newIndex >= newArr.length) {
      var k = newIndex - newArr.length + 1;
      while (k--) {
        newArr.push(undefined);
      }
    }
    newArr.splice(newIndex, 0, newArr.splice(oldIndex, 1)[0]);
    return newArr;
  }

  const onMoveUp = (index: number) => {
    if (index <= 0) {
      return;
    }
    const compositeValues = arrayMove(values, index, index - 1);
    setValues(compositeValues);
    onChange({ name, compositeValues });
  };

  const onMoveDown = (index: number) => {
    const targetIndex = index + 1;
    if (targetIndex >= values.length) {
      return;
    }
    const compositeValues = arrayMove(values, index, targetIndex);
    setValues(compositeValues);
    onChange({ name, compositeValues });
  };

  return (
    <DragDropContext onDragEnd={onDragEnd}>
      <Droppable droppableId={`composite-droppable-${name}`}>
        {(provided) => {
          return (
            <div className="synri-composite" ref={provided.innerRef} {...provided.droppableProps}>
              {values?.map?.((value: any, index: number) => {
                return (
                  <CompositeGroup
                    key={value.repeatId}
                    value={value}
                    order={index + 1}
                    configuration={configuration}
                    onChange={onCompositeChange}
                    layout={layout}
                    onDelete={onDelete}
                    fetchPicklistValues={fetchPicklistValues}
                    picklistValues={picklistValues}
                    onClickUp={onMoveUp}
                    onClickDown={onMoveDown}
                    hideOrderNumber={hideOrderNumber}
                    hideDelete={hideDelete}
                  />
                );
              })}
              {provided.placeholder}
              {repeatable && (
                <Button type="link" onClick={onAdd}>
                  {addText}
                </Button>
              )}
            </div>
          );
        }}
      </Droppable>
    </DragDropContext>
  );
}

export default Composite;
