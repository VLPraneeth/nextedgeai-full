import { noop } from 'lodash';

import { SelectionInfoWithAction, SelectionInfoWithActionProps } from 'components/SelectionInfoWithAction';
import { render, screen } from 'tests/helpers';

const props: SelectionInfoWithActionProps = {
  selectionText: 'Selection Text',
  action: noop,
  actionText: 'Action Text',
};

describe('SelectionInfoWithAction', () => {
  it('should correctly display the selectionText', async () => {
    render(<SelectionInfoWithAction {...props} />);

    expect(await screen.findByText('Selection Text')).toBeVisible();
  });

  it('should correctly display the actionText', async () => {
    render(<SelectionInfoWithAction {...props} />);

    expect(await screen.findByText('Action Text')).toBeVisible();
  });

  it('should not display the actionText when the action is undefined', async () => {
    const modifiedProps = {
      ...props,
      action: undefined,
    };

    render(<SelectionInfoWithAction {...modifiedProps} />);

    const action = screen.queryByText('Action Text');
    expect(action).toBeNull();
  });
});
