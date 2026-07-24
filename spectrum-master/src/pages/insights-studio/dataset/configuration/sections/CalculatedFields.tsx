import { Tag } from 'antd';
import { useCallback, useState } from 'react';

import InfoBox from 'components/InfoBox';
import { Stack } from 'components/layout';
import { useUnifiedDataCardAuthoringContext } from 'pages/insights-studio/context/UnifiedDataCardAuthoringContext';
import { useDatasetGroup } from 'pages/insights-studio/utils/useDatasetGroup';

import { CalculatedField } from './CalculatedFields.types';
import { CalculatedFieldsPicker } from './CalculatedFieldsPicker';

import './CalculatedFields.less';

const CalculatedFields = () => {
  const { calculatedFields, setCalculatedFields, setPopupIsOpen, ...context } = useUnifiedDataCardAuthoringContext();
  const [visible, setVisibleState] = useState(false);
  const setVisible = useCallback(
    (vis: boolean) => {
      setVisibleState(vis);
      setPopupIsOpen(vis);
    },
    [setPopupIsOpen]
  );
  const [editField, setEditField] = useState<undefined | CalculatedField>();
  const { syncGroups } = useDatasetGroup();

  const closePicker = () => {
    setVisible(false);
    setEditField(undefined);
  };

  const onCalcFieldChange = useCallback(
    (newField: CalculatedField) => {
      const newCalculatedFields = editField
        ? calculatedFields.map((fd) => (fd.apiName === editField.apiName ? newField : fd))
        : [...calculatedFields, newField];

      setCalculatedFields(newCalculatedFields);
      syncGroups(newCalculatedFields);
    },
    [calculatedFields, editField, setCalculatedFields, syncGroups]
  );

  const deleteField = useCallback(
    (calculatedField: CalculatedField) => {
      // TODO: Remove the field from the group
      const newCalculatedFields = calculatedFields.filter((fld) => fld.apiName !== calculatedField.apiName);
      setCalculatedFields(newCalculatedFields);
      syncGroups(newCalculatedFields);
    },
    [calculatedFields, setCalculatedFields, syncGroups]
  );

  if (context.selectedDataSources.length < 1) {
    return <InfoBox message="You need to select a data source to add calculated field." type="info" showIcon />;
  }

  return (
    <Stack spacing="xs">
      {calculatedFields.length > 0 && (
        // When the user click the containing box, open the popover to create a new field
        <div className="calculated-fields-picker--variable-view" onClick={() => setVisible(!visible)}>
          {calculatedFields.map((calculatedField) => {
            return (
              <Tag
                key={calculatedField.apiName}
                closable
                onClose={(evt: React.MouseEvent<HTMLElement>) => {
                  evt.stopPropagation();
                  deleteField(calculatedField);
                  closePicker();
                }}
                onClick={(evt: React.MouseEvent<HTMLElement>) => {
                  evt.stopPropagation();
                  setEditField(calculatedField);
                  setVisible(true);
                }}>
                {calculatedField.aliasName}
              </Tag>
            );
          })}
        </div>
      )}

      {visible && (
        <CalculatedFieldsPicker
          key={`${editField?.apiName}${editField?.aliasName}`}
          onClose={closePicker}
          editField={editField}
          onSave={onCalcFieldChange}
        />
      )}

      <button type="button" className="ant-btn ant-btn-link" onClick={() => setVisible(true)}>
        <span className="syncari-text line-height-regular font-size-md font-weight-regular">
          + Add a new calculated field
        </span>
      </button>
    </Stack>
  );
};

export default CalculatedFields;
