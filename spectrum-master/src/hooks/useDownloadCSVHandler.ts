import { endsWith } from 'lodash';
import { unparse } from 'papaparse';
import { useState } from 'react';

export const useDownloadCSVHandler = () => {
  const [isCreatingCSV, setIsCreatingCSV] = useState(false);

  const fixCSVName = (csvName: string) => {
    let modifiedName = csvName;
    modifiedName = modifiedName.trim();

    if (!endsWith(modifiedName, '.csv')) {
      modifiedName += '.csv';
    }

    return modifiedName;
  };

  const handleDownloadCSV = (csvData: Record<string, string>[], csvName: string) => {
    setIsCreatingCSV(true);
    const csv = unparse(csvData);

    const csvBlob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const csvURL = window.URL.createObjectURL(csvBlob);

    const link = document.createElement('a');
    link.href = csvURL;
    link.setAttribute('download', fixCSVName(csvName));
    link.click();
    setIsCreatingCSV(false);
  };

  return {
    isCreatingCSV,
    handleDownloadCSV,
  };
};
