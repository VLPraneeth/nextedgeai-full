import { capitalize } from 'lodash';

import { CategoryValue, DataColumn } from 'store/insights-studio/types';
import { hexToHSL } from 'utils/ColorUtil';

export interface ShadeMap {
  [key: number | string]: string;
}

export interface LegacyColorMap {
  [key: string]: ShadeMap;
}

export const legacyColorMap: LegacyColorMap = {
  red: {
    100: '#de9a7d', // Red Lighter
    300: '#d78563', // Red Light
    500: '#ca5d2f', // Red
    700: '#b15129', // Red Dark
    900: '#7e3a1d', // Red Darker
  },
  orange: {
    100: '#f0c694', // Orange Lighter
    300: '#e9aa5f', // Orange Light
    500: '#e28d29', // Orange
    700: '#a96a1f', // Orange Dark
    900: '#714715', // Orange Darker
  },
  yellow: {
    100: '#f7e0b9', // Yellow Lighter
    300: '#f3d095', // Yellow Light
    500: '#efc072', // Yellow
    700: '#b39056', // Yellow Dark
    900: '#776039', // Yellow Darker
  },
  green: {
    100: '#b2d7b6', // Green Lighter
    300: '#8cc492', // Green Light
    500: '#65b06d', // Green
    700: '#4c8452', // Green Dark
    900: '#335837', // Green Darker
  },
  teal: {
    100: '#a4d7dc', // Teal Lighter
    300: '#77c3cb', // Teal Light
    500: '#49afb9', // Teal
    700: '#37838b', // Teal Dark
    900: '#25575c', // Teal Darker
  },
  blue: {
    100: '#9ec8f5', // Blue Lighter
    300: '#6eacf0', // Blue Light
    500: '#3d90eb', // Blue
    700: '#2e6cb0', // Blue Dark
    900: '#1e4875', // Blue Darker
  },
  purple: {
    100: '#b1b5e3', // Purple Lighter
    300: '#8991d4', // Purple Light
    500: '#626cc6', // Purple
    700: '#4a5195', // Purple Dark
    900: '#313663', // Purple Darker
  },
  gray: {
    100: '#dedede', // Gray Lighter
    300: '#cdcdcd', // Gray Light
    500: '#bcbcbc', // Gray
    700: '#8d8d8d', // Gray Dark
    900: '#5e5e5e', // Gray Darker;
  },
};

export const VizerGraphColors = {
  legacyColorTheme(color?: keyof typeof legacyColorMap) {
    if (!color) {
      color = 'purple';
    }
    return [
      legacyColorMap[color]?.[500],
      legacyColorMap[color]?.[100],
      legacyColorMap[color]?.[900],
      legacyColorMap[color]?.[300],
      legacyColorMap[color]?.[700],
    ];
  },
  getDefaultColor(color?: string) {
    if (!color) {
      color = chartColors[0].color;
    }
    // Support legacy color theme
    if (!color.startsWith('#')) {
      return {
        color: legacyColorMap[color]?.[500],
        name: capitalize(color),
      };
    }

    return {
      name: color,
      color,
    };
  },
  getShades(color: string | undefined, numberOfShades: number) {
    if (!color) {
      color = chartColors[0].color;
    }
    // Support legacy color theme
    if (!color.startsWith('#')) {
      color = legacyColorMap[color][900];
    }
    const baseColor = hexToHSL(color);
    const colors = [];
    const lightnessStep = 100 / (numberOfShades + 1);
    for (let i = 0; i < numberOfShades; i++) {
      const lightness = (i + 1) * lightnessStep;
      const hslColor = `hsl(${baseColor.h}, ${baseColor.s}%, ${lightness}%)`;
      colors.push(hslColor);
    }
    return colors;
  },
  getColorFromIndex(index: number) {
    return chartColors[index % chartColors.length].color;
  },
  getNextColor(color: string) {
    const index = chartColors.findIndex((chartColor) => color === chartColor.color);

    const nextIndex = (index + 1) % chartColors.length;
    return chartColors[nextIndex].color;
  },
  assignColorsToCategories(categoryValues?: CategoryValue[]): CategoryValue[] {
    const usedColors = new Set<string>();
    const uniqueItems: CategoryValue[] = [];
    const encounteredNames = new Set<string>();

    let colorIndex = 0;

    categoryValues?.forEach((item) => {
      const name = item.name || '';
      if (!encounteredNames.has(name)) {
        encounteredNames.add(name);
        if (!item.color) {
          while (usedColors.has(chartColors[colorIndex].color)) {
            colorIndex = (colorIndex + 1) % chartColors.length;
          }
          item.color = chartColors[colorIndex].color;
          usedColors.add(chartColors[colorIndex].color);
          colorIndex = (colorIndex + 1) % chartColors.length;
        } else {
          usedColors.add(item.color);
        }
        uniqueItems.push(item);
      }
    });

    return uniqueItems;
  },
  metricDefaultColor(column: DataColumn | undefined) {
    return column?.color || chartColors[0].color;
  },
};

