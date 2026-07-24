import { mockedAjaxUtils, renderHook, screen, userEvent } from 'tests/helpers';
import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { useDiscardEntityDraftWithConfirm } from '../SchemaStudio.hooks';

const mockedAxios = mockedAjaxUtils();
jest.mock('utils/AjaxUtil');

const entityId = 'entityId';
const connectorId = 'connectorId';
const entityName = 'entityName';

describe('Schema Studio hooks', () => {
  test('useDiscardEntityDraftWithConfirm should confirm with the user before deleting the draft', (done) => {
    const watchDelete = jest.fn(() => Promise.resolve());
    mockedAxios.deleteRequest.mockImplementation(watchDelete);

    const discardWithConfirm = renderHook(() => useDiscardEntityDraftWithConfirm());

    discardWithConfirm({ entityId, connectorId, entityName }).catch(() => {
      expect(watchDelete).not.toHaveBeenCalled();
      done();
    });

    const cancelButton = screen.queryByText('Cancel');
    expect(cancelButton).toBeInTheDocument();

    cancelButton && userEvent.click(cancelButton);
  });

  test('useDiscardEntityDraftWithConfirm should delete the draft after confirming', (done) => {
    const watchDelete = jest.fn(() => Promise.resolve());
    mockedAxios.deleteRequest.mockImplementation(watchDelete);

    const discardWithConfirm = renderHook(() => useDiscardEntityDraftWithConfirm());

    discardWithConfirm({ entityId, connectorId, entityName }).then(() => {
      expect(watchDelete).toHaveBeenCalledWith(makeUrl(DataUrlConstants.DISCARD_ENTITY_SCHEMA, { entityId }));
      done();
    });

    const discardElements = screen.queryAllByText('Delete Draft');
    // "Delete Draft" is in the confirmation text and the button element so two
    // elements are found
    const [, confirmButton] = discardElements;
    expect(confirmButton).toBeInTheDocument();

    confirmButton && userEvent.click(confirmButton);
  });
});
