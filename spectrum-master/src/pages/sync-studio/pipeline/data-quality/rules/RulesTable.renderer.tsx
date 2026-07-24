import { Menu, Modal, Tooltip } from 'antd';
import { noop } from 'lodash';
import { useCallback, useMemo } from 'react';

import Condition, { FetchPicklistParams } from 'components/inputs/condition';
import InputWithLabel from 'components/inputs/InputWithLabel';
import KebabMenu from 'components/KebabMenu';
import Popover from 'components/Popover';
import { Text } from 'components/typography';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { CellRendererProps } from 'pages/sync-studio/entity/PipelineDetailsTable/PipelineDetailsTable.renderers';
import { useDeleteRuleMutation, useGetCategoriesListQuery, useGetRulesMetadataQuery } from 'store/data-quality-v2/api';
import { fetchPicklistValues, FetchPicklistValuesParams } from 'store/picklists/thunks';
import AppConstants from 'utils/AppConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';

import { useDataQuality } from '../DataQuality.hooks';
import { DataQualityAction } from './DataQualityAction';
import './RulesTable.scss';

const tn = tNamespaced('DataQuality');

export const RulesActions = ({ data }: CellRendererProps<never>) => {
  const { entityId, graphVersion, navigateToEditRule, editable } = useDataQuality();

  const menuItems = [
    <Menu.Item key="edit" disabled={!editable}>
      <DataQualityAction>{tc('edit')}</DataQualityAction>
    </Menu.Item>,

    <Menu.Item key="delete" disabled={!editable}>
      <DataQualityAction>{tc('delete')}</DataQualityAction>
    </Menu.Item>,
  ];

  const [deleteRule] = useDeleteRuleMutation();

  const deleteRuleConfirm = useCallback(
    (id: string) => {
      Modal.confirm({
        title: tn('delete_rule'),
        content: <Text beDangerous>{tn('delete_confirmation', { name: data.name })}</Text>,
        okText: tc('delete'),
        cancelText: tc('cancel'),
        onOk: () => {
          deleteRule({
            ruleId: data?.id,
            syncariEntityId: entityId,
            version: graphVersion,
          });
        },
      });
    },
    [data?.id, data.name, deleteRule, entityId, graphVersion]
  );

  const clickHandler = useCallback(
    (action: { key: string }) => {
      switch (action.key) {
        case 'delete':
          deleteRuleConfirm(data.id);
          break;
        case 'edit':
          navigateToEditRule(data.id);
          break;
      }
    },
    [data.id, deleteRuleConfirm, navigateToEditRule]
  );

  return <KebabMenu onClick={clickHandler} menuItems={menuItems} />;
};

export const Category = ({ data }: CellRendererProps<never>) => {
  const { data: categories } = useGetCategoriesListQuery();
  const category = categories?.find((cat) => cat.id === data?.category)?.name || data?.category;

  return <span>{category}</span>;
};

export const Policy = ({ data }: CellRendererProps<never>) => {
  const { entityId } = useDataQuality();
  const { data: metadata } = useGetRulesMetadataQuery(
    { syncariEntityId: entityId },
    {
      skip: !Boolean(entityId),
    }
  );
  const policy = metadata?.policies?.find((policy) => policy.value === data?.policy)?.label || data?.policy;
  return <span>{policy}</span>;
};

export const Scope = ({ data }: CellRendererProps<never>) => {
  const { entityId } = useDataQuality();
  const { data: metadata } = useGetRulesMetadataQuery(
    { syncariEntityId: entityId },
    {
      skip: !Boolean(entityId),
    }
  );
  const scopeLabels = useMemo(() => {
    return (
      (data.scope || []).map(
        (scp: string) => metadata?.scopes?.find((scope) => scope.value === scp)?.label || data?.scope
      ) || []
    ).join(', ');
  }, [metadata?.scopes, data.scope]);

  return (
    <Tooltip title={scopeLabels}>
      <span>{scopeLabels}</span>
    </Tooltip>
  );
};

export const ConditionRenderer = ({ data }: CellRendererProps<never>) => {
  const picklistValues = useEnhancedSelector((state) => state.picklist.picklistValues);
  const dispatch = useEnhancedDispatch();
  const { entityId } = useDataQuality();

  const { data: metadata } = useGetRulesMetadataQuery(
    { syncariEntityId: entityId },
    {
      skip: !Boolean(entityId),
    }
  );

  const fetchValues = (param: FetchPicklistValuesParams | FetchPicklistParams) =>
    dispatch(fetchPicklistValues(param as FetchPicklistValuesParams));

  const condition = data?.condition && (
    <InputWithLabel
      name="ruleConfig"
      id="ruleConfig"
      datatype={AppConstants.INPUT_TYPE.PREDICATE}
      displayMode={AppConstants.INPUT_DISPLAY_MODE.READONLY}
      picklistValues={picklistValues}
      values={metadata?.predicate?.fieldValues}
      value={data?.condition}
      defaultValue={data?.condition}
      fetchPicklistValues={fetchValues}
      onChange={noop}
    />
  );

  const summary = data?.condition && (
    <Condition
      name="summary"
      values={metadata?.predicate?.fieldValues}
      defaultValue={data?.condition?.predicates?.[0]}
      fetchPicklistValues={(param: FetchPicklistValuesParams | FetchPicklistParams) =>
        dispatch(fetchPicklistValues({ ...(param as FetchPicklistValuesParams), dependantType: 'dfiOperator' }))
      }
      picklistValues={picklistValues}
      displayMode={AppConstants.INPUT_DISPLAY_MODE.READONLY}
      onChange={noop}
    />
  );

  return data?.condition ? (
    <Popover
      title={tn('condition')}
      className="condition-popover"
      overlayClassName="condition-popover-overlay"
      content={condition}>
      <div>
        {summary}
        {data?.condition?.predicates?.length > 1 && '…'}
      </div>
    </Popover>
  ) : (
    tc('none')
  );
};
