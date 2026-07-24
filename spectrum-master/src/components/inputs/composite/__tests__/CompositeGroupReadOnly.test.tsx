//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import configureAppStore from 'store/configureStore';
import { render, screen } from 'tests/helpers';

import CompositeGroupReadOnly from '../CompositeGroupReadOnly';
import {
  getValue,
  getConfiguration,
  getPicklistValues,
  getEntityPipelineState,
} from '../CompositeGroupReadOnly.fixtures';

describe('Composite Group ReadOnly', () => {
  test('should render the group readonly with token value testing', async () => {
    render(
      <CompositeGroupReadOnly
        configuration={getConfiguration()}
        order={1}
        picklistValues={getPicklistValues()}
        value={getValue({
          // @ts-ignore: this might need to be fixed?
          newValue: { name: 'newValue', value: 'testing' },
        })}
        disabled
      />,
      {
        store: configureAppStore({
          entityPipeline: getEntityPipelineState(),
        }),
      }
    );
    expect(await screen.findByText('testing')).toBeInTheDocument();
  });

  /*
   * //TODO: Is this still needed? We shoudln't be rendering a blank token for a readonly field, we only render
   * //the tokens now, which would mean no blank token
   * //
   *  test('should render the group readonly with blank token', async () => {
   *    const { container } = render(
   *      <CompositeGroupReadOnly
   *        configuration={getConfiguration()}
   *        order={1}
   *        picklistValues={getPicklistValues()}
   *        value={getValue({
   *          // @ts-ignore
   *          newValue: { name: 'newValue', value: '' },
   *        })}
   *        disabled
   *      />,
   *      {
   *        store: configureAppStore({
   *          entityPipeline: getEntityPipelineState(),
   *        }),
   *      }
   *    );
   *
   *    screen.debug();
   *
   *    // No input/textarea element is rendered :(.
   *    // Unfortunately theres no better way to check for blank values
   *    // but checking the data attribute. Fragile much.
   *    expect(container.querySelector('[data-slate-length="0"]')).toBeInTheDocument();
   *  });
   */
});
