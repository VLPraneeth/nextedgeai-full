import { GridApi } from 'ag-grid-community';
import { Button } from 'antd';
import { useEffect, useState, Dispatch, SetStateAction, ReactNode, useCallback } from 'react';

import Spinner from 'components/Spinner';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { EMPTY_ARRAY } from 'store/constants';
import { Mapping } from 'store/fast-mapper';
import { selectMappings } from 'store/fast-mapper/selectors';
import { resetBrowseMappingModal } from 'store/fast-mapper/slice';
import { tNamespaced } from 'utils/i18nUtil';

import { ExportFieldMappingsButton } from '../ExportFieldMappingsButton';
import { FastMapperMode, useFastMapper } from '../FastMapperModal';
import { Mapper } from '../Mapper';
import { EditedMapping } from '../types';
import { useBrowseMapping } from './BrowseMapping.hooks';

const tn = tNamespaced('BrowseMapping');

export interface BrowseMappingProps {
  onChange: (values: EditedMapping[]) => void;
  switchToAdd?: () => void;
  setChildFooter: Dispatch<SetStateAction<ReactNode>>;
}

export const BrowseMapping = ({ onChange, switchToAdd, setChildFooter }: BrowseMappingProps) => {
  const { visible } = useFastMapper();
  const { isRemapping, mappings, validateAndRemapFields, refreshServerMappings } = useBrowseMapping();

  const serverMappings = useEnhancedSelector(selectMappings) ?? EMPTY_ARRAY;

  const [values, setValues] = useState<Mapping[]>([]);
  const [editedValues, setEditedValues] = useState<EditedMapping[]>([]);
  const [searchValue, setSearchValue] = useState('');
  const [gridApi, setGridApi] = useState<GridApi>();
  const [gridUpdatedTrigger, setGridUpdatedTrigger] = useState(false);
  const dispatch = useEnhancedDispatch();

  useEffect(() => {
    onChange(editedValues);
  }, [editedValues, onChange]);

  const handleFilter = (value: string) => {
    dispatch(resetBrowseMappingModal());
    setSearchValue(value);
    gridApi && gridApi.setQuickFilter(value);
  };

  const handleRemapFields = useCallback(async () => {
    const result = await validateAndRemapFields(values, editedValues);
    if (result && !('error' in result) && result.payload.success) {
      setEditedValues([]);
      refreshServerMappings();
    }
  }, [editedValues, refreshServerMappings, validateAndRemapFields, values]);

  useEffect(() => {
    if (visible) {
      setChildFooter(
        <Button
          className="mapper__header-button"
          onClick={handleRemapFields}
          type="primary"
          disabled={!editedValues?.length}>
          {isRemapping && <Spinner className="mapper__button-spinner" />}
          {isRemapping ? tn('remapping') : tn('remap_fields')}
        </Button>
      );
    }

    // Unmount the button from the footer
    return () => {
      setChildFooter(null);
    };
  }, [editedValues?.length, handleRemapFields, isRemapping, setChildFooter, visible]);

  const header = (
    <>
      <ExportFieldMappingsButton
        className="mapper__header-button"
        disabled={!!editedValues?.length}
        mappings={serverMappings}
        gridApi={gridApi}
        gridUpdatedTrigger={gridUpdatedTrigger}
        searchValue={searchValue}
      />
      <Button onClick={switchToAdd} type="primary">
        {tn('add_mappings')}
      </Button>
    </>
  );

  return (
    <Mapper
      header={header}
      initialMappings={mappings}
      switchToAdd={switchToAdd}
      mode={FastMapperMode.BROWSE}
      onFilter={handleFilter}
      setValues={setValues}
      setEditedValues={setEditedValues}
      setGridApi={setGridApi}
      setGridUpdatedTrigger={setGridUpdatedTrigger}
    />
  );
};
