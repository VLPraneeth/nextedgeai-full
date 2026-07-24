import { Form, Input } from 'antd';
import moment from 'moment';
import * as React from 'react';

import DateRangePicker from 'components/DateRangePicker';
import { TranslatedText } from 'components/typography';
import { DfiRuleCondition, RuleConditionType } from 'store/data-quality';
import { SHORT_DATE_FORMAT_WITH_TIME } from 'utils/DateUtil';
import { UnreachableCaseError } from 'utils/TypeUtils';

interface RuleConditionProps {
  condition: DfiRuleCondition;
  onChange: (conditionValues: string[]) => void;
}

const RuleCondition = ({ condition, onChange }: RuleConditionProps) => {
  const [firstConditionValue = '', secondConditionValue = ''] = condition.conditionValues || [];

  const makeChangeHandlerForIndex = (valueIndex: number) => (evt: React.ChangeEvent<HTMLInputElement>) => {
    const newValues = condition.conditionValues ? [...condition.conditionValues] : [];
    newValues[valueIndex] = evt.target.value;
    onChange(newValues);
  };

  switch (condition.type) {
    case RuleConditionType.BOOLEAN:
      return null;
    case RuleConditionType.STRING:
    case RuleConditionType.INTEGER:
    case RuleConditionType.REGEX:
      return (
        <div className="synri-dfi-rules-condition-options-container">
          <Form.Item>
            <Input
              type={condition.type === RuleConditionType.INTEGER ? 'number' : 'text'}
              name={condition.name}
              value={firstConditionValue}
              onChange={makeChangeHandlerForIndex(0)}
              placeholder={condition.type === RuleConditionType.REGEX ? '^regex$' : ''}
            />
          </Form.Item>
        </div>
      );
    case RuleConditionType.INT_RANGE:
      return (
        <div className="synri-dfi-rules-condition-options-container">
          <Form.Item>
            <Input
              type="number"
              name={`${condition.name}_start`}
              value={firstConditionValue}
              onChange={makeChangeHandlerForIndex(0)}
            />
          </Form.Item>
          <TranslatedText namespace="Common" text="to" />
          <Form.Item>
            <Input
              type="number"
              name={`${condition.name}_end`}
              value={secondConditionValue}
              onChange={makeChangeHandlerForIndex(1)}
            />
          </Form.Item>
        </div>
      );

    case RuleConditionType.DATE_RANGE:
      const startMoment = moment(firstConditionValue);
      const endMoment = moment(secondConditionValue);

      const startDate = startMoment.isValid() ? startMoment : moment();
      const endDate = endMoment.isValid() ? endMoment : moment().add(1, 'day');

      return (
        <div className="synri-dfi-rules-condition-options-container">
          <DateRangePicker
            startDate={startDate}
            endDate={endDate}
            format={SHORT_DATE_FORMAT_WITH_TIME}
            onChange={(startDate, endDate) => {
              onChange([startDate?.toISOString() || '', endDate?.toISOString() || '']);
            }}
            showTime
          />
        </div>
      );

    default:
      throw new UnreachableCaseError(condition.type);
  }
};

export default RuleCondition;
