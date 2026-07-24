import { Alert, Button, Col, Form, Icon, message, Row, Tooltip } from 'antd';
import cx from 'classnames';
import produce from 'immer';
import { orderBy } from 'lodash';
import moment from 'moment';
import { ChangeEvent, useEffect, useMemo, useState } from 'react';
import InlineSVG from 'react-inlinesvg';

import TrashIcon from 'assets/icons/Trash.svg';
import DrawerPanel from 'components/DrawerPanel';
import I18nProvider from 'components/I18nProvider';
import { useFieldOptions } from 'components/inputs/FieldOptions';
import InputWithLabel from 'components/inputs/InputWithLabel';
import Select from 'components/inputs/Select';
import { HStack, Spacer } from 'components/layout';
import { TranslatedText } from 'components/typography';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { EMPTY_ARRAY } from 'store/constants';
import {
  DfiRuleCondition,
  DfiRuleFormValues,
  DfiRulePostData,
  EntityDfiRulesPostData,
  RuleConditionType,
  RuleImpact,
  ruleImpactKeys,
  showDfiRuleDetails,
} from 'store/data-quality';
import { useSelectDfiEditingRule, useSelectDfiRulesForEntity } from 'store/data-quality/hooks';
import { saveDfiRule } from 'store/data-quality/thunks';
import AppConstants from 'utils/AppConstants';
import { replaceItem } from 'utils/ArrayUtil';
import { tNamespaced } from 'utils/i18nUtil';
import useSetState from 'utils/useSetState';

import RuleCondition from './components/RuleCondition';

import './DfiRuleDetailsPanel.less';

const classNameMap: Record<RuleConditionType, string> = {
  [RuleConditionType.BOOLEAN]: 'type-boolean',
  [RuleConditionType.STRING]: 'type-text',
  [RuleConditionType.INTEGER]: 'type-text',
  [RuleConditionType.REGEX]: 'type-text',
  [RuleConditionType.INT_RANGE]: 'type-int-range',
  [RuleConditionType.DATE_RANGE]: 'type-date-range',
};

const tc = tNamespaced('Common');
const tn = tNamespaced('DataQualityRules');

const emptyCondition: DfiRuleCondition = {
  name: '',
  conditionMatches: true,
  impact: RuleImpact.HIGH,
  type: RuleConditionType.BOOLEAN,
  conditionValues: [],
};

// Roughly sort conditions with simpler conditions first
const sortConditionsMap: Record<RuleConditionType, number> = {
  [RuleConditionType.BOOLEAN]: 0,
  [RuleConditionType.STRING]: 1,
  [RuleConditionType.INTEGER]: 2,
  [RuleConditionType.REGEX]: 3,
  [RuleConditionType.INT_RANGE]: 4,
  [RuleConditionType.DATE_RANGE]: 5,
};

const emptyFormData = {
  name: '',
  conditionMatches: true,
  selectedFields: [''],
  conditions: [emptyCondition],
};

interface DfiRuleDetailsPanelProps {
  selectedEntityId?: string;
}

