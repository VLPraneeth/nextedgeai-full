import { useMemo, useState } from 'react';

import CollapsibleLineItem from 'components/CollapsibleLineItem';
import DataScoreBadge from 'components/data-fitness/DataScoreBadge';
import PieChart, { PieChartDatum } from 'components/datavis/charts/PieChart/PieChart';
import { HStack, Stack } from 'components/layout';
import { Text } from 'components/typography';
import { gaugeSegments, getSegmentForValue } from 'store/datascore';
import { capitalize } from 'utils/Fp';
import { tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('DataQualityStudio.EntityBreakdownPieChart');

// bind our datascore gauge segments to simplify the usage later
const getSegment = getSegmentForValue.bind(null, gaugeSegments);

const APPROX_ITEM_HEIGHT = 50;

export interface EntityBreakdownPieChartProps {
  data: PieChartDatum[];
}

const EntityBreakdownPieChart = ({ data }: EntityBreakdownPieChartProps) => {
  const [expandedSection, setExpandedSection] = useState(0);

  // group data by segment
  const groupedData = useMemo(() => {
    return data
      .slice()
      .sort((a, b) => b.value - a.value)
      .reduce((acc, item) => {
        const segment = getSegment(item.value);
        const rangeKey = `${segment.min}-${segment.max}`;

        if (!(rangeKey in acc)) {
          acc[rangeKey] = {
            ...segment,
            items: [],
          };
        }

        acc[rangeKey].items.push(item);
        return acc;
      }, {} as Record<string, ReturnType<typeof getSegment> & { items: any[] }>);
  }, [data]);

  // count of items in each segment + color
  const preparedPieData = useMemo(() => {
    return Object.entries(groupedData).map(([rangeKey, datum]) => ({
      id: rangeKey,
      label: rangeKey,
      value: datum.items.length,
      color: datum.color,
    }));
  }, [groupedData]);

  return (
    <PieChart
      data={preparedPieData}
      selectedPieSliceIdx={expandedSection}
      onClickPieSlice={(_, idx) => setExpandedSection(idx)}
      legend={preparedPieData.map((datum, idx) => {
        const { items, ...segment } = groupedData[datum.id] || {};

        return (
          <CollapsibleLineItem
            key={datum.id}
            title={segment.label || datum.label}
            expanded={expandedSection === idx}
            onToggle={(visible) => setExpandedSection(visible ? idx : -1)}
            leftTitleChildren={
              <DataScoreBadge score={segment.max} fontColorThreshold={150}>
                {tn('score_range', { min: segment.min, max: segment.max })}
              </DataScoreBadge>
            }
            rightTitleChildren={<span>{tn('entities_count', { count: datum.value })}</span>}
            contentMaxHeight={items.length * APPROX_ITEM_HEIGHT}>
            <div className="pie-chart-legend-line-items-wrapper">
              <Stack className="pie-chart-legend-line-item">
                {items.map((entity) => (
                  <HStack key={entity.id} spacing="xs">
                    <Text weight="bold" size="sm" className="entity-line-item-title">
                      {capitalize(entity.label)}
                    </Text>
                    <DataScoreBadge score={entity.value} fontColorThreshold={150} />
                  </HStack>
                ))}
              </Stack>
            </div>
          </CollapsibleLineItem>
        );
      })}
    />
  );
};

export default EntityBreakdownPieChart;
