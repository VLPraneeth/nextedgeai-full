//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { useState, useEffect } from 'react';

import DrawerPanel from 'components/DrawerPanel';
import { withI18n, useI18nContext } from 'components/I18nProvider';
import { useLazyGetDatasetQuery } from 'store/insights-studio';
import { Dataset } from 'store/insights-studio/types';

import { useDatasetAuthoringContext } from '../context/DatasetAuthoringContext';
import DataSetContent from './DatasetContent';

const DataSetWizard = () => {
  const { tn } = useI18nContext();
  const {
    showDatasetWizard,
    setShowDatasetWizard,
    selectedDatasetId,
    setSelectedDatasetId,
  } = useDatasetAuthoringContext();

  const [dataset, setDataset] = useState<Dataset>();

  const [fetchDataset] = useLazyGetDatasetQuery({
    selectFromResult: (result) => {
      if (result.isSuccess && !result.isFetching && selectedDatasetId && !dataset) {
        setDataset(result.data);
      }
    },
  });

  const closeAndReset = () => {
    setSelectedDatasetId(null);
    setDataset(undefined);
    setShowDatasetWizard(false);
  };

  useEffect(() => {
    setDataset(undefined);
    if (selectedDatasetId && showDatasetWizard) {
      fetchDataset({ datasetId: selectedDatasetId });
    }
  }, [selectedDatasetId, fetchDataset, showDatasetWizard]);

  return (
    <DrawerPanel
      className="synri-config-full-content"
      keyboard={false}
      maskClosable={false}
      destroyOnClose
      noPadding
      onClose={closeAndReset}
      title={dataset?.displayName ? tn('edit_dataset_title', { name: dataset?.displayName }) : tn('no_datasets_title')}
      visible={showDatasetWizard}
      width="full">
      {(dataset || !selectedDatasetId) && <DataSetContent dataset={dataset} close={closeAndReset} />}
    </DrawerPanel>
  );
};

export default withI18n(DataSetWizard, 'Dataset');
