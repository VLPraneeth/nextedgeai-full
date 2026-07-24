//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { renderWithRouter, screen } from 'tests/helpers';

import SideMenuItem from './SideMenuItem';

describe('SideMenuItem', () => {
  it('renders provided text', async () => {
    const title = 'pikachu is op';

    renderWithRouter(<SideMenuItem navigationStatus={''} isCollapsed={false} selected={false} title={title} />);

    expect(await screen.findByText(title)).toBeVisible();
  });
});