export const chartColorGrays = [
  { color: '#7B7B7B', name: 'Dark Grey 2' },
  { color: '#999999', name: 'Dark Grey 1' },
  { color: '#D9D9D9', name: 'Grey' },
  { color: '#DBDBDB', name: 'Light Grey 1' },
  { color: '#D6D6D6', name: 'Light Grey 2' },
];

export const chartColors = [
  { color: '#FB8072', name: 'Red' },
  { color: '#FDB462', name: 'Orange' },
  { color: '#FFFF70', name: 'Yellow' },
  { color: '#B3DE69', name: 'Green' },
  { color: '#8DD3C7', name: 'Teal' },
  { color: '#80B1D3', name: 'Blue' },
  { color: '#BC80BD', name: 'Violet' },
  { color: '#FCC5E1', name: 'Pink' },
  { color: '#BEBADA', name: 'Lilac' },
  { color: '#FDB3AA', name: 'Light Red 2' },
  { color: '#FDD6AA', name: 'Light Orange 2' },
  { color: '#FDFDAA', name: 'Light Yellow 2' },
  { color: '#DFFDAA', name: 'Light Green 2' },
  { color: '#ACFBEE', name: 'Light Teal 2' },
  { color: '#AADAFD', name: 'Light Blue 2' },
  { color: '#EBBCEB', name: 'Light Violet 2' },
  { color: '#F8AFD5', name: 'Light Pink 2' },
  { color: '#B7ACFB', name: 'Light Lilac 2' },
  { color: '#FC998E', name: 'Light Red 1' },
  { color: '#FCC98E', name: 'Light Orange 1' },
  { color: '#FCFC8E', name: 'Light Yellow 1' },
  { color: '#D4FC8E', name: 'Light Green 1' },
  { color: '#97F3E4', name: 'Light Teal 1' },
  { color: '#91CEF9', name: 'Light Blue 1' },
  { color: '#E7A2E8', name: 'Light Violet 1' },
  { color: '#F694C7', name: 'Light Pink 1' },
  { color: '#9F91F9', name: 'Light Lilac 1' },
  { color: '#D6685C', name: 'Dark Red 1' },

  { color: '#E29E4F', name: 'Dark Orange 1' },
  { color: '#E0E051', name: 'Dark Yellow 1' },
  { color: '#A9D65C', name: 'Dark Green 1' },
  { color: '#64CEBC', name: 'Dark Teal 1' },
  { color: '#5CA3D6', name: 'Dark Blue 1' },
  { color: '#C769C9', name: 'Dark Violet 1' },
  { color: '#D65C9B', name: 'Dark Pink 1' },
  { color: '#6C5CD6', name: 'Dark Lilac 1' },
  { color: '#B15045', name: 'Dark Red 2' },
  { color: '#C47F31', name: 'Dark Orange 2' },
  { color: '#CBCB2B', name: 'Dark Yellow 2' },
  { color: '#8CBB3B', name: 'Dark Green 2' },
  { color: '#3BBBA6', name: 'Dark Teal 2' },
  { color: '#3D85B8', name: 'Dark Blue 2' },
  { color: '#AF45B1', name: 'Dark Violet 2' },
  { color: '#B1457D', name: 'Dark Pink 2' },
  { color: '#5345B1', name: 'Dark Lilac 2' },
];

export const darkColors = [
  chartColors[35].color,
  chartColors[36].color,
  chartColors[42].color,
  chartColors[43].color,
  chartColors[44].color,
];

export const baseChartColors = chartColors.slice(0, 9);
