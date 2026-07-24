import ObjectID from 'bson-objectid';

import { OPERATOR } from 'components/inputs/filter';
import { ConditionValue } from 'components/inputs/types';
import { EntityFilter } from 'store/data-studio/types';

/* takes in ConditionValue (eg, from dataScore contributing factor), and creates an EntityFilter
 * that we can pass to our FilterPanel
 */
export const makeFakeEntityFilter = (
  filterCondition?: ConditionValue,
  values: Partial<EntityFilter> = {}
): Partial<EntityFilter> | undefined =>
  filterCondition
    ? {
        criteria: {
          groupPredicateId: ObjectID.generate(),
          operator: OPERATOR.AND,
          predicates: [
            {
              ...filterCondition,
              predicateId: ObjectID.generate(),
            },
          ],
        },
        ...values,
      }
    : undefined;

export const processPredicates = {
  /**
   * Counts all predicates recursively, including nested predicates
   */
  countAll: (predicates: any[]): number => {
    let count = 0;

    const traverse = (node: any) => {
      if (!node) return;
      if (Array.isArray(node.predicates)) {
        node.predicates.forEach(traverse);
      } else if (node.left?.value && node.operator) {
        // Only count valid predicates with left value and operator
        count++;
      }
    };

    predicates.forEach(traverse);
    return count;
  },

  getDuplicates: (predicates: any[]): string[] => {
    const seen = new Set<string>();
    const duplicates = new Set<string>();

    const traverse = (node: any) => {
      if (!node) return;
      if (node.left?.value) {
        seen.has(node.left.value) ? duplicates.add(node.left.value) : seen.add(node.left.value);
      }
      if (Array.isArray(node.predicates)) {
        node.predicates.forEach(traverse);
      }
    };

    predicates.forEach(traverse);
    return Array.from(duplicates);
  },

  extract: (predicates: any[], fieldValue: string): any[] => {
    const matched: any[] = [];

    const traverse = (node: any) => {
      if (Array.isArray(node.predicates)) {
        node.predicates.forEach(traverse);
      } else if (node.left?.value === fieldValue) {
        matched.push(node);
      }
    };

    predicates.forEach(traverse);
    return matched;
  },

  remove: (predicates: any[], fieldValue: string): any[] => {
    return predicates.reduce((filtered: any[], predicate) => {
      if (predicate.predicates && Array.isArray(predicate.predicates)) {
        const filteredNested = processPredicates.remove(predicate.predicates, fieldValue);
        if (filteredNested.length > 0) {
          filtered.push({ ...predicate, predicates: filteredNested });
        }
      } else if (predicate.left?.value !== fieldValue) {
        filtered.push(predicate);
      }
      return filtered;
    }, []);
  },

  filterValid: (predicates: any[]): any[] => {
    return predicates.reduce((filtered: any[], predicate) => {
      if (predicate.predicates && Array.isArray(predicate.predicates)) {
        const filteredNested = processPredicates.filterValid(predicate.predicates);
        if (filteredNested.length > 0) {
          filtered.push({ ...predicate, predicates: filteredNested });
        }
      } else if (predicate.left?.value && predicate.operator) {
        filtered.push({
          ...predicate,
          right: predicate.right || { type: 'literal', value: '' },
        });
      }
      return filtered;
    }, []);
  },

  updateInPlace: (existingPredicates: any[], fieldValue: string, newPredicates: any[]): any[] => {
    let foundInGroup = false;
    let foundAtRoot = false;

    const updated = existingPredicates.reduce((acc: any[], predicate) => {
      if (predicate.predicates && Array.isArray(predicate.predicates)) {
        const hasFieldInGroup = processPredicates.extract(predicate.predicates, fieldValue).length > 0;

        if (hasFieldInGroup) {
          foundInGroup = true;
          const otherPredicates = processPredicates.remove(predicate.predicates, fieldValue);
          const updatedGroupPredicates = [...otherPredicates, ...newPredicates];

          if (updatedGroupPredicates.length > 0) {
            acc.push({ ...predicate, predicates: updatedGroupPredicates });
          }
        } else {
          acc.push(predicate);
        }
      } else if (predicate.left?.value === fieldValue) {
        foundAtRoot = true;
      } else {
        acc.push(predicate);
      }
      return acc;
    }, []);

    if ((foundAtRoot || (!foundInGroup && !foundAtRoot)) && newPredicates.length > 0) {
      updated.push(...newPredicates);
    }

    return updated;
  },
};
