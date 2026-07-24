import Highcharts, { Chart, ChartEventsOptions, Point, SeriesOptionsType, TooltipOptions } from 'highcharts';
import { useMemo } from 'react';

import { DataCardData, DataCardVizConfig, DataRow } from 'store/insights-studio/types';

import { formatValue } from './VizerDisplayFormatter';
import { VizerGraphColors, chartColorGrays } from './VizerGraphColors';

export interface Params {
  configuration: DataCardVizConfig;
  data: DataCardData;
}

export interface ReturnTypes {
  highchartSeries: SeriesOptionsType[];
  tooltip: TooltipOptions;
  events: ChartEventsOptions;
}

/**
 * Custom hook to convert Syncari's API response to Highcharts-compatible
 * data format.
 *
 * For more info see
 * - https://api.highcharts.com/highcharts/series.pie
 */
export const usePieVizer = ({ configuration, data }: Params): ReturnTypes => {
  const tooltip = useMemo(() => {
    const displayFormat = configuration.value?.displayFormat;
    return {
      pointFormatter(this: Point): string {
        const value = this.y && formatValue(displayFormat, this.y, this?.percentage);
        // Highchart need a markup in string
        return `<span">&bull;${this.series.name}</span>: <b>${value}</b><br />`;
      },
      shared: true,
    };
  }, [configuration.value?.displayFormat]);

  const events = useMemo(() => {
    return {
      load(this: Chart) {
        const overlaps = findOverlaps(this.series?.[0]?.points, this.series?.[1]?.points);
        overlaps.forEach(({ subcategory, xOverlap, yOverlap }) => {
          subcategory.update({ dataLabels: { x: xOverlap, y: yOverlap } }, false);
        });

        this.redraw();
      },
    };
  }, []);

  return {
    highchartSeries: getPieSeries({ configuration, data }).series,
    tooltip,
    events,
  };
};

export function getPieSeries({ configuration, data }: Params) {
  const categoryColumnName = configuration?.category?.name || '';
  const categoryDisplayName = configuration.category?.displayName || categoryColumnName;
  const subCategoryColumnName = configuration.subCategory?.name || '';
  const subCategoryDisplayName = configuration.subCategory?.displayName || subCategoryColumnName;
  const valueColumnName = configuration.value?.name || '';

  const minimumValue = configuration.minimumValue?.value;
  const minimumValueLabel = configuration.minimumValue?.label || 'Others';
  const applyToSubCategories = configuration.minimumValue?.applyToSubCategories;

  // Group data by category column
  const groupedData: Record<string, { rows: DataRow[]; isOtherCategory: boolean }> = {};
  data.rows
    .slice()
    .sort((a, b) => {
      const valueA = String(a[categoryColumnName]).toLowerCase();
      const valueB = String(b[categoryColumnName]).toLowerCase();

      if (valueA < valueB) {
        return -1;
      }
      if (valueA > valueB) {
        return 1;
      }
      return 0;
    })
    .forEach((item) => {
      const category = item[categoryColumnName];
      if (!groupedData[category]) {
        groupedData[category] = {
          rows: [],
          isOtherCategory: false,
        };
      }
      groupedData[category].rows.push(item);
    });

  // If minimum value is set, group small values into 'Others' category.
  if (minimumValue) {
    const othersCategory: DataRow[] = [];
    Object.keys(groupedData).forEach((category) => {
      const categoryValue = groupedData[category].rows.reduce((total, item) => total + item[valueColumnName], 0);
      if (categoryValue < minimumValue) {
        othersCategory.push(...groupedData[category].rows);
        delete groupedData[category];
      }
    });
    if (othersCategory.length > 0) {
      groupedData[minimumValueLabel] = {
        rows: othersCategory,
        isOtherCategory: true,
      };
    }
  }

  // Create array of categories with total values
  const categories = Object.entries(groupedData).map(([category, items], index) => ({
    name: category,
    color:
      configuration.categoryValues?.find((val) => val.name === category)?.color ||
      (items.isOtherCategory ? chartColorGrays[0].color : VizerGraphColors.getColorFromIndex(index)),
    y: items.rows.reduce((total, item) => total + item[valueColumnName], 0),
  }));

  // Create array of subcategories with values and colors derived from category's color
  const subCategories = Object.entries(groupedData).flatMap(([category, items]) => {
    const categoryIndex = categories.findIndex((item) => item.name === category);
    const categoryColor = categories[categoryIndex].color;
    let subCategoryArray = items.rows.map((item) => ({
      name: item[subCategoryColumnName],
      y: item[valueColumnName],
      color: Highcharts.color(categoryColor).brighten(0.2).get(),
    }));

    // If minimum value is set, group small values into 'Others' subcategory.
    if (minimumValue && applyToSubCategories) {
      const othersSubCategory: {
        name: string;
        y: number;
        color: Highcharts.ColorType;
      }[] = [];
      subCategoryArray = subCategoryArray.filter((subCategory) => {
        if (subCategory.y < minimumValue) {
          othersSubCategory.push(subCategory);
          return false;
        }
        return true;
      });

      if (othersSubCategory.length > 0) {
        subCategoryArray.push({
          name: minimumValueLabel,
          y: othersSubCategory.reduce((total, subCategory) => total + subCategory.y, 0),
          color: Highcharts.color(categoryColor).brighten(0.2).get(),
        });
      }
    }

    return subCategoryArray;
  });

  const transformedSeries: Highcharts.SeriesOptionsType[] = [
    {
      name: categoryDisplayName,
      data: categories,
      size: '60%',
      type: 'pie',
    },
  ];
  subCategoryColumnName &&
    transformedSeries.push({
      name: subCategoryDisplayName,
      data: subCategories,
      size: '80%',
      innerSize: '60%',
      type: 'pie',
    });

  return {
    series: transformedSeries,
    categories,
  };
}

