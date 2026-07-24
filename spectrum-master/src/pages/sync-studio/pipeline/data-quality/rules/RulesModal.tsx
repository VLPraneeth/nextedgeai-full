//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { useCallback, useEffect, useState } from 'react';

import FieldTypeBadge from 'components/FieldTypeBadge';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { FilterValue } from 'components/inputs/types';
import { Stack, HStack } from 'components/layout';
import Modal from 'components/Modal';
import { FieldDataType } from 'components/types';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import {
  useGetCategoriesListQuery,
  useGetRulesListQuery,
  useGetRulesMetadataQuery,
  useSaveRuleMutation,
  useSaveCategoriesMutation,
  useGetReferenceDataSetsQuery,
} from 'store/data-quality-v2/api';
import { DfiV2Rule } from 'store/data-quality-v2/types';
import { fetchPicklistValues, FetchPicklistValuesParams } from 'store/picklists/thunks';
import { setSelectedGraphNode } from 'actions/entityPipelineActions';
import { setCurrentGraph } from 'store/pipeline/actions';
import Button from 'components/button-component/Button';
import Input from 'components/inputs/Input';
import { Option } from 'components/inputs/Select';
import { MenuItem } from 'components/KebabMenu';
import { Dropdown, Menu } from 'antd';
import { OptionProps } from 'antd/lib/select';
import { message } from 'antd';

import AppConstants from 'utils/AppConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';

import { useDataQuality } from '../DataQuality.hooks';
import { RootState } from 'reducers';
import { NodeTypeKeys } from 'utils/AppConstants.types';
import { UNSELECTABLE_NODES } from 'pages/sync-studio/pipeline/PipelineEditor.constants';

import './RulesModal.scss';

const { INPUT_TYPE } = AppConstants;

const tn = tNamespaced('DataQuality');

