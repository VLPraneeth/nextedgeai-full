import { isFilterEmpty } from './utils';

describe('filter.utils isFilterEmpty', () => {
  it('should return false if a complete filter is passed', () => {
    expect(
      isFilterEmpty({
        predicates: [
          {
            left: {
              label: 'Account.About Us (account.AboutUs)',
              value: '632905c5bee3a10156acce03',
            },
            right: {
              value: '',
              type: 'literal',
            },
            predicateId: '635087161c2a67ab20674d8c',
            operator: 'contains',
          },
        ],
        groupPredicateId: '635087161c2a67ab20674d8d',
        operator: 'AND',
      })
    ).toBe(false);
  });

  it('should return true if all fields are empty', () => {
    expect(
      isFilterEmpty(
        // @ts-expect-error
        {
          predicates: [
            {
              left: undefined,
              operator: undefined,
              right: undefined,
            },
          ],
        }
      )
    ).toBe(true);
  });

  it('should return false if one input is filled', () => {
    expect(
      isFilterEmpty(
        // @ts-expect-error
        {
          predicates: [
            {
              left: undefined,
              operator: 'contains',
              right: undefined,
            },
          ],
        }
      )
    ).toBe(false);
  });
});
