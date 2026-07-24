import Modal from 'antd/lib/modal';
import { useCallback, useEffect, useMemo, useState } from 'react';
import * as React from 'react';
import { DragDropContext, DropResult } from 'react-beautiful-dnd';

import Button from 'components/Button';
import InlineMessage from 'components/InlineMessage';
import { HStack } from 'components/layout';
import SearchBox from 'components/SearchBox';
import { moveItem } from 'utils/ArrayUtil';
import { tc, tNamespaced } from 'utils/i18nUtil';

import './ConfigureTableColumnsModal.scss';
import { ColumnList } from './ColumnList';
const tn = tNamespaced('ConfigureTableColumns');

export interface ColumnItem {
  columnName: string;
  isSelected: boolean;
}

export interface ConfigureColumnsModalProps {
  isOpen: boolean;
  /* fn to close this modal */
  onRequestClose: () => void;
  /* fn called when clicking 'Save' */
  onRequestSave: (columnItems: ColumnItem[]) => void;
  /* the columns we can select from */
  allAvailableColumns: ColumnItem[];
  labelForColumn: (columnName: string) => string;
  dataTypeForColumn: (columnName: string) => string;
}

const ConfigureColumnsModal = ({
  isOpen,
  onRequestClose,
  onRequestSave,
  allAvailableColumns,
  dataTypeForColumn,
  labelForColumn,
}: ConfigureColumnsModalProps): React.ReactElement => {
  const [draftAllAvailableColumns, setDraftAllAvailableColumns] = useState<ColumnItem[]>(() => allAvailableColumns);
  const [filterString, setFilterString] = useState('');
  const [hideDisabled, setHideDisabled] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState('');

  useEffect(() => {
    setDraftAllAvailableColumns(allAvailableColumns);
  }, [allAvailableColumns]);

  const onDragEnd = useCallback((result: DropResult) => {
    const { destination, source } = result;
    if (
      source.droppableId === 'selected' &&
      destination?.droppableId === 'selected' &&
      source.index !== destination.index
    ) {
      setDraftAllAvailableColumns((columns) => {
        const newItems = moveItem(columns, source.index, destination.index);

        return newItems;
      });
      return;
    }
  }, []);

  const toggleHideDisabled = useCallback(() => {
    if (hideDisabled) {
      const selectedItems: ColumnItem[] = [];
      const nonSelectedItems: ColumnItem[] = [];

      draftAllAvailableColumns.forEach((col) => {
        if (col.isSelected) {
          selectedItems.push(col);
        } else {
          nonSelectedItems.push(col);
        }
      });

      nonSelectedItems.sort((a, b) => a.columnName.localeCompare(b.columnName));
      setDraftAllAvailableColumns(() => [...selectedItems, ...nonSelectedItems]);

      setHideDisabled(false);
    } else {
      setHideDisabled(true);
    }
  }, [draftAllAvailableColumns, hideDisabled]);

  const handleSelectedItemChange = useCallback((columnName, checked) => {
    setDraftAllAvailableColumns((columns) =>
      columns.map((col) => {
        if (columnName === col.columnName) {
          return {
            columnName,
            isSelected: checked,
          };
        }
        return col;
      })
    );
  }, []);

  const reset = () => {
    setFilterString('');
    setErrorMessage('');
    setHideDisabled(false);

    onRequestClose();
  };

  const handleSave = () => {
    if (!draftAllAvailableColumns.find((col) => col.isSelected)) {
      setErrorMessage(tn('all_columns_disabled'));
      return;
    }
    onRequestSave(draftAllAvailableColumns);
    reset();
  };

  const handleClose = () => {
    setDraftAllAvailableColumns(allAvailableColumns);
    reset();
  };

  const moveTo = useCallback((currentIndex: number, destinationIndex: number) => {
    setDraftAllAvailableColumns((columns) => {
      const newItems = moveItem(columns, currentIndex, destinationIndex);
      return newItems;
    });
  }, []);

  return (
    <Modal
      visible={isOpen}
      title={tn('title')}
      onCancel={handleClose}
      onOk={handleSave}
      width={631}
      wrapClassName="configure-table-columns"
      footer={
        <HStack justify="end">
          <Button key="cancel" onClick={handleClose}>
            {tc('cancel')}
          </Button>
          <Button key="ok" type="primary" onClick={handleSave}>
            {tc('save')}
          </Button>
        </HStack>
      }>
      <InlineMessage type="error" title={errorMessage}>
        {errorMessage}
      </InlineMessage>

      <div className="top_bar">
        <SearchBox
          className="column-search-box"
          onChange={(event) => setFilterString(event.target.value)}
          placeholder={tc('search')}
          value={filterString}
        />

        <div className="action_buttons">
          <Button
            disabled={!draftAllAvailableColumns.find((col) => !col.isSelected)}
            onClick={() => {
              setDraftAllAvailableColumns((columns) => columns.map((col) => ({ ...col, isSelected: true })));
            }}>
            {tn('enable_all')}
          </Button>
          <Button
            disabled={!draftAllAvailableColumns.find((col) => col.isSelected)}
            onClick={() => {
              setDraftAllAvailableColumns((columns) => columns.map((col) => ({ ...col, isSelected: false })));
            }}>
            {tn('disable_all')}
          </Button>
          <Button onClick={toggleHideDisabled}>{hideDisabled ? tn('show_disabled') : tn('hide_disabled')}</Button>
        </div>
      </div>
      <br />
      <DragDropContext onDragEnd={onDragEnd}>
        <ColumnList
          id="selected"
          allItems={draftAllAvailableColumns}
          dataTypeForColumn={dataTypeForColumn}
          labelForColumn={labelForColumn}
          filterString={filterString}
          hideDisabled={hideDisabled}
          handleSelectedItemChange={handleSelectedItemChange}
          moveTo={moveTo}
          emptyColumnContent={tn('no_selected_columns')}
        />
      </DragDropContext>
    </Modal>
  );
};

