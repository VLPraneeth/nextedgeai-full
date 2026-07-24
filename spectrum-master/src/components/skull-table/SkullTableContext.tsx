import { findIndex, forEach } from 'lodash';
import { createContext, ReactNode, useCallback, useContext, useEffect, useState } from 'react';

import { EMPTY_ARRAY, EMPTY_OBJECT } from 'store/constants';
import { replaceItem } from 'utils/ArrayUtil';

import { SkullTableInitialRowData } from './types';

interface SkullTableState {
  updateValue: (update: { id: string; fieldName: string; value: unknown }) => void;
  getFieldValue: (query: { id: string; fieldName: string }) => unknown;
  inputValue: unknown;
}

const SkullTableContext = createContext<SkullTableState>({
  inputValue: EMPTY_OBJECT,
  updateValue: (update) => {},
  getFieldValue: (query) => {},
});

export interface SkullTableContextProviderProps {
  initialData: Record<string, unknown>[];
  onChange: (inputValue: any) => void;
  children: ReactNode;
}

/**
 * Takes the server response for useDependsOnData and transforms the data into
 * the form values
 */
export const tranformSourceDataToFormData = (sourceData?: SkullTableInitialRowData[]): Record<string, unknown>[] => {
  if (!sourceData) {
    return EMPTY_ARRAY;
  }

  return sourceData.map((rowItem) => {
    const formData: Record<string, unknown> = {
      id: rowItem.id,
    };

    forEach(rowItem.fields, ({ value }, key) => {
      formData[key] = value;
    });

    return formData;
  });
};

export const SkullTableContextProvider = ({ children, initialData, onChange }: SkullTableContextProviderProps) => {
  const [inputValue, setInputValue] = useState(initialData);

  useEffect(() => {
    // If the form inputs change and the table data is updated we need to reset
    // the table with the new form data
    setInputValue(initialData);
  }, [initialData]);

  const updateValue = useCallback(
    ({ id, fieldName, value }: { id: string; fieldName: string; value: unknown }) => {
      const index = findIndex(inputValue, { id });
      const row = inputValue[index];

      const updatedInputValue = replaceItem(inputValue, index, { ...row, [fieldName]: value });

      setInputValue(updatedInputValue);
      onChange(updatedInputValue);
    },
    [inputValue, onChange]
  );

  const getFieldValue = useCallback(
    ({ id, fieldName }: { id: string; fieldName: string }) => {
      const row = inputValue.find((val) => val.id === id);
      return row?.[fieldName];
    },
    [inputValue]
  );

  return (
    <SkullTableContext.Provider
      value={{
        inputValue,
        updateValue,
        getFieldValue,
      }}>
      {children}
    </SkullTableContext.Provider>
  );
};

export const useSkullTableContext = () => {
  const { inputValue, updateValue, getFieldValue } = useContext(SkullTableContext);
  return {
    inputValue,
    updateValue,
    getFieldValue,
  };
};
