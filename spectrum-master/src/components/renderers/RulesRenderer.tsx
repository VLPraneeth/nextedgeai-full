import { HStack } from 'components/layout';
import StatusBadge, { StatusBadgeType } from 'components/StatusBadge';
import { useEnhancedDispatch } from 'hooks/redux';
import { showDfiRuleDetails } from 'store/data-quality';

import './RulesCell.less';

export interface RulesCellRule {
  ruleId: string;
  ruleName: string;
}

export interface RulesCellProps {
  rules: RulesCellRule[];
}

// TODO: enhance with rules data popover
const RulesCell = ({ rules }: RulesCellProps) => {
  const dispatch = useEnhancedDispatch();

  return (
    <HStack spacing="xs" className="rules-list-cell">
      {rules.map(({ ruleId, ruleName }) => (
        <div
          key={ruleId}
          role="button"
          onClick={() => {
            dispatch(showDfiRuleDetails({ visible: true, ruleId }));
          }}>
          <StatusBadge className="rules-badge" type={StatusBadgeType.DEFAULT}>
            {ruleName}
          </StatusBadge>
        </div>
      ))}
    </HStack>
  );
};

const RulesCellRenderer = (rules: RulesCellProps['rules']) => <RulesCell rules={rules} />;

export default RulesCellRenderer;