type UseConfigurableColumnsResult = [
  React.FC<ConfigureColumnsModalProps>,
  ConfigureColumnsModalProps,
  {
    isOpen: boolean;
    openModal: () => void;
    closeModal: () => void;
    toggleModal: () => void;
  }
];

/** convenience hook around the modal so we don't need to track state, etc in parent
 *
 */
const useConfigurableColumns = ({
  onRequestSave,
  allAvailableColumns,
  labelForColumn,
  dataTypeForColumn,
}: Omit<ConfigureColumnsModalProps, 'onRequestClose' | 'isOpen'>): UseConfigurableColumnsResult => {
  const [isOpen, setIsOpen] = useState(false);

  const openModal = () => setIsOpen(true);
  const closeModal = () => setIsOpen(false);
  const toggleModal = () => setIsOpen((prev) => !prev);

  const modalProps = {
    isOpen,
    onRequestSave,
    onRequestClose: closeModal,
    allAvailableColumns,
    dataTypeForColumn,
    labelForColumn,
  };

  return [
    ConfigureColumnsModal,
    modalProps,
    useMemo(
      () => ({
        isOpen,
        openModal,
        closeModal,
        toggleModal,
      }),
      [isOpen]
    ),
  ];
};

const mergeConfiguredAndDefaultColumns = (configuredColumns: ColumnItem[] = [], defaultColumns: ColumnItem[] = []) => {
  const configuredMap = new Map();
  configuredColumns.forEach((col) => configuredMap.set(col.columnName, col));

  const columns = [...configuredColumns];

  defaultColumns.forEach((defaultCol) => {
    if (!configuredMap.has(defaultCol.columnName)) {
      columns.push(defaultCol);
    }
  });
  return columns;
};

export default ConfigureColumnsModal;
export { useConfigurableColumns, mergeConfiguredAndDefaultColumns };
