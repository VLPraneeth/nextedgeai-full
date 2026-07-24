//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { render } from '@testing-library/react';

import PropertyPanelAction from 'components/PropertyPanelAction';

it('Empty PropertyPanelAction component renders correctly', () => {
  const { asFragment } = render(<PropertyPanelAction actions={[]} />);
  expect(asFragment()).toMatchSnapshot();
});

it('PropertyPanelAction component renders correctly with actions', () => {
  const actions = [
    {
      id: 'tag',
      handler: () => {},
      icon: 'tag',
      name: 'Create Pipeline',
    },
    {
      id: 'create',
      handler: () => {},
      icon: 'arrow',
      name: 'Create Field Pipeline',
    },
  ];
  const { asFragment } = render(<PropertyPanelAction actions={actions} />);
  expect(asFragment()).toMatchSnapshot();
});
