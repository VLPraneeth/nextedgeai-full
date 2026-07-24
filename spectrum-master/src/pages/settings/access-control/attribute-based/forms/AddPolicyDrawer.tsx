import { useCallback, useEffect, useState } from 'react';
import { Input, message } from 'antd';

import Button from 'components/Button';
import DrawerPanel from 'components/DrawerPanel';
import InputWithLabel from 'components/inputs/InputWithLabel';
import Select from 'components/inputs/Select';
import { Stack } from 'components/layout';
import InlineMessage from 'components/InlineMessage';

import {
  useAddPolicyMutation,
  useEditPolicyMutation,
  useGetAttributesOfResourceQuery,
  // useGetPolicyQuery,
  useListResourceQuery,
  useListResourceTypeQuery,
  useListPoliciesQuery,
} from 'store/access-control/abac/api';
import { fetchPicklistValues, FetchPicklistValuesParams } from 'store/picklists/thunks';
import { useEnhancedDispatch, useEnhancedSelector as useSelector } from 'hooks/redux';

import AppConstants from 'utils/AppConstants';
import { tCommon, tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('Settings.AccessControl.ABAC.policyForm');

export interface AddPolicyDrawerProps {
  visible: boolean;
  onClose: () => void;
  policyId?: string;
}

interface PolicyFormData {
  name: string;
  resourceType: string;
  resource: string;
  condition: any;
  permissions: string[];
}

const initialFormData: PolicyFormData = {
  name: '',
  resource: '',
  resourceType: '',
  condition: undefined,
  permissions: [],
};

const AddPolicyDrawer = ({ visible, onClose, policyId }: AddPolicyDrawerProps) => {
  const [formData, setFormData] = useState<PolicyFormData>(initialFormData);
  const { data: resourceTypes = [] } = useListResourceTypeQuery();
  const { data: resources = [] } = useListResourceQuery(formData.resourceType, { skip: !formData.resourceType });
  const [addPolicy, { isLoading: isAddLoading }] = useAddPolicyMutation();
  const [editPolicy, { isLoading: isEditLoading }] = useEditPolicyMutation();
  // const { data: policyData } = useGetPolicyQuery(policyId || '', { skip: !policyId });
  const { data: policyList } = useListPoliciesQuery();
  const policyData = policyList?.find((policy) => policy?.id === policyId);
  const { data: selectedResourceAttributes } = useGetAttributesOfResourceQuery(
    {
      id: formData.resource,
      type: formData.resourceType,
    },
    { skip: !formData?.resourceType || formData?.resource?.length === 0 }
  );
  const { data: userResourceAttributes } = useGetAttributesOfResourceQuery({
    id: 'user',
    type: 'USER',
  });
  const picklistValues = useSelector((state) => state.picklist.picklistValues);
  const dispatch = useEnhancedDispatch();

  useEffect(() => {
    if (policyData) {
      setFormData({
        name: policyData?.name,
        resourceType: policyData?.resourceTypeId,
        resource: policyData?.resourceId,
        condition: reverseTransformCondition(policyData?.condition, userResourceAttributes),
        permissions: policyData?.permissions || [],
      });
    }
  }, [policyData, policyId]);

  useEffect(() => {
    return () => {
      setFormData(initialFormData);
    };
  }, [visible]);

  const handleSave = useCallback(async () => {
    try {
      const payload = {
        name: formData?.name,
        resourceId: formData?.resource,
        resourceTypeId: formData?.resourceType,
        condition: transformCondition(formData?.condition, userResourceAttributes),
        permissions: formData?.permissions,
      };

      if (policyId) {
        await editPolicy({ id: policyId, req: payload }).unwrap();
      } else {
        await addPolicy(payload).unwrap();
      }
      message.success(`Policy ${policyId ? 'updated' : 'saved'} successfully.`);
      onClose();
      setFormData(initialFormData);
    } catch (error: any) {
      console.error(`${tn('save_error')}:`, error);
      message.error(`${tn('save_error')}: ${error?.data?.error} | ${error?.data?.message}`, 7);
    }
  }, [formData, addPolicy, editPolicy, policyId, onClose]);

  const handleResourceTypeChange = useCallback((value: any) => {
    setFormData((prev) => ({
      ...initialFormData,
      name: prev?.name,
      resourceType: value,
    }));
  }, []);

  const handleInputChange = useCallback((field: keyof PolicyFormData, value: any) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
  }, []);

  const isResourceAndTypeUnselected: boolean = !formData?.resourceType || !formData?.resource;

  const isSaveDisabled: boolean = !formData?.name || isResourceAndTypeUnselected;

  //Adding datatype picklist to force RHS to have showTokens = false
  const newList: any = {};
  Object.keys(picklistValues)?.forEach((key) => {
    newList[key] = picklistValues?.[key]?.map((item) => ({
      ...item,
      datatype: 'multiselect',
    }));
  });

  const _renderConditionAndPermission = () => (
    <>
      <InputWithLabel
        label={tn('input_condition_label')}
        name="condition"
        id="condition"
        datatype={AppConstants.INPUT_TYPE.PREDICATE}
        operatorType="abacOperator"
        picklistValues={newList}
        defaultValue={formData.condition}
        values={
          selectedResourceAttributes?.map((attr) => ({
            label: `${attr?.resourceName}.${attr?.name}`,
            value: attr?.id,
            datatype: attr?.dataType,
            //Adding renderType to force RHS to a dropdown
            renderType: 'multivaluetext',
          })) || []
        }
        fetchPicklistValues={(param: FetchPicklistValuesParams) => dispatch(fetchPicklistValues(param))}
        onChange={(name: string, id: string, value: any) => {
          handleInputChange('condition', value);
        }}
        rightValues={
          userResourceAttributes?.map((attr) => ({
            label: `${attr?.resourceName}.${attr?.name}`,
            value: attr?.id,
            dataType: attr?.dataType,
          })) || []
        }
      />

      <InputWithLabel
        label={tn('input_permissions_label')}
        input={
          <Select
            mode="multiple"
            placeholder={tn('input_permissions_placeholder')}
            value={formData?.permissions}
            onChange={(value) => handleInputChange('permissions', value)}
            optionData={
              resourceTypes
                .find((type) => type.name === formData?.resourceType)
                ?.permissions.map((permission) => ({
                  label: permission,
                  value: permission,
                })) || []
            }
          />
        }
      />
    </>
  );

  return (
    <DrawerPanel
      className="add-policy-drawer"
      title={policyId ? tn('edit_policy_title') : tn('add_policy_title')}
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
          <Button
            key="save"
            type="primary"
            loading={isAddLoading || isEditLoading}
            onClick={handleSave}
            disabled={isSaveDisabled}>
            {tCommon('save')}
          </Button>
        </>
      }>
      <Stack spacing="lg">
        <InputWithLabel
          label={tn('input_policy_name_label')}
          input={
            <Input
              placeholder={tn('input_policy_name_placeholder')}
              value={formData?.name}
              onChange={(e) => handleInputChange('name', e.target.value)}
            />
          }
        />

        <InputWithLabel
          label={tn('input_resource_type_label')}
          input={
            <Select
              value={formData.resourceType}
              onChange={(value) => handleResourceTypeChange(value)}
              optionData={resourceTypes?.reduce((acc: any[], type) => {
                if (type.name !== 'USER') {
                  acc.push({ label: type?.displayName, value: type?.name });
                }
                return acc;
              }, [])}
              placeholder={tn('input_resource_type_placeholder')}
              disabled={!!policyId}
            />
          }
        />

        <InputWithLabel
          label={tn('input_resource_label')}
          input={
            <Select
              value={formData?.resource}
              onChange={(value) => handleInputChange('resource', value)}
              optionData={resources?.map((res) => ({ label: res?.displayName, value: res?.id }))}
              placeholder={tn('input_resource_placeholder')}
              disabled={!!policyId}
            />
          }
        />
        {!isResourceAndTypeUnselected &&
          (selectedResourceAttributes && selectedResourceAttributes?.length > 0 ? (
            _renderConditionAndPermission()
          ) : (
            <div>
              <InlineMessage type="info">{tn('info_message')}</InlineMessage>
            </div>
          ))}
      </Stack>
    </DrawerPanel>
  );
};

