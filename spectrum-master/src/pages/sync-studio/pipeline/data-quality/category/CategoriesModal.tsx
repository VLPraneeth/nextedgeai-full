//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { ColDef, ColGroupDef, GridApi, GridReadyEvent } from 'ag-grid-community';
import { Button, Icon, message } from 'antd';
import cx from 'classnames';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { FetchBaseQueryError } from '@reduxjs/toolkit/dist/query';
import { SerializedError } from '@reduxjs/toolkit';

import AgTable, { ResizeColumnsCondition } from 'components/AgTable/AgTable';
import { HStack, Stack } from 'components/layout';
import Modal from 'components/Modal';
import { TimeCell } from 'pages/sync-studio/entity/PipelineDetailsTable/PipelineDetailsTable.renderers';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tc, tNamespaced } from 'utils/i18nUtil';

import { useDataQuality } from '../DataQuality.hooks';
import { useCategoriesContext } from './CategoriesTable.context';
import { CategoryActions, NameEditor } from './CategoriesTable.renderer';
import './CategoriesModal.scss';
import { DataQualityAction } from '../rules/DataQualityAction';
const tn = tNamespaced('DataQuality');

const CategoriesModal = () => {
  const [gridApi, setGridApi] = useState<GridApi | null>();
  const { navigateToDataQuality, categoriesMatch, editable } = useDataQuality();

  const { addCategory, isLoading, categories, saveCategories, categoriesError, hasChanges } = useCategoriesContext();

  useEffect(() => {
    if (!categoriesMatch?.entityId) {
      setGridApi(null);
    }
  }, [categoriesMatch?.entityId]);

  const handleStartCellEdit = useCallback((grid: GridApi, columnName: string, rowIndex: number) => {
    setTimeout(() => {
      if (grid) {
        grid.setFocusedCell(0, columnName);
        grid.startEditingCell({
          rowIndex,
          colKey: columnName,
        });
      }
    }, 200);
  }, []);

  const columns: (ColDef | ColGroupDef)[] = useMemo(() => {
    return [
      {
        headerName: tc('name'),
        field: 'name',
        resizable: true,
        editable: (params) => {
          return params.node.data?.type !== 'system';
        },
        cellEditorFramework: NameEditor,
      },
      {
        headerName: tc('last_changed'),
        field: 'updatedAt',
        cellRenderer: 'time',
        resizable: true,
      },
      {
        headerName: tc('type'),
        field: 'type',
        resizable: true,
      },
      {
        headerName: tc('changed_by'),
        field: 'updatedBy',
        resizable: true,
      },
      {
        headerName: tc('actions'),
        field: 'actions',
        minWidth: 70,
        maxWidth: 70,
        cellRenderer: 'actions',
        pinned: 'right',
        width: 70,
        resizable: false,
        cellRendererParams: {
          editHandler: (grid: GridApi, catId: string, rowIndex: number) => {
            handleStartCellEdit(grid, 'name', rowIndex);
          },
          editable,
        },
      },
    ];
  }, [handleStartCellEdit, editable]);

  const handleClose = useCallback(() => navigateToDataQuality(), [navigateToDataQuality]);

  const handleSave = useCallback(async () => {
    try {
      await saveCategories();
      handleClose();
    } catch (error) {
      const fetchError = error as FetchBaseQueryError;
      if (fetchError.data && typeof fetchError.data === 'object' && 'message' in fetchError.data) {
        message.error(fetchError.data.message as string);
      } else {
        message.error(
          getRtkQueryErrorMessage(error as FetchBaseQueryError | SerializedError, tn('error_creating_category'))
        );
      }
    }
  }, [saveCategories, handleClose]);

  const onGridReady = (event: GridReadyEvent) => {
    setGridApi(event.api);
  };

  const startAddCategory = useCallback(() => {
    addCategory?.();
    gridApi && handleStartCellEdit(gridApi, 'name', categories?.length || 0);
  }, [addCategory, categories?.length, gridApi, handleStartCellEdit]);
  return (
    <Modal
      title={tn('manage_categories')}
      centered
      className="manage-categories-modal"
      visible={!!categoriesMatch?.entityId}
      width="60%"
      onOk={handleSave}
      onCancel={handleClose}
      footer={
        <>
          <Button type={!editable ? 'primary' : undefined} onClick={handleClose}>
            {!editable ? tc('close') : tc('cancel')}
          </Button>
          {editable && (
            <Button type="primary" onClick={handleSave} disabled={!hasChanges}>
              {tc('save')}
            </Button>
          )}
        </>
      }
      destroyOnClose>
      <Stack className="manage-categories-modal__container" spacing="md" fill>
        <HStack justify="end">
          <DataQualityAction>
            <Button onClick={startAddCategory} disabled={!editable}>
              <Icon type="plus" />

              {tn('add_category')}
            </Button>
          </DataQualityAction>
        </HStack>
        <AgTable
          className={cx('manage-categories-modal__table', !categories?.length && 'empty')}
          domLayout="autoHeight"
          onGridReady={onGridReady}
          singleClickEdit
          frameworkComponents={catFrameworkComponents}
          loading={isLoading}
          columnDefs={columns}
          error={getRtkQueryErrorMessage(categoriesError)}
          rowData={categories}
          sizeColumnsToFit={ResizeColumnsCondition.WHEN_NARROWER}
          suppressCellSelection
          enableCellTextSelection
          colResizeDefault="shift"
          getRowNodeId={(data) => data.id}
        />
      </Stack>
    </Modal>
  );
};

export default CategoriesModal;

export const catFrameworkComponents = {
  actions: CategoryActions,
  time: TimeCell,
};
