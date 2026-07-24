import { useCallback, useEffect, useState } from 'react';
import { message, Input } from 'antd';

import Button from 'components/Button';
import DrawerPanel from 'components/DrawerPanel';
import InputWithLabel from 'components/inputs/InputWithLabel';
import Select from 'components/inputs/Select';
import { Stack } from 'components/layout';
// import TokenizableFieldGroup from 'components/inputs/TokenizableFieldGroup';
// import InputContainer from 'components/inputs/InputContainer';
import InlineMessage from 'components/InlineMessage';

import {
  useAddAttributeValueMutation,
  useGetAttributesOfResourceQuery,
  useGetAttributeValueQuery,
  useListResourceForValuesQuery,
  useListResourceTypeQuery,
} from 'store/access-control/abac/api';

import AppConstants from 'utils/AppConstants';
import { tCommon, tNamespaced } from 'utils/i18nUtil';
import { VisiblityTypes } from '../tables/ValuesTable';

const tn = tNamespaced('Settings.AccessControl.ABAC.valuesForm');

export interface EditValuesDrawerProps {
  visible: VisiblityTypes;
  onClose: () => void;
  selectedValues?: Array<any>;
}

interface EditValuesFormData {
  resourceType: string;
  resource: any;
  [key: string]: any;
}

const initialFormData: EditValuesFormData = {
  resourceType: '',
  resource: [],
};

