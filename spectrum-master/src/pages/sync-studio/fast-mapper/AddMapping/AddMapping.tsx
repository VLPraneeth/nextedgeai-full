import { Button } from 'antd';
import { useState, Dispatch, SetStateAction, ReactNode, useEffect, useCallback } from 'react';

import Spinner from 'components/Spinner';
import { Mapping } from 'store/fast-mapper';
import { tNamespaced, tc } from 'utils/i18nUtil';

import { AutoMapContextProvider } from '../AutoMap/AutoMap.context';
import { FastMapperMode, useFastMapper } from '../FastMapperModal';
import { Mapper } from '../Mapper';
import { useAddMapping } from './AddMapping.hooks';

export interface AddMappingProps {
  onChange: (values: Mapping[]) => void;
  switchToBrowse: () => void;
  setChildFooter: Dispatch<SetStateAction<ReactNode>>;
}

const tn = tNamespaced('AddMapping');

export var AddMapping = ({ onChange, setChildFooter }: AddMappingProps) => {
  const { visible } = useFastMapper();
  const { isSaving, validateAndSave } = useAddMapping();

  const [values, setValues] = useState<Mapping[]>([]);

  const handleMapFields = useCallback(() => {
    validateAndSave(values);
  }, [validateAndSave, values]);

  useEffect(() => {
    if (visible) {
      setChildFooter(
        <Button className="add-mapping__button--primary" type="primary" onClick={handleMapFields} disabled={isSaving}>
          {isSaving && <Spinner className="add-mapping__button-spinner" />}
          {isSaving ? tc('saving') : tn('save_mappings')}
        </Button>
      );
    }

    // Unmount the button from the footer
    return () => {
      setChildFooter(null);
    };
  }, [isSaving, handleMapFields, setChildFooter, visible]);

  return (
    <AutoMapContextProvider>
      <Mapper mode={FastMapperMode.ADD} onChange={onChange} setValues={setValues} />
    </AutoMapContextProvider>
  );
};
