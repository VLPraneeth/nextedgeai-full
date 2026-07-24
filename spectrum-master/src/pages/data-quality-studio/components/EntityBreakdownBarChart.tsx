import { useCallback, useMemo, useState } from 'react';
import * as React from 'react';

import DataScoreBatteryMeter from 'components/data-fitness/DataScoreBatteryMeter';
import DataScoreLink, { getLinkItemsForEntity } from 'components/data-fitness/DataScoreLink';
import { PieChartDatum } from 'components/datavis/charts/PieChart/PieChart';
import { HStack } from 'components/layout';
import MouseFollower from 'components/MouseFollower';
import { Text, Truncation } from 'components/typography';
import { useEnhancedSelector } from 'hooks/redux';
import useDimensions from 'hooks/useDimensions';
import { useCurrentSyncStudioRootTab } from 'pages/sync-studio/entity/SyncStudioRootTabs';
import { gaugeSegments, getSegmentForValue } from 'store/datascore';
import { selectEntityApiNameMap } from 'store/entity/selectors';
import { Entity } from 'store/entity/types';
import { clamp } from 'utils/NumberUtil';

import './EntityBreakdownBarChart.less';

export interface EntityBreakdownChartProps {
  barHeightPx?: number;
  data: PieChartDatum[];
}

// bind our datascore gauge segments to simplify the usage later
const getSegment = getSegmentForValue.bind(null, gaugeSegments);

const VerticalReferenceLine = ({ children }: { children: string | number }) => {
  return (
    <div className="vertical-reference-line">
      <div className="label">{children}</div>
    </div>
  );
};

const SegmentLegend = ({ children, width }: { children: React.ReactNode; width: number }) => (
  <div className="segment-legend" style={{ width: `${width}%` }}>
    {children}
  </div>
);

type DataRowProps = {
  entity: Entity;
  rowHeight?: number;
  value: number;
};

const DataRow = ({ entity, rowHeight, value }: DataRowProps) => {
  const [mouseOver, setMouseOver] = useState(false);

  const { currentTab } = useCurrentSyncStudioRootTab();
  const linkItems = useMemo(() => getLinkItemsForEntity(entity.id, currentTab), [currentTab, entity.id]);

  const segment = getSegment(value);

  const handleMouseEnter = useCallback(() => setMouseOver(true), []);
  const handleMouseLeave = useCallback(() => setMouseOver(false), []);

  return (
    <div className="data-row">
      <HStack align="center" justify="space-between" spacing="xs" className="legend">
        <Truncation>
          <Text color="black" weight="bold">
            {entity.displayName}
          </Text>
        </Truncation>
        <HStack align="center" justify="end">
          {linkItems.map((link) => (
            <DataScoreLink key={link.label} {...link} />
          ))}
        </HStack>
      </HStack>
      <Bar
        color={segment.color}
        height={rowHeight}
        value={value}
        onMouseEnter={handleMouseEnter}
        onMouseLeave={handleMouseLeave}
      />

      {mouseOver && (
        <MouseFollower>
          <DataScoreBatteryMeter className="box-shadow-md" score={value} />
        </MouseFollower>
      )}
    </div>
  );
};

const DEFAULT_BAR_HEIGHT = 25;
const MIN_BAR_HEIGHT = 15;
const MAX_BAR_HEIGHT = 60;

type BarProps = {
  color: string;
  height?: number;
  value: number;
};

const Bar = ({ color, height = DEFAULT_BAR_HEIGHT, value, ...props }: BarProps & JSX.IntrinsicElements['div']) => {
  const barContainerStyle = useMemo(
    () => ({
      height: clamp(height, MIN_BAR_HEIGHT, MAX_BAR_HEIGHT),
    }),
    [height]
  );

  const barStyle = useMemo(
    () => ({
      backgroundColor: color,
      width: value ? `${value}%` : 3,
    }),
    [color, value]
  );

  return (
    <div className="bar-container" style={barContainerStyle} {...props}>
      <div className="bar" style={barStyle} />
    </div>
  );
};

// NOTE: If this changes you might need to adjust styling appropriately, the styling
// assumes that this array will include 0 and 100
const referenceLinePercentages = [0, ...gaugeSegments.map((segment) => segment.max)];
const referenceLines = referenceLinePercentages.map((position) => (
  <VerticalReferenceLine key={position}>{position}</VerticalReferenceLine>
));

// These are the Poor, Needs Improvement, … legend bars on the X Axis
const segmentLegends = gaugeSegments.map((segment) => (
  <SegmentLegend key={segment.label} width={100 / gaugeSegments.length}>
    <Truncation>
      <Text as="div" size="sm">
        {segment.label}
      </Text>
    </Truncation>
  </SegmentLegend>
));

const EntityBreakdownBarChart = ({ data }: EntityBreakdownChartProps) => {
  const [measureRef, dimensions] = useDimensions({ liveMeasure: true });
  const entityMap = useEnhancedSelector(selectEntityApiNameMap);

  const preparedData = useMemo(() => {
    /* prepare the data for display,
     * - slice so we can safely sort without mutating prop data
     * - sort high -> low
     * - slice off the first X items
     * - enhance with entity data
     */
    return data
      .slice()
      .sort((a, b) => b.value - a.value)
      .slice(0, 12)
      .map((datum, i) => {
        const entity = entityMap[datum.label] ?? {};

        return {
          ...datum,
          entity,
        };
      });
  }, [data, entityMap]);

  // Calculate the row height based on the number of items we're going to display
  const rowHeight = dimensions.height ? dimensions.height / preparedData.length : undefined;

  return (
    <div className="entity-breakdown-chart-container">
      <div className="entity-breakdown-chart-domain" ref={measureRef}>
        <div className="bars">
          {preparedData.map((datum) => (
            <DataRow key={datum.id} entity={datum.entity} value={datum.value} rowHeight={rowHeight} />
          ))}
        </div>
        <div className="reference-lines">{referenceLines}</div>
        <div className="axis-legend">{segmentLegends}</div>
      </div>
    </div>
  );
};

export default EntityBreakdownBarChart;