function findOverlaps(categories: any, subCategories: any) {
  if (!categories || !subCategories) {
    return [];
  }

  const overlapping = [];

  // Loop through categories and subcategories and find overlapping labels
  for (let i = 0; i < categories.length; i++) {
    const category = categories[i];

    for (let j = 0; j < subCategories.length; j++) {
      const subcategory = subCategories[j];

      const catLabel = category.dataLabel;
      const subcatLabel = subcategory.dataLabel;

      if (!catLabel || !subcatLabel) {
        continue;
      }

      const categoryDimensions = {
        x: category.labelPosition.final.x,
        y: category.labelPosition.final.y,
        width: catLabel.bBox.width,
        height: catLabel.bBox.height,
      };
      const subCategoryDimensions = {
        x: subcategory.labelPosition.final.x,
        y: subcategory.labelPosition.final.y,
        width: subcatLabel.bBox.width,
        height: subcatLabel.bBox.height,
      };

      if (
        categoryDimensions.x + categoryDimensions.width >= subCategoryDimensions.x &&
        subCategoryDimensions.x + subCategoryDimensions.width >= categoryDimensions.x
      ) {
        if (
          categoryDimensions.y + categoryDimensions.height >= subCategoryDimensions.y &&
          subCategoryDimensions.y + subCategoryDimensions.height >= categoryDimensions.y
        ) {
          const xOverlap = Math.min(
            categoryDimensions.x + categoryDimensions.width - subCategoryDimensions.x,
            subCategoryDimensions.x + subCategoryDimensions.width - categoryDimensions.x
          );
          const yOverlap = Math.min(
            categoryDimensions.y + categoryDimensions.height - subCategoryDimensions.y,
            subCategoryDimensions.y + subCategoryDimensions.height - categoryDimensions.y
          );
          overlapping.push({
            category,
            subcategory,
            cname: category.name,
            scname: subcategory.name,
            xOverlap,
            yOverlap,
          });
        }
      }
    }
  }

  return overlapping;
}
