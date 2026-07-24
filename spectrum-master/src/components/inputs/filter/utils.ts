import { tNamespaced } from 'utils/i18nUtil';

import { isGroupPredicate } from '../types';
import type { ConditionValue, FilterValue } from '../types';

const tn = tNamespaced('SimpleFilter');

export const conditionToString = (condition: ConditionValue | FilterValue): string => {
  if (isGroupPredicate(condition)) {
    return condition.predicates
      .map((p) => conditionToString(p))
      .join(` <span class="operator">${tn(condition.operator)}</span> `);
  }

  if (condition.left && condition.operator && condition.right) {
    return `<strong>${condition.left.label}</strong> ${tn(condition.operator)} <strong>${
      condition.right.value
    }</strong>`;
  }

  if (condition.left && condition.operator) {
    return `<strong>${condition.left.label}</strong> ${tn(condition.operator)}`;
  }

  return '';
};

/**
 * Check if the filter is empty. Note that you cannot set an empty value on the
 * LHS, operator or RHS once you made a selection.
 * @param filter Filter value
 * @returns boolean true when filter is empty otherwise false
 */
export const isFilterEmpty = (filter?: FilterValue) => {
  const predicates = filter?.predicates;
  const firstPredicate: ConditionValue | undefined = predicates?.[0];
  return (
    !predicates ||
    predicates.length <= 0 ||
    !firstPredicate ||
    (!firstPredicate.operator && !firstPredicate.left && !firstPredicate.right)
  );
};

// Return undefined if the filter is empty
export const removeBlankFilter = (filter?: FilterValue) => (isFilterEmpty(filter) ? undefined : filter);

// This is just an interface wrapper. Filter cleanup is just remove placeholder values because the BE doesn't
// recognize partial empty filter
export const cleanupFilterInputValue = removeBlankFilter;