export default AddPolicyDrawer;

function transformCondition(condition: any, userResourceAttributes: Array<{ id: string }> = []) {
  const processPredicate = (predicate: any) => {
    if (predicate.predicates) {
      return {
        ...predicate,
        predicates: predicate.predicates.map(processPredicate),
      };
    }

    const newRight = {
      ...predicate.right,
      value: predicate.right.value[0],
      type: userResourceAttributes.some((attr) => attr.id === predicate.right.value[0]) ? 'variable' : 'literal',
    };

    return {
      ...predicate,
      left: {
        ...predicate.left,
        type: 'variable',
      },
      right: newRight,
    };
  };

  if (!condition) return null;
  return processPredicate(condition);
}

function reverseTransformCondition(condition: any, userResourceAttributes: Array<{ id: string }> = []) {
  const processPredicate = (predicate: any) => {
    if (predicate.predicates) {
      return {
        ...predicate,
        predicates: predicate.predicates.map(processPredicate),
      };
    }

    const newRight = {
      ...predicate.right,
      value: [predicate.right.value],
      type: userResourceAttributes.some((attr) => attr.id === predicate.right.value) ? 'variable' : 'literal',
    };

    return {
      ...predicate,
      right: newRight,
    };
  };

  if (!condition) return null;
  return processPredicate(condition);
}
