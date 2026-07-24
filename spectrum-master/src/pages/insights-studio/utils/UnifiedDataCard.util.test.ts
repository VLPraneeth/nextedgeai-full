import { FilterValue } from 'components/inputs/types';

import { removeFiltersWithRemovedEntityFields } from './UnifiedDataCard.util';

const tests = [
  {
    name: 'Removes conditions not using one of the supplied IDs',
    input: {
      groupPredicateId: 'g1',
      operator: 'AND',
      predicates: [{ left: { datasetId: 'd1' } }, { left: { datasetId: 'd1' } }, { left: { datasetId: 'd2' } }],
    },
    datasets: [{ datasetId: 'd1' }],
    output: {
      groupPredicateId: 'g1',
      operator: 'AND',
      predicates: [{ left: { datasetId: 'd1' } }, { left: { datasetId: 'd1' } }],
    },
  },

  {
    name: 'Removed any filter groups with an empty predicate array',
    input: {
      groupPredicateId: 'g1',
      operator: 'AND',
      predicates: [
        { left: { datasetId: 'd1' } },
        { predicates: [{ left: { datasetId: 'd2' } }, { left: { datasetId: 'd2' } }] },
      ],
    },
    datasets: [{ datasetId: 'd1' }],
    output: {
      groupPredicateId: 'g1',
      operator: 'AND',
      predicates: [{ left: { datasetId: 'd1' } }],
    },
  },

  {
    name: 'Removes conditions from nested filters',
    input: {
      groupPredicateId: 'g1',
      operator: 'AND',
      predicates: [
        { left: { datasetId: 'd1' } },
        { predicates: [{ left: { datasetId: 'd1' } }, { left: { datasetId: 'd2' } }, { left: { datasetId: 'd3' } }] },
        { left: { datasetId: 'd2' } },
        { left: { datasetId: 'd3' } },
      ],
    },
    datasets: [{ datasetId: 'd1' }, { datasetId: 'd2' }],
    output: {
      groupPredicateId: 'g1',
      operator: 'AND',
      predicates: [
        { left: { datasetId: 'd1' } },
        { predicates: [{ left: { datasetId: 'd1' } }, { left: { datasetId: 'd2' } }] },
        { left: { datasetId: 'd2' } },
      ],
    },
  },

  {
    name: 'Removes conditions from deeply nested filters',
    input: {
      groupPredicateId: 'g1',
      operator: 'AND',
      predicates: [
        { left: { datasetId: 'd1' } },
        {
          predicates: [
            { left: { datasetId: 'd1' } },
            {
              predicates: [
                { left: { datasetId: 'd1' } },
                {
                  predicates: [
                    { left: { datasetId: 'd1' } },
                    { left: { datasetId: 'd2' } },
                    { left: { datasetId: 'd3' } },
                  ],
                },
              ],
            },
          ],
        },
      ],
    },
    datasets: [{ datasetId: 'd1' }, { datasetId: 'd2' }],
    output: {
      groupPredicateId: 'g1',
      operator: 'AND',
      predicates: [
        { left: { datasetId: 'd1' } },
        {
          predicates: [
            { left: { datasetId: 'd1' } },
            {
              predicates: [
                { left: { datasetId: 'd1' } },
                {
                  predicates: [{ left: { datasetId: 'd1' } }, { left: { datasetId: 'd2' } }],
                },
              ],
            },
          ],
        },
      ],
    },
  },

  {
    name: 'Flattens groups when only one item remains in sub-group',
    input: {
      groupPredicateId: 'g1',
      operator: 'AND',
      predicates: [
        { predicateId: 'p1', left: { datasetId: 'd1' } },
        {
          groupPredicateId: 'g2',
          predicates: [
            { predicateId: 'p2', left: { datasetId: 'd1' } },
            { predicateId: 'p3', left: { datasetId: 'd2' } },
          ],
        },
        {
          groupPredicateId: 'g3',
          predicates: [
            { predicateId: 'p4', left: { datasetId: 'd1' } },
            {
              groupPredicateId: 'g4',
              predicates: [
                { predicateId: 'p5', left: { datasetId: 'd1' } },
                { predicateId: 'p6', left: { datasetId: 'd2' } },
              ],
            },
          ],
        },
        { left: { predicateId: 'p7', datasetId: 'd2' } },
        { left: { predicateId: 'p8', datasetId: 'd3' } },
      ],
    },
    datasets: [{ datasetId: 'd1' }],
    output: {
      groupPredicateId: 'g1',
      operator: 'AND',
      predicates: [
        { predicateId: 'p1', left: { datasetId: 'd1' } },
        { predicateId: 'p2', left: { datasetId: 'd1' } },
        {
          groupPredicateId: 'g3',
          predicates: [
            { predicateId: 'p4', left: { datasetId: 'd1' } },
            { predicateId: 'p5', left: { datasetId: 'd1' } },
          ],
        },
      ],
    },
  },

  {
    name: 'Returns undefined if no conditions remain after filtering',
    input: {
      groupPredicateId: 'g1',
      operator: 'AND',
      predicates: [{ left: { datasetId: 'd1' } }, { left: { datasetId: 'd1' } }, { left: { datasetId: 'd2' } }],
    },
    datasets: [{ datasetId: 'd4' }],
    output: undefined,
  },

  {
    name: 'Returns undefined if no filter is passed',
    input: undefined,
    datasets: [{ datasetId: 'd4' }],
    output: undefined,
  },
];

describe('removeFiltersWithRemovedEntityFields', () => {
  tests.forEach((test) => {
    it(test.name, () => {
      expect(removeFiltersWithRemovedEntityFields(test.input as FilterValue, test.datasets as any)).toEqual(
        test.output
      );
    });
  });
});
