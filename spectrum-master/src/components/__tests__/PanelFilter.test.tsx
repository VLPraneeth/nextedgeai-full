//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import PanelFilter from 'components/PanelFilter';
import { render } from 'tests/helpers';

test('PanelFilter component renders correctly', async () => {
  const { findByPlaceholderText } = render(<PanelFilter />);

  const placeholder = await findByPlaceholderText('Filter…');

  expect(placeholder).toBeInTheDocument();
});
