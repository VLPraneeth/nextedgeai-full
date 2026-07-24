import { Spin } from 'antd';
import { useEffect } from 'react';

import { getAxisGraphSeries } from 'components/vizer/utils/useHighchartsAxisGraph';
import { getPieSeries } from 'components/vizer/utils/usePieVizer';
import { VizerGraphColors } from 'components/vizer/utils/VizerGraphColors';
import { usePreviewDataCardMutation } from 'store/insights-studio';
import {
  CategoryValue,
  DataCardData,
  DataCardVizConfig,
  Dataset,
  UnsavedDataCard,
  VizType,
} from 'store/insights-studio/types';
import { tNamespaced } from 'utils/i18nUtil';
import { DeepPartial } from 'utils/TypeUtils';

import { DataCardError } from '../../data-card-error/DataCardError';
import { ColorPickerGrid } from './ColorPickerGrid';
import { hasConfiguration } from './DataCardPreview';

const tn = tNamespaced('InsightsStudio');

export function CategoryColorPicker({
  dataset,
  dataCard,
  vizType,
  handleColorChange,
  setInitialValues,
}: {
  vizType?: VizType;
  dataCard?: UnsavedDataCard;
  dataset?: DeepPartial<Dataset>;
  handleColorChange: (categoryValue: CategoryValue) => void;
  setInitialValues: (categoryValues: CategoryValue[]) => void;
}) {
  const [previewDataCard, { data: dataCardPreview, isLoading }] = usePreviewDataCardMutation();

  useEffect(() => {
    const fetchPreview = () => {
      if (!dataCard || !dataset) {
        return;
      }
      previewDataCard({ datacard: dataCard, dataset });
    };
    fetchPreview();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (dataCardPreview?.contents?.data && dataCard?.contents?.configuration) {
      const values = getValues(dataCard?.contents?.configuration, dataCardPreview?.contents?.data, vizType);
      setInitialValues(values);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dataCardPreview]);

  if (isLoading) {
    return <Spin className="spinner" />;
  }

  if (!dataCard?.contents?.configuration || !dataset) {
    return null;
  }

  if (!hasConfiguration(dataCard?.contents?.configuration)) {
    return <DataCardError error={{ title: tn('incomplete_config_title'), body: '' }} />;
  }

  if (!dataCardPreview?.contents?.data?.rows || dataCardPreview.contents.data.rows.length === 0) {
    return <DataCardError error={{ title: tn('Vizer.no_data_title'), body: '' }} />;
  }

  const values = getValues(dataCard.contents.configuration, dataCardPreview.contents.data, vizType);

  return (
    <div>
      {values.map((value) => {
        return (
          <ColorPickerGrid
            onChange={(newColor) => {
              handleColorChange({ name: value.name, color: newColor });
            }}
            color={value.color || ''}
            label={value.name}
          />
        );
      })}
    </div>
  );
}

function getValues(dataCardConfig: DataCardVizConfig, data: DataCardData, vizType?: VizType): CategoryValue[] {
  switch (vizType) {
    case 'PIE': {
      return VizerGraphColors.assignColorsToCategories(
        getPieSeries({ configuration: dataCardConfig, data }).categories
      );
    }
    case 'BAR':
    case 'COLUMN':
    case 'LINE': {
      return VizerGraphColors.assignColorsToCategories(getAxisGraphSeries(data, dataCardConfig));
    }
    default: {
      return [];
    }
  }
}
