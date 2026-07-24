//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { OperatorValue } from 'components/inputs/types';
import configureAppStore from 'store/configureStore';
import { testEntityPipelineState } from 'store/tokens/__testdata';
import { fireEvent, render, screen, userEvent } from 'tests/helpers';
import AppConstants from 'utils/AppConstants';
import { noop } from 'utils/AppUtil';
import { t } from 'utils/i18nUtil';

import { getPredicateValues, getOperatorPicklist } from '../Filter.fixtures';
import Filter from '../index';

describe('Filter base tests', () => {
  const value = {
    predicates: [
      {
        predicateId: '5ee2ec58e794d775c028eee3',
        left: {
          datatype: 'string',
          picklistGroup: 'Account (Syncari)',
          label: 'About Us',
          type: 'variable',
          value: '5ee15c4d7f939d21244da2cd',
        },
        operator: 'eq',
        right: { type: 'literal', value: 'about us value' },
      },
      {
        predicateId: '5ee29cfc1513bc2ad9b21e36',
        left: {
          datatype: 'textarea',
          picklistGroup: 'Account (Syncari)',
          label: 'Account Description',
          type: 'variable',
          value: '5ee15c4d7f939d21244da2e2',
        },
        operator: 'eq',
        right: { type: 'literal', value: 'jjj' },
      },
      {
        predicateId: '5ee31de1c1a3950d587eaaef',
        left: {
          datatype: 'picklist',
          picklistGroup: 'Account (Syncari)',
          label: 'Account Source',
          type: 'variable',
          value: '5ee15c4d7f939d21244da2ec',
        },
        operator: 'starts_with',
        right: { type: 'literal' },
      },
    ],
    groupPredicateId: '5ee2ec37e794d775c028eecc',
    operator: 'AND',
  };

  test('renders blank filter', async () => {
    const val = {
      predicates: [
        {
          predicateId: '5ee2ec58e794d775c028eee3',
          left: undefined,
          operator: undefined,
          right: undefined,
        },
      ],
    };
    render(
      <Filter
        name="test"
        onChange={() => {}}
        onDelete={() => {}}
        value={val}
        picklistValues={{}}
        fetchPicklistValues={() => {}}
        fieldValues={[]}
      />
    );
    await userEvent.hover(screen.queryAllByRole('combobox')?.[0]);
    expect(await screen.findByText('+ Add Condition')).toBeInTheDocument();
  });

  test('renders string value', async () => {
    const val = {
      predicates: [
        {
          predicateId: '5ee2ec58e794d775c028eee3',
          left: {
            datatype: 'string',
            picklistGroup: 'Account (Syncari)',
            label: 'About Us',
            type: 'variable',
            value: '5ee15c4d7f939d21244da2cd',
          },
          operator: 'eq',
          right: { type: 'literal', value: 'about us string' },
        },
      ],
      groupPredicateId: '5ee2ec37e794d775c028eecc',
      operator: 'AND',
    };
    render(
      <Filter
        name="test"
        onChange={() => {}}
        onDelete={() => {}}
        // @ts-expect-error
        value={val}
        picklistValues={getOperatorPicklist().picklistValues}
        fetchPicklistValues={() => {}}
        fieldValues={[]}
      />,
      {
        store: configureAppStore({
          entityPipeline: testEntityPipelineState,
        }),
      }
    );
    expect(await screen.findByText('about us string')).toBeInTheDocument();
  });

  // TODO: This is failing in Jenkins, but not locally. This is potentially a
  // strange timing issue.
  // eslint-disable-next-line
  test.skip('renders multiple values predicates', async () => {
    const val = {
      predicates: [
        {
          predicateId: '5ee2ec58e794d775c028eee3',
          left: {
            datatype: 'string',
            picklistGroup: 'Account (Syncari)',
            label: 'About Us',
            type: 'variable',
            value: '5ee15c4d7f939d21244da2cd',
          },
          operator: 'eq',
          right: { type: 'literal', value: 'about us value' },
        },
        {
          predicateId: '5ee29cfc1513bc2ad9b21e36',
          left: {
            datatype: 'textarea',
            picklistGroup: 'Account (Syncari)',
            label: 'Account Description',
            type: 'variable',
            value: '5ee15c4d7f939d21244da2e2',
          },
          operator: 'eq',
          right: { type: 'literal', value: 'account description value' },
        },
      ],
      groupPredicateId: '5ee2ec37e794d775c028eecc',
      operator: 'AND',
    };
    render(
      <Filter
        name="test"
        onChange={() => {}}
        onDelete={() => {}}
        // @ts-expect-error
        value={val}
        picklistValues={getOperatorPicklist().picklistValues}
        fetchPicklistValues={() => {}}
        fieldValues={[]}
      />,
      {
        store: configureAppStore({
          entityPipeline: testEntityPipelineState,
        }),
      }
    );
    expect(await screen.findByText('about us value')).toBeInTheDocument();
    expect(await screen.findByText('account description value')).toBeInTheDocument();
  });

  test('render the group operator', async () => {
    render(
      <Filter
        name="test"
        onChange={() => {}}
        onDelete={() => {}}
        // @ts-ignore
        value={value}
        picklistValues={getOperatorPicklist().picklistValues}
        fetchPicklistValues={() => {}}
        fieldValues={[]}
      />,
      {
        store: configureAppStore({
          entityPipeline: testEntityPipelineState,
        }),
      }
    );

    expect(await screen.findByText('AND')).toBeInTheDocument();
  });

  test('render the group operator with OR', async () => {
    const val = {
      predicates: [
        {
          predicateId: '5ee2ec58e794d775c028eee3',
          left: {
            datatype: 'string',
            picklistGroup: 'Account (Syncari)',
            label: 'About Us',
            type: 'variable',
            value: '5ee15c4d7f939d21244da2cd',
          },
          operator: 'eq',
          right: { type: 'literal', value: 'about us value' },
        },
        {
          predicateId: '5ee29cfc1513bc2ad9b21e36',
          left: {
            datatype: 'textarea',
            picklistGroup: 'Account (Syncari)',
            label: 'Account Description',
            type: 'variable',
            value: '5ee15c4d7f939d21244da2e2',
          },
          operator: 'eq',
          right: { type: 'literal', value: 'account description value' },
        },
      ],
      groupPredicateId: '5ee2ec37e794d775c028eecc',
      operator: 'OR',
    };
    render(
      <Filter
        name="test"
        onChange={() => {}}
        onDelete={() => {}}
        // @ts-expect-error
        value={val}
        picklistValues={getOperatorPicklist().picklistValues}
        fetchPicklistValues={() => {}}
        fieldValues={[]}
      />,
      {
        store: configureAppStore({
          entityPipeline: testEntityPipelineState,
        }),
      }
    );
    expect(await screen.findByText('OR')).toBeInTheDocument();
  });

  test('change the group operator', async () => {
    const val = {
      predicates: [
        {
          predicateId: '5ee2ec58e794d775c028eee3',
          left: {
            datatype: 'string',
            picklistGroup: 'Account (Syncari)',
            label: 'About Us',
            type: 'variable',
            value: '5ee15c4d7f939d21244da2cd',
          },
          operator: 'eq',
          right: { type: 'literal', value: 'about us value' },
        },
        {
          predicateId: '5ee29cfc1513bc2ad9b21e36',
          left: {
            datatype: 'textarea',
            picklistGroup: 'Account (Syncari)',
            label: 'Account Description',
            type: 'variable',
            value: '5ee15c4d7f939d21244da2e2',
          },
          operator: 'eq',
          right: { type: 'literal', value: 'account description value' },
        },
      ],
      groupPredicateId: '5ee2ec37e794d775c028eecc',
      operator: 'OR',
    };
    render(
      <Filter
        name="test"
        onChange={() => {}}
        onDelete={() => {}}
        // @ts-expect-error
        value={val}
        picklistValues={getOperatorPicklist().picklistValues}
        fetchPicklistValues={() => {}}
        fieldValues={[]}
      />,
      {
        store: configureAppStore({
          picklist: getOperatorPicklist(),
          entityPipeline: testEntityPipelineState,
        }),
      }
    );
    expect(await screen.findByText('OR')).toBeInTheDocument();
    expect(screen.queryByText('AND')).not.toBeInTheDocument();

    const operatorEl = document.querySelector('.filter-operator-container a');
    if (operatorEl !== null) {
      fireEvent.click(operatorEl);
    }
    expect(await screen.findByText('AND')).toBeInTheDocument();
  });

  // TODO: This is failing in Jenkins, but not locally. This is potentially a
  // strange timing issue.
  // eslint-disable-next-line
  test.skip('delete a predicate', async () => {
    const val = {
      predicates: [
        {
          predicateId: '5ee2ec58e794d775c028eee3',
          left: {
            datatype: 'string',
            picklistGroup: 'Account (Syncari)',
            label: 'About Us',
            type: 'variable',
            value: '5ee15c4d7f939d21244da2cd',
          },
          operator: 'eq',
          right: { type: 'literal', value: 'about us value' },
        },
        {
          predicateId: '5ee29cfc1513bc2ad9b21e36',
          left: {
            datatype: 'textarea',
            picklistGroup: 'Account (Syncari)',
            label: 'Account Description',
            type: 'variable',
            value: '5ee15c4d7f939d21244da2e2',
          },
          operator: 'eq',
          right: { type: 'literal', value: 'account description value' },
        },
      ],
      groupPredicateId: '5ee2ec37e794d775c028eecc',
      operator: 'AND',
    };
    render(
      <Filter
        name="test"
        onChange={() => {}}
        onDelete={() => {}}
        // @ts-expect-error
        value={val}
        picklistValues={getOperatorPicklist().picklistValues}
        fetchPicklistValues={() => {}}
        fieldValues={[]}
      />,
      {
        store: configureAppStore({
          entityPipeline: testEntityPipelineState,
        }),
      }
    );

    expect(await screen.findByText('about us value')).toBeInTheDocument();
    expect(await screen.findByText('account description value')).toBeInTheDocument();
    const deleteBtn = document.querySelector('.anticon-delete');
    expect(deleteBtn).toBeInTheDocument();
    if (deleteBtn) {
      fireEvent.click(deleteBtn);
    }
    expect(document.querySelector('input[value="about us value"]')).not.toBeInTheDocument();
  });

  test('renders multiple values readonly', async () => {
    const val = getPredicateValues();
    render(
      <Filter
        name="test"
        onChange={() => {}}
        onDelete={() => {}}
        value={val}
        picklistValues={getOperatorPicklist().picklistValues}
        fetchPicklistValues={() => {}}
        fieldValues={[]}
        displayMode={AppConstants.INPUT_DISPLAY_MODE.READONLY}
      />,
      {
        testState: {
          entityPipeline: testEntityPipelineState,
        },
      }
    );
    expect(await screen.findByText('about us value')).toBeInTheDocument();
    expect(await screen.findByText('account description value')).toBeInTheDocument();
  });

  test('renders single condition', async () => {
    render(
      <Filter
        name="test"
        onChange={() => {}}
        onDelete={() => {}}
        value={{}}
        picklistValues={getOperatorPicklist().picklistValues}
        fetchPicklistValues={() => {}}
        fieldValues={[]}
        singleCondition
      />,
      {
        testState: {
          entityPipeline: testEntityPipelineState,
        },
      }
    );
    expect(screen.queryByText(t('Filter.add_condition'))).not.toBeInTheDocument();
  });

  test('test boolean datatype default value', async () => {
    let filterValue: any;

    render(
      <Filter
        name="test"
        onChange={(_, __, value) => {
          // @eslint-disable-next-line
          filterValue = value;
        }}
        onDelete={noop}
        picklistValues={{
          '5e613d6598a68d0001ab85e1/testOperator': [
            { label: 'Equals', unary: false, value: 'eq' },
            { label: 'Not Equals', unary: false, value: 'ne' },
          ] as OperatorValue[],
        }}
        fetchPicklistValues={noop}
        fieldValues={[
          {
            // @ts-ignore
            datatype: 'boolean',
            picklistGroup: 'Account (syncari)',
            label: 'Is Public',
            type: 'variable',
            value: '5e613d6598a68d0001ab85e1',
          },
        ]}
      />,
      {
        testState: {
          entityPipeline: testEntityPipelineState,
        },
      }
    );

    const fieldSelect = await screen.findByRole('combobox', { name: t('Filter.condition_lhs') });
    await userEvent.click(fieldSelect);
    await userEvent.click(await screen.findByText('Is Public'));

    const operatorSelect = await screen.findByRole('combobox', { name: t('Filter.condition_operator') });
    await userEvent.click(operatorSelect);
    await userEvent.click(await screen.findByText('Equals'));

    expect(filterValue.predicates[0].right).toEqual({ type: 'literal', value: false });
  });

  test('renders multivalue text operator datatype', async () => {
    const value = getPredicateValues({
      predicates: [
        {
          predicateId: '5ee2ec58e794d775c028eee3',
          left: {
            datatype: 'string',
            picklistGroup: 'Account (Syncari)',
            label: 'About Us',
            type: 'variable',
            renderType: 'string',
            value: '5ee15c4d7f939d21244da2cd',
          },
          operator: 'eq',
          right: { type: 'literal', value: ['about us value'] },
        },
      ],
    });

    const picklistValues = getOperatorPicklist({
      '5ee15c4d7f939d21244da2cd/predicateOperator': [
        {
          label: 'Equals',
          unary: false,
          value: 'eq',
          datatype: 'multivaluetext',
        },
      ],
    });

    const { rerender } = render(
      <Filter
        name="predicate"
        onChange={() => {}}
        onDelete={() => {}}
        value={value}
        picklistValues={picklistValues}
        fetchPicklistValues={() => {}}
        fieldValues={[]}
      />,
      {
        testState: {
          entityPipeline: testEntityPipelineState,
        },
      }
    );

    rerender(
      <Filter
        name="predicate"
        onChange={() => {}}
        onDelete={() => {}}
        value={value}
        picklistValues={picklistValues}
        operatorType="Operator"
        rightType="Right"
        fetchPicklistValues={() => {}}
        fieldValues={[]}
      />
    );

    expect(await screen.findByText('about us value')).toBeInTheDocument();
    expect(screen.getByTitle('about us value')).toBeInTheDocument();
  });

  test('renders inputcomposite operator renderType', async () => {
    const value = getPredicateValues({
      predicates: [
        {
          predicateId: '5ee2ec58e794d775c028eee3',
          left: {
            datatype: 'string',
            picklistGroup: 'Account (Syncari)',
            label: 'About Us',
            type: 'variable',
            renderType: 'string',
            value: '5ee15c4d7f939d21244da2cd',
          },
          operator: 'findNotMatchingValue',
          right: {
            type: 'literal',
            value: {
              // @ts-expect-error
              texts: ['about us value'],
              sortDirection: 'most_recently_created_with_value',
            },
          },
        },
      ],
    });

    const picklistValues = getOperatorPicklist({
      '5ee15c4d7f939d21244da2cd/predicateOperator': [
        {
          label: 'Find not matching value',
          unary: false,
          value: 'findNotMatchingValue',
          renderType: 'compositeinput',
          configuration: [
            {
              datatype: 'multivaluetext',
              name: 'texts',
            },
          ],
        },
      ],
    });

    const { rerender } = render(
      <Filter
        name="predicate"
        onChange={() => {}}
        onDelete={() => {}}
        value={value}
        picklistValues={picklistValues}
        fetchPicklistValues={() => {}}
        fieldValues={[]}
      />,
      {
        testState: {
          entityPipeline: testEntityPipelineState,
          picklist: getOperatorPicklist(),
        },
      }
    );

    expect(await screen.findByText('about us value')).toBeInTheDocument();

    rerender(
      <Filter
        name="predicate"
        onChange={() => {}}
        onDelete={() => {}}
        value={value}
        picklistValues={picklistValues}
        operatorType="Operator"
        rightType="Right"
        displayMode="readonly"
        fetchPicklistValues={() => {}}
        fieldValues={[]}
      />
    );
    expect(await screen.findByText('about us value')).toHaveAttribute('data-testid', 'readonly-multi-value-text');
  });
});
