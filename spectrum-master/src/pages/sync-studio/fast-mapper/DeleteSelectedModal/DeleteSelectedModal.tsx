import { GridApi } from 'ag-grid-community';
import { Modal } from 'antd';

import { tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('DeleteSelectedModal');

export interface DeleteSelectedModalProps {
  visible: boolean;
  onOk: () => void;
  onCancel: () => void;
  gridApi?: GridApi;
}

export const DeleteSelectedModal = ({ visible, onOk, onCancel, gridApi }: DeleteSelectedModalProps) => {
  const selectedRowCount = gridApi?.getSelectedRows()?.length;

  return (
    <Modal visible={visible} title={tn('title')} onOk={onOk} onCancel={onCancel}>
      {tn('description', { count: selectedRowCount })}
    </Modal>
  );
};
