import { InstanceCopyModalType } from 'store/instances/slice';
import { render, screen } from 'tests/helpers';

import { InstanceCopyModal } from './InstanceCopyModal';

describe('InstanceCopyModal', () => {
  it('should render render two text input fields in the SUPER_ADMIN modal', async () => {
    render(<InstanceCopyModal />, {
      testState: {
        instance: {
          instanceCopyModal: {
            visible: true,
            modalType: InstanceCopyModalType.GLOBAL,
          },
        },
      },
    });

    expect(await screen.findByLabelText('Source Instance (NextEdge ID)')).toBeVisible();
    expect(await screen.findByLabelText('Destination Instance (NextEdge ID)')).toBeVisible();
  });

  it('should render render one text input field and one dropdown in the ADMIN modal', async () => {
    render(<InstanceCopyModal />, {
      testState: {
        instance: {
          instances: [
            {
              syncariId: 'TEST_INSTANCE_1',
            },
            {
              syncariId: 'TEST_INSTANCE_2',
            },
          ],
          instanceCopyModal: {
            visible: true,
            modalType: InstanceCopyModalType.ORG_ONLY,
            syncariId: 'TEST_INSTANCE_1',
          },
        },
      },
    });

    expect(await screen.findByLabelText('Source Instance (NextEdge ID)')).toBeDisabled();
    expect(await screen.findByText('TEST_INSTANCE_2')).toBeVisible();
  });
});