const EditValuesDrawer = ({ visible, onClose, selectedValues = [] }: EditValuesDrawerProps) => {
  const [formData, setFormData] = useState<EditValuesFormData>(initialFormData);
  const [resourceMultiSelect, setResourceMultiSelect] = useState(true);

  const { data: resourceTypes = [] } = useListResourceTypeQuery();
  const { data: resources = [] } = useListResourceForValuesQuery(formData.resourceType, {
    skip: !formData.resourceType,
  });
  const { data: attributeData = [] } = useGetAttributesOfResourceQuery(
    {
      type: formData.resourceType,
      id: formData.resource?.[0],
    },
    { skip: !formData.resourceType || formData.resource?.length === 0 }
  );
  // const { data: valueData } = useGetAttributeValueQuery(selectedValues?.[0]?.id, { skip: selectedValues?.length !== 1 });

  const [addAttributeValue, { isLoading }] = useAddAttributeValueMutation();

  useEffect(() => {
    if (selectedValues?.length > 0) {
      const res: EditValuesFormData = {
        resourceType: selectedValues[0]?.resourceTypeId,
        resource: [selectedValues[0]?.resourceId],
      };

      selectedValues.forEach((val) => {
        res[val.attributeId] = val.value;
      });

      setFormData(res);
    } else {
      setFormData(initialFormData);
    }
  }, [selectedValues]);

  const onPanelClose = useCallback(() => {
    if (visible === 'add') {
      setFormData(initialFormData);
    }
    onClose?.();
  }, [onClose]);

  const handleSave = useCallback(async () => {
    try {
      const payload = Object.entries(formData)
        .filter(([key]) => key !== 'name' && key !== 'resourceType' && key !== 'resource')
        .reduce((acc, [attributeId, value]) => {
          const attrs: [] = formData?.resource?.map((res: any) => {
            return {
              attributeId,
              value,
              resourceTypeId: formData?.resourceType,
              resourceId: res,
              id: selectedValues?.find((item: any) => item?.attributeId === attributeId)?.id || undefined,
            };
          });
          acc.push(...attrs);
          return acc;
        }, []);
      await addAttributeValue(payload as any).unwrap();
      message.success(`Value(s) ${selectedValues?.length ? 'updated' : 'saved'} successfully.`);
      onPanelClose();
    } catch (error: any) {
      console.error(`${tn('save_error')}:`, error);
      message.error(`${tn('save_error')}: ${error?.data?.error} | ${error?.data?.message}`, 7);
    }
  }, [formData, addAttributeValue, onClose]);

  const handleResourceTypeChange = useCallback((value: any) => {
    const selectedType: any = resourceTypes?.find((item) => item?.name === value) || {};
    setResourceMultiSelect(selectedType?.multiSelectSupport);
    setFormData({
      ...initialFormData,
      resourceType: value,
    });
  }, []);

  const handleInputChange = useCallback((field: string, value: any, isToken = false) => {
    setFormData((prev) => ({
      ...prev,
      [field]: isToken && prev[field] ? prev[field] + value : value,
    }));
  }, []);

  const _renderAttributeFields = () => {
    return attributeData
      ?.filter((attr) => selectedValues?.length === 0 || selectedValues.some((item) => item?.attributeId === attr.id))
      .map(({ id, dataType, name, allowedValues, multiValued }, index) => {
        // TODO: To be added back in next version
        const datatype =
          dataType === AppConstants.INPUT_TYPE.BOOLEAN
            ? 'text' //AppConstants.INPUT_TYPE.CHECKBOX
            : dataType === 'enumeration' || multiValued
            ? AppConstants.INPUT_TYPE.PICKLIST
            : dataType.toLowerCase();

        return (
          <InputWithLabel
            label={name}
            placeholder="Enter attribute value"
            value={formData[id] || undefined}
            checked={formData[id] || undefined}
            onChange={(value: any) => {
              let res = value;
              if (value?.target) {
                // if (datatype === 'checkbox') {
                //   res = value?.target?.checked;
                // } else {
                res = value?.target?.value;
                // }
              }
              handleInputChange(id, res, false);
            }}
            datatype={datatype}
            mode={multiValued ? 'tags' : 'single'}
            optionData={
              allowedValues?.map((val) => ({
                label: val,
                value: val,
              })) || []
            }
          />
        );

        // TODO: To be added back in next version
        // <TokenizableFieldGroup
        //   key={`input-attribute-value-toekn-${index}`}
        //   hideTokenPicker={false}
        //   disableTokens={true}
        //   fallbackOnTokenSelect={(token) => {
        //     handleInputChange(id, token?.token, true);
        //   }}
        //   id={`attribute-token-${index}`}
        //   name={name}
        //   required={false}
        //   tooltip=""
        //   label={name}>
        //   <InputContainer
        //     datatype={datatype}
        //     className="attribute__inputs__value"
        //     value={formData[id] || undefined}
        //     attributeResourceInfo={{
        //       id: formData?.res,
        //       type: formData.resourceType,
        //     }}
        //     renderType={AppConstants.INPUT_RENDER_TYPE.TOKENS}
        //     showTokenSelector={false}
        //     onChange={(evt: React.ChangeEvent<HTMLInputElement> | string) => {
        //       const val = typeof evt === 'string' ? evt : evt.target.value;
        //       handleInputChange(id, val, false);
        //     }}
        //   />
        // </TokenizableFieldGroup>
      });
  };

  const isResourceAndTypeUnselected: boolean = !formData.resourceType || formData.resource?.length === 0;

  const isSaveDisabled = () => {
    if (!isResourceAndTypeUnselected) {
      return !attributeData.some((attr) => formData[attr?.id]);
    }
    return true;
  };

  return (
    <DrawerPanel
      className="edit-values-drawer"
      title={selectedValues?.length > 0 ? tn('edit_value_title') : tn('add_value_title')}
      mask
      maskClosable
      maskStyle={{ backgroundColor: 'transparent' }}
      onClose={onPanelClose}
      width="full"
      visible={visible === 'add' || visible === 'edit'}
      footer={
        <>
          <Button key="cancel" onClick={onPanelClose}>
            {tCommon('cancel')}
          </Button>
          <Button key="save" type="primary" loading={isLoading} onClick={handleSave} disabled={isSaveDisabled()}>
            {tCommon('save')}
          </Button>
        </>
      }>
      <Stack spacing="lg">
        <InputWithLabel
          label={tn('input_resource_type_label')}
          input={
            <Select
              value={formData.resourceType}
              onChange={(value) => handleResourceTypeChange(value)}
              optionData={resourceTypes.map((type) => ({
                label: type.displayName,
                value: type.name,
              }))}
              placeholder={tn('input_resource_type_placeholder')}
              disabled={selectedValues?.length > 0}
            />
          }
        />

        <InputWithLabel
          label={tn('input_resource_label')}
          input={
            <Select
              value={formData.resource}
              mode={resourceMultiSelect ? 'multiple' : undefined}
              onChange={(value) => {
                const val = !resourceMultiSelect ? [value] : value;
                handleInputChange('resource', val);
              }}
              optionData={
                formData?.resourceType ? resources?.map((res) => ({ label: res?.displayName, value: res?.id })) : []
              }
              placeholder={tn('input_resource_placeholder')}
              disabled={selectedValues?.length > 0}
            />
          }
        />
        {!isResourceAndTypeUnselected &&
          (attributeData?.length > 0 ? (
            _renderAttributeFields()
          ) : (
            <div>
              <InlineMessage type="info">{tn('info_message')}</InlineMessage>
            </div>
          ))}
      </Stack>
    </DrawerPanel>
  );
};

export default EditValuesDrawer;