const RulesModal = () => {
  const [saveRule] = useSaveRuleMutation();
  const [saveCategories] = useSaveCategoriesMutation();
  const { rulesMatch, navigateToDataQuality, graphVersion, ruleIdMatch } = useDataQuality();
  const dispatch = useEnhancedDispatch();
  const picklistValues = useEnhancedSelector((state: RootState) => state.picklist.picklistValues);
  const pipeline = useEnhancedSelector((state: RootState) => state.entityPipeline.entityPipeline);
  const [isScopeSelectOpen, setIsScopeSelectOpen] = useState(false);
  const currentGraphNode = useEnhancedSelector((state: RootState) => state.entityPipeline.selectedGraphNode);
  const currentGraph = useEnhancedSelector((state: RootState) => state.entityPipeline.currentGraph);

  const handleClose = useCallback(() => navigateToDataQuality(), [navigateToDataQuality]);

  useEffect(() => {
    if (pipeline && !currentGraphNode) {
      const nodeWithTempVars = pipeline.nodes?.find((node: { nodeType: NodeTypeKeys; configuration?: any }) => {
        if (!node.configuration) return false;
        const hasTempVars = JSON.stringify(node.configuration).includes('syncari.temp.');
        return hasTempVars && !UNSELECTABLE_NODES.includes(node.nodeType as typeof UNSELECTABLE_NODES[number]);
      });

      const validNode =
        nodeWithTempVars ||
        pipeline.nodes?.find(
          (node: { nodeType: NodeTypeKeys }) =>
            !UNSELECTABLE_NODES.includes(node.nodeType as typeof UNSELECTABLE_NODES[number])
        );

      if (validNode) {
        dispatch(setSelectedGraphNode(validNode));
      }
    }
    if (pipeline && !currentGraph) {
      dispatch(setCurrentGraph(pipeline));
    }
  }, [currentGraphNode, pipeline, currentGraph, dispatch]);

  const [rule, setRule] = useState<DfiV2Rule>({});

  const syncariEntityId = rulesMatch?.entityId || '';

  const { data: rules } = useGetRulesListQuery(
    { syncariEntityId, version: graphVersion },
    { skip: !Boolean(syncariEntityId) || !Boolean(graphVersion) }
  );
  const { data: metadata } = useGetRulesMetadataQuery(
    { syncariEntityId },
    {
      skip: !Boolean(rulesMatch?.entityId),
    }
  );

  useEffect(() => {
    if (!rulesMatch) {
      setRule({});
    }
  }, [rulesMatch]);

  useEffect(() => {
    if (ruleIdMatch?.ruleId) {
      const editRule = rules?.find((rule) => rule.id === ruleIdMatch?.ruleId) || {};
      setRule(editRule);
    }
  }, [ruleIdMatch?.ruleId, rules]);

  useEffect(() => {
    if (metadata?.policies && !rule.policy) {
      setRule((prevRule) => ({
        ...prevRule,
        policy: 'report',
      }));
    }
  }, [metadata?.policies, rule.policy]);

  const { data: categories } = useGetCategoriesListQuery();
  const { data: referenceDataSets } = useGetReferenceDataSetsQuery();

  const [newCategoryName, setNewCategoryName] = useState('');
  const [isEditingCategory, setIsEditingCategory] = useState(false);
  const [editingCategoryId, setEditingCategoryId] = useState<string | null>(null);

  const [dropdownVisible, setDropdownVisible] = useState(false);

  useEffect(() => {
    if (!rulesMatch?.entityId) {
      setDropdownVisible(false);
    }
  }, [rulesMatch?.entityId]);

  const isFormValid = useCallback(() => {
    if (!rule.name) return false;
    if (metadata?.policies && !rule.policy) return false;
    if (metadata?.scopes && (!rule.scope || rule.scope.length === 0)) return false;
    if (categories?.length && !rule.category) return false;
    if (!rule.ruleConfig) return false;
    return true;
  }, [rule, metadata, categories]);

  const handleSave = useCallback(() => {
    saveRule({
      rule,
      syncariEntityId,
      version: graphVersion,
    })
      .unwrap()
      .then(() => {
        handleClose();
      })
      .catch((error) => {
        const errorMessage = error?.data?.message || 'An error occurred while saving the rule';
        message.error(errorMessage);
      });
  }, [graphVersion, handleClose, rule, saveRule, syncariEntityId]);

  const filterOption = (input: string, option: React.ReactElement<OptionProps>) => {
    if (!option?.key) return false;
    const keyValue = option.key.toString();
    if (!keyValue) return false;
    return keyValue.toLowerCase().indexOf(input.toLowerCase()) >= 0;
  };

  const startAddCategory = useCallback(() => {
    if (newCategoryName.trim() && newCategoryName.toLowerCase() !== 'other') {
      const newCategory = {
        id: isEditingCategory ? editingCategoryId : null,
        name: newCategoryName,
        type: 'custom' as const,
      };
      saveCategories({ categories: [newCategory] });
      setNewCategoryName('');
      setIsEditingCategory(false);
      setEditingCategoryId(null);
    }
  }, [newCategoryName, saveCategories, isEditingCategory, editingCategoryId]);

  const addCategoryMenu = (
    <Menu>
      <MenuItem key="add-category" style={{ padding: '8px' }}>
        <Stack spacing="sm">
          <Input
            placeholder={tn('new_category_name')}
            value={newCategoryName}
            onChange={(e) => setNewCategoryName(e.target.value)}
            style={{ width: '200px' }}
            onClick={(e) => e.stopPropagation()}
          />
          <HStack justify="end">
            <Button
              onClick={(e) => {
                e.stopPropagation();
                startAddCategory();
                setDropdownVisible(false);
              }}
              disabled={!newCategoryName.trim() || newCategoryName.toLowerCase() === 'other'}>
              {tc('save')}
            </Button>
          </HStack>
        </Stack>
      </MenuItem>
    </Menu>
  );

  return (
    <Modal
      title={tn('create_rule')}
      centered
      className="rule-modal"
      visible={!!rulesMatch?.entityId}
      width="800px"
      onOk={handleClose}
      onCancel={handleClose}
      footer={
        <>
          <Button onClick={handleClose}>{tc('cancel')}</Button>
          <Button type="primary" onClick={handleSave} disabled={!isFormValid()}>
            {tc('save')}
          </Button>
        </>
      }
      destroyOnClose>
      <Stack className="rule-modal__container" spacing="xxs">
        <InputWithLabel
          name="name"
          label={tc('name')}
          value={rule.name}
          datatype={INPUT_TYPE.STRING}
          onChange={(evt: React.ChangeEvent<HTMLInputElement>) => {
            setRule({
              ...rule,
              name: evt.target.value,
            });
          }}
        />
        {metadata?.policies && (
          <InputWithLabel
            label={tn('policy')}
            name="policy"
            value={rule.policy}
            datatype={INPUT_TYPE.PICKLIST}
            onChange={(policy: string) => {
              setRule({
                ...rule,
                policy,
              });
            }}
            options={metadata.policies.map(({ label, value }) => (
              <Option value={value} key={value}>
                <span>{label}</span>
              </Option>
            ))}
            filterOption={filterOption}
          />
        )}
        {metadata?.scopes && (
          <InputWithLabel
            label={tn('scope')}
            name="scope"
            value={rule.scope}
            datatype={INPUT_TYPE.MULTISELECT}
            onDropdownVisibleChange={(open: boolean) => setIsScopeSelectOpen(open)}
            open={isScopeSelectOpen}
            onChange={(scope: string[]) => {
              const lastSelectedScope = scope[scope.length - 1];
              const isSystemScope = lastSelectedScope === 'record' || lastSelectedScope === 'all_fields';

              if (isSystemScope) {
                setRule({
                  ...rule,
                  scope: [lastSelectedScope],
                  scopeType: 'system',
                  ruleConfig: lastSelectedScope === 'record' ? undefined : rule.ruleConfig,
                });
                setIsScopeSelectOpen(false);
                return;
              }

              const attributeScopes = scope.filter((scp) => scp !== 'record' && scp !== 'all_fields');
              setRule({
                ...rule,
                scope: attributeScopes,
                scopeType: 'attribute',
              });
            }}
            options={metadata.scopes.map(({ label, value, datatype }) => (
              <Option value={value} key={value + label}>
                <div className="rule-modal__datatype-option">
                  {datatype && (
                    <FieldTypeBadge dataType={datatype as FieldDataType} description={datatype} disableTooltip />
                  )}
                  <span>{label}</span>
                </div>
              </Option>
            ))}
            filterOption={filterOption}
          />
        )}
        {categories?.length && (
          <Stack spacing="sm">
            <HStack justify="end" spacing="sm">
              <Dropdown
                overlay={addCategoryMenu}
                trigger={['click']}
                visible={dropdownVisible}
                onVisibleChange={setDropdownVisible}>
                <Button type="link">{tn('add_category')}</Button>
              </Dropdown>
            </HStack>
            <InputWithLabel
              label={tn('category')}
              name="category"
              value={rule.category}
              datatype={INPUT_TYPE.PICKLIST}
              onChange={(category: string) => {
                setRule({
                  ...rule,
                  category,
                });
              }}
              options={categories.map(({ name, id }) => (
                <Option value={id} key={name}>
                  <span>{name}</span>
                </Option>
              ))}
              filterOption={filterOption}
            />
          </Stack>
        )}
        {metadata?.predicate && (
          <InputWithLabel
            name="ruleConfig"
            id="ruleConfig"
            datatype={INPUT_TYPE.PREDICATE}
            picklistValues={picklistValues}
            values={
              rule.scope?.[0] !== 'record'
                ? [
                    {
                      value: 'field_value',
                      type: 'variable',
                      label: 'Field Value',
                      datatype: 'reference',
                    },
                    ...(metadata?.predicate?.fieldValues || []),
                  ]
                : metadata?.predicate?.fieldValues
            }
            value={rule?.ruleConfig}
            fetchPicklistValues={(param: FetchPicklistValuesParams) => {
              const dependantType = param.dependantType === 'Operator' ? 'dfiOperator' : param.dependantType;
              dispatch(fetchPicklistValues({ ...param, dependantType }));
            }}
            onChange={(name: string, id: string, value: FilterValue) => {
              setRule({
                ...rule,
                ruleConfig: value,
              });
            }}
          />
        )}
      </Stack>
    </Modal>
  );
};

export default RulesModal;
