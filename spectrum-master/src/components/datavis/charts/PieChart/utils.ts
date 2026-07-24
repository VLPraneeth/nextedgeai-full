import { arc as arcPath } from '@visx/shape';
import { PieArcDatum } from '@visx/shape/lib/shapes/Pie';

// finds center point of the outer edge of the pie slice
export const arcOuterCentroid = <Datum extends unknown>(outerRadius: number, arc: PieArcDatum<Datum>) => {
  const midAngle = (+arc.startAngle + +arc.endAngle) / 2 - Math.PI / 2;

  return [Math.cos(midAngle) * outerRadius, Math.sin(midAngle) * outerRadius];
};

// this is a helper that allows us to dynamically change radius configuration of the arc
// and not just the angles
//
// eg, for animating a change in pie slice radius
export const getArcPath = <Datum extends unknown>(path: Parameters<typeof arcPath>[0], arc: PieArcDatum<Datum>) =>
  arcPath(path)(arc);
