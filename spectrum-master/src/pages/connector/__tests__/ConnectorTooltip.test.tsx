//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { render, userEvent } from 'tests/helpers';

import ConnectorTooltip from '../ConnectorTooltip';

describe('ConnectorTooltip', () => {
  test('should render the tooltip on hover', async () => {
    const tooltipText = 'my tooltip';

    const { findByText, getByTestId } = render(<ConnectorTooltip />, {
      testState: {
        pipeline: {
          tooltipCoordinates: {
            top: '100px',
            left: '100px',
          },
        },
        connector: {
          nodeTootipMessage: tooltipText,
        },
      },
    });

    await userEvent.hover(getByTestId('tooltip-container'));
    expect(await findByText(tooltipText)).toBeInTheDocument();
  });
});