const DfiRuleDetailsPanel = ({ selectedEntityId }: DfiRuleDetailsPanelProps) => {
  const dispatch = useEnhancedDispatch();

  const { dfiRuleDetailsOpen, dfiRuleDetailsRuleId } = useEnhancedSelector((state) => state.dataQuality);
  const initialRuleValues = useSelectDfiEditingRule(selectedEntityId);

  const entityRuleData = useSelectDfiRulesForEntity(selectedEntityId);

  const [formValues, setFormValues] = useSetState<DfiRuleFormValues>(emptyFormData);

  const [validationError, setValidatonError] = useState('');

  const impactOptionData = useMemo(
    () =>
      ruleImpactKeys.map((impact) => ({
        value: impact,
        label: tn(`impact_${impact}`),
      })),
    []
  );

  // Update the form values when modal opens
  useEffect(() => {
    // Only update on `open` so the form doesn't get reset while the modal is closing
    if (dfiRuleDetailsOpen) {
      if (initialRuleValues) {
        const { name, selectedFields, conditions } = initialRuleValues;
        setFormValues({ name, selectedFields, conditions });
      } else {
        setFormValues(emptyFormData);
      }
    }
  }, [dfiRuleDetailsOpen, initialRuleValues, setFormValues]);

  const selectedFieldSelectOptions = useFieldOptions(entityRuleData?.fields || []);

  const conditionOptions = useMemo(() => {
    const orderedConditions = orderBy(entityRuleData?.ruleDefinitions, [
      (condition) => sortConditionsMap[condition.type],
      'label',
    ]);

    return orderedConditions.map((rule) => ({
      value: rule.name,
      label: rule.label,
    }));
  }, [entityRuleData?.ruleDefinitions]);

  const saveRule = () => {
    setValidatonError('');

    if (!entityRuleData) {
      return;
    }

    const { fields, rules, lastPublished, ruleDefinitions, ...rest } = entityRuleData;

    const updatedRules = produce<DfiRulePostData[]>(rules, (draft) => {
      const updatedRule = { ...formValues, modified: true };

      if (dfiRuleDetailsRuleId) {
        const ruleToUpdate = draft.find((rule) => rule.id === dfiRuleDetailsRuleId);
        if (ruleToUpdate) {
          Object.assign(ruleToUpdate, updatedRule);
        }
      } else {
        draft.push(updatedRule);
      }
    });

    const entityRuleDataWithNewRule = { ...rest, rules: updatedRules } as EntityDfiRulesPostData;

    dispatch(saveDfiRule(entityRuleDataWithNewRule)).then((result) => {
      if (saveDfiRule.fulfilled.match(result)) {
        message.success(dfiRuleDetailsRuleId ? tn('update_success') : tn('create_success'));
        closeRuleModal();
      } else if (result.payload) {
        setValidatonError(result.payload.errorMessage);
      }
    });
  };

  const saveConditionAtIndex = (index: number) => (value: string) => {
    const selectedRule = entityRuleData?.ruleDefinitions.find((rule) => rule.name === value);

    if (selectedRule) {
      let conditionValues: string[] = EMPTY_ARRAY;

      if (selectedRule.type === RuleConditionType.DATE_RANGE) {
        conditionValues = [moment().toISOString(), moment().add(1, 'day').toISOString()];
      }

      setFormValues({
        conditions: replaceItem(formValues.conditions, index, {
          name: selectedRule.name,
          conditionMatches: true,
          impact: selectedRule.defaultImpact,
          type: selectedRule.type,
          conditionValues,
        }),
      });
    }
  };

  const changeOptionData = useMemo(
    () => [
      {
        value: AppConstants.TRUE,
        label: tn('if_true'),
      },
      {
        value: AppConstants.FALSE,
        label: tn('if_false'),
      },
    ],
    []
  );

  const closeRuleModal = () => {
    setValidatonError('');
    dispatch(showDfiRuleDetails({ visible: false }));
  };

  return (
    <I18nProvider namespace="DataQualityRules">
      <DrawerPanel
        className="synri-dfi-rules-modal"
        keyboard={false}
        mask
        maskClosable={false}
        onClose={closeRuleModal}
        title={tn(dfiRuleDetailsRuleId ? 'edit_rule' : 'new_rule')}
        visible={dfiRuleDetailsOpen}
        width="xlarge"
        footer={
          <HStack>
            <Button type="danger" ghost onClick={closeRuleModal}>
              {tc('cancel')}
            </Button>
            <Spacer flex />
            <Button type="primary" onClick={saveRule}>
              {tc(dfiRuleDetailsRuleId ? 'save' : 'create')}
            </Button>
          </HStack>
        }>
        {validationError && <Alert type="error" message={validationError} />}

        <Form>
          <Row>
            <Col span={24}>
              <InputWithLabel
                name="rule-name"
                label={tn('rule_name')}
                value={formValues.name}
                onChange={(e: ChangeEvent<HTMLInputElement>) => setFormValues({ name: e.target.value })}
              />
            </Col>
          </Row>

          {/* Target Fields */}
          <div className="synri-dfi-rules-target-fields">
            <TranslatedText color="gray-900" weight="semibold" size="md" lineHeight="loose" text="target_fields" />
            {formValues.selectedFields.map((fieldId, index) => {
              return (
                <div key={index} className="synri-dfi-rules-row">
                  <Form.Item>
                    <Select
                      placeholder={tn('select_field_placeholder')}
                      onChange={(value: string) => {
                        setFormValues({
                          selectedFields: replaceItem(formValues.selectedFields, index, value),
                        });
                      }}
                      value={fieldId || undefined}
                      {...selectedFieldSelectOptions}
                    />
                  </Form.Item>

                  <button
                    className="synri-dfi-rules-delete"
                    onClick={() => {
                      const newSelectedFields = formValues.selectedFields.filter(
                        (_field, fieldIndex) => fieldIndex !== index
                      );
                      if (newSelectedFields.length === 0) {
                        newSelectedFields.push('');
                      }

                      setFormValues({ selectedFields: newSelectedFields });
                    }}>
                    <InlineSVG src={TrashIcon} />
                  </button>
                </div>
              );
            })}
            <Button type="link" onClick={() => setFormValues({ selectedFields: [...formValues.selectedFields, ''] })}>
              <TranslatedText text="add_field" />
            </Button>
          </div>

          {/* Conditions */}
          <div className="synri-dfi-rules-conditions">
            <div className="synri-dfi-rules-condition-labels">
              <TranslatedText color="gray-900" weight="semibold" size="md" lineHeight="loose" text="conditions" />
              <div>
                <TranslatedText
                  className="synri-dfi-rules-condition-label-score-change"
                  weight="semibold"
                  color="gray-900"
                  size="md"
                  lineHeight="loose"
                  text="increase_score"
                />
                <div className="synri-dfi-rules-condition-label-impact">
                  <TranslatedText weight="semibold" color="gray-900" size="md" lineHeight="loose" text="impact" />
                  <Tooltip className="synri-dfi-rules-condition-tooltip-icon" title={tn('impact_tooltip')}>
                    <Icon type="info-circle" />
                  </Tooltip>
                </div>
              </div>
            </div>
            {formValues.conditions.map((condition, index) => {
              return (
                <div key={index} className="synri-dfi-rules-row-striped">
                  <div className={cx('synri-dfi-rules-condition-options', classNameMap[condition.type])}>
                    <Form.Item>
                      <Select
                        className="synri-dfi-rules-condition-key"
                        placeholder={tn('select_placeholder')}
                        onChange={saveConditionAtIndex(index)}
                        value={condition.name || undefined}
                        optionData={conditionOptions}
                      />
                    </Form.Item>

                    <RuleCondition
                      condition={condition}
                      onChange={(values) => {
                        setFormValues({
                          conditions: replaceItem(formValues.conditions, index, {
                            ...condition,
                            conditionValues: values,
                          }),
                        });
                      }}
                    />
                  </div>

                  <Spacer flex />

                  <Form.Item>
                    <Select
                      value={formValues.conditions[index].conditionMatches ? AppConstants.TRUE : AppConstants.FALSE}
                      showSearch={false}
                      optionData={changeOptionData}
                      onChange={(value: string) => {
                        setFormValues({
                          conditions: replaceItem(formValues.conditions, index, {
                            ...formValues.conditions[index],
                            conditionMatches: value === AppConstants.TRUE,
                          }),
                        });
                      }}
                    />
                  </Form.Item>

                  <Form.Item>
                    <Select
                      value={formValues.conditions[index].impact}
                      showSearch={false}
                      optionData={impactOptionData}
                      onChange={(value: RuleImpact) => {
                        setFormValues({
                          conditions: replaceItem(formValues.conditions, index, {
                            ...formValues.conditions[index],
                            impact: value,
                          }),
                        });
                      }}
                    />
                  </Form.Item>

                  <button
                    className="synri-dfi-rules-delete"
                    onClick={() => {
                      const conditions = formValues.conditions.filter((_field, fieldIndex) => fieldIndex !== index);
                      if (conditions.length === 0) {
                        conditions.push(emptyCondition);
                      }

                      setFormValues({ conditions });
                    }}>
                    <InlineSVG src={TrashIcon} />
                  </button>
                </div>
              );
            })}

            <Button
              type="link"
              onClick={() => setFormValues({ conditions: [...formValues.conditions, emptyCondition] })}>
              <TranslatedText text="add_condition" />
            </Button>
          </div>
        </Form>
      </DrawerPanel>
    </I18nProvider>
  );
};

export default DfiRuleDetailsPanel;
