import { render, screen, userEvent } from 'tests/helpers';

import ReferenceDataModal from './ReferenceDataUpsertModal';

jest.mock('utils/AjaxUtil');

const renderComponent = () =>
  render(<ReferenceDataModal visible onRequestClose={jest.fn()} />, {
    testState: {
      referenceData: {
        ids: [],
        entities: {},
        upsertError: {},
        upsertStatus: {},
        previewError: {},
        previewStatus: {},
      },
    },
  });

describe('ReferenceDataModal', () => {
  it('clears values when closed', async () => {
    renderComponent();

    await userEvent.type(screen.getByLabelText('Name'), 'Test');
    expect(screen.getByLabelText('Name')).toHaveValue('Test');

    await userEvent.click(screen.getByLabelText('Close'));
    expect(screen.getByLabelText('Name')).not.toHaveValue('Test');
  });

  it('clears errors when closed', async () => {
    renderComponent();

    await userEvent.click(screen.getByText('Import'));
    expect(screen.getByText('Name is required')).toBeVisible();

    await userEvent.click(screen.getByLabelText('Close'));
    expect(screen.queryByText('Name is required')).not.toBeInTheDocument();
  });
});
