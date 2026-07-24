//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { render } from 'tests/helpers';
import { tNamespaced } from 'utils/i18nUtil';

import TestNodeNotFound from '../TestNodeNotFound';

const tn = tNamespaced('TestNodeNotFound');

describe('TestNodeNotFound', () => {
  test('renders without error', async () => {
    const { findByText } = render(<TestNodeNotFound />);

    expect(await findByText(tn('node_not_available'))).toBeInTheDocument();
    expect(await findByText(tn('pipeline_changed'))).toBeInTheDocument();
  });
});
