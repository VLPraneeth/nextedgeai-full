import { useCallback, useEffect, useState } from 'react';
import { Checkbox, Input, message } from 'antd';

import Button from 'components/Button';
import DrawerPanel from 'components/DrawerPanel';
import InputWithLabel from 'components/inputs/InputWithLabel';
import Select from 'components/inputs/Select';
import { Stack } from 'components/layout';

import {
  useAddAttributeMutation,
  useEditAttributesMutation,
  useGetAttributeQuery,
  useGetSupportedDataTypesQuery,
  useListResourceQuery,
  useListResourceTypeQuery,
} from 'store/access-control/abac/api';

import { tCommon, tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('Settings.AccessControl.ABAC.attributeForm');

export interface AddAttributeDrawerProps {
  visible: boolean;
  onClose: () => void;
  attributeId?: string;
}

interface AttributeFormData {
  name: string;
  dataType: string;
  multiValued: boolean;
  allowedValues: string;
  resourceType: string;
  resource: string;
}

const initialFormData: AttributeFormData = {
  name: '',
  dataType: 'text',
  multiValued: false,
  allowedValues: '',
  resourceType: '',
  resource: '',
};

const AddAttributeDrawer = ({ visible, onClose, attributeId }: AddAttributeDrawerProps) => {
  const [formData, setFormData] = useState<AttributeFormData>(initialFormData);
  const { data: resourceTypes = [] } = useListResourceTypeQuery();
  const { data: resources = [] } = useListResourceQuery(formData?.resourceType, { skip: !formData?.resourceType });
  const [addAttribute, { isLoading }] = useAddAttributeMutation();
  const { data: attributeData } = useGetAttributeQuery(attributeId || '', { skip: !attributeId });
  const { data: supportedDataTypes = [] } = useGetSupportedDataTypesQuery();

  useEffect(() => {
    if (attributeData) {
      setFormData({
        name: attributeData?.name,
        dataType: attributeData?.dataType,
        multiValued: attributeData?.multiValued,
        allowedValues: attributeData?.allowedValues?.join('\n') || '',
        resourceType: attributeData?.resourceTypeId,
        resource: attributeData?.resourceId,
      });
    }
  }, [attributeData, attributeId]);

  useEffect(() => {
    return () => {
      setFormData(initialFormData);
    };
  }, [visible]);

  const [editAttributes] = useEditAttributesMutation();

  const handleSave = useCallback(async () => {
    try {
      const payload = {
        name: formData?.name,
        dataType: formData?.dataType,
        multiValued: formData?.multiValued,
        resourceId: formData?.resource,
        resourceTypeId: formData?.resourceType,
        allowedValues:
          formData?.dataType === 'enumeration' ? formData?.allowedValues?.split('\n')?.filter(Boolean) : [],
      };

      if (attributeId) {
        await editAttributes({ id: attributeId, req: payload }).unwrap();
      } else {
        await addAttribute(payload).unwrap();
      }
      message.success(`Attribute ${attributeId ? 'updated' : 'saved'} successfully.`);
      onClose();
      setFormData(initialFormData);
    } catch (error: any) {
      console.error(`${tn('save_error')}:`, error);
      message.error(`${tn('save_error')}: ${error?.data?.error} | ${error?.data?.message}`, 7);
    }
  }, [formData, addAttribute, editAttributes, attributeId, onClose]);

  const handleInputChange = useCallback((field: keyof AttributeFormData, value: any) => {
    // Multi value support only available for Text and Enum. This logic is added to address scenarios
    // where the user attempts to update a Text/Enum value and change its datatype.
    const disableMultiSelect = field === 'dataType' && !['text', 'enumeration'].includes(value);

    setFormData((prev) => ({
      ...prev,
      [field]: value,
      ...(disableMultiSelect ? { multiValued: false } : {}),
    }));
  }, []);

  const isSaveDisabled: boolean = !formData?.name || !formData?.resourceType || !formData?.resource;

  return (
    <DrawerPanel
      className="add-attribute-drawer"
      title={attributeId ? tn('edit_attribute_title') : tn('add_attribute_title')}
      mask
      maskClosable
      maskStyle={{ backgroundColor: 'transparent' }}
      onClose={onClose}
      width="full"
      visible={visible}
      footer={
        <>
          <Button key="cancel" onClick={onClose}>
            {tCommon('cancel')}
          </Button>
          <Button key="save" type="primary" disabled={isSaveDisabled} loading={isLoading} onClick={handleSave}>
            {tCommon('save')}
          </Button>
        </>
      }>
      <Stack spacing="lg">
        <InputWithLabel
          label={tn('input_attribute_name_label')}
          input={
            <Input
              placeholder={tn('input_attribute_name_placeholder')}
              value={formData?.name}
              onChange={(e) => handleInputChange('name', e.target.value)}
            />
          }
        />

        <InputWithLabel
          label={tn('input_resource_type_label')}
          input={
            <Select
              value={formData?.resourceType}
              onChange={(value) => handleInputChange('resourceType', value)}
              optionData={resourceTypes?.map((type) => ({ label: type?.displayName, value: type?.name }))}
              placeholder={tn('input_resource_type_placeholder')}
              disabled={!!attributeId}
            />
          }
        />

        <InputWithLabel
          label={tn('input_resource_label')}
          input={
            <Select
              value={formData?.resource}
              onChange={(value) => handleInputChange('resource', value)}
              optionData={resources.map((res) => ({ label: res?.displayName, value: res?.id }))}
              placeholder={tn('input_resource_placeholder')}
              disabled={!!attributeId}
            />
          }
        />

        <InputWithLabel
          label={tn('input_datatype_label')}
          input={
            <Select
              value={formData?.dataType}
              onChange={(value) => handleInputChange('dataType', value)}
              optionData={supportedDataTypes}
            />
          }
        />

        {/* Multi value supported only for Text and Enum data types from backend. */}
        {['text', 'enumeration'].includes(formData.dataType) && (
          <InputWithLabel
            label={tn('input_multi_select_label')}
            input={
              <Checkbox
                checked={formData?.multiValued}
                onChange={(e) => handleInputChange('multiValued', e.target.checked)}
              />
            }
          />
        )}

        {formData?.dataType === 'enumeration' && (
          <InputWithLabel
            label={tn('input_allowed_values_label')}
            input={
              <Input.TextArea
                placeholder={tn('input_allowed_values_placeholder')}
                value={formData?.allowedValues}
                onChange={(e) => handleInputChange('allowedValues', e.target.value)}
                rows={4}
              />
            }
          />
        )}
      </Stack>
    </DrawerPanel>
  );
};

export default AddAttributeDrawer;
