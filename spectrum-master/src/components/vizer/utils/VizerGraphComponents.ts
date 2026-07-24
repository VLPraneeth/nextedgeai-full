import { HighchartsAxisGraph } from '../graph-components/HighchartsAxisGraph';
import { HighchartsFunnelGraph } from '../graph-components/HighchartsFunnelGraph';
import { HighchartsGaugeGraph } from '../graph-components/HighChartsGaugeGraph';
import { HighchartsPieGraph } from '../graph-components/HighchartsPieGraph';
import { VizerMetric } from '../graph-components/VizerMetric';
import { VizerTable } from '../graph-components/VizerTable';
import { VizerComponent } from '../types';

export const VizerGraphMap: Record<string, VizerComponent> = {
  bar: HighchartsAxisGraph,
  column: HighchartsAxisGraph,
  line: HighchartsAxisGraph,
  pie: HighchartsPieGraph,
  table: VizerTable,
  metric: VizerMetric,
  gauge: HighchartsGaugeGraph,
  funnel: HighchartsFunnelGraph,
} as const;
