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
