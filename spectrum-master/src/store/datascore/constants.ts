import { colors } from 'utils/LessConstants';

export const unavailableDataScoreSegment = { color: colors.gray300 };

export const gaugeSegments = [
  {
    color: colors.red300,
    label: 'Poor',
    min: 0,
    max: 20,
  },
  {
    color: colors.orange600,
    label: 'Needs Improvement',
    min: 21,
    max: 40,
  },
  {
    color: colors.yellow500,
    label: 'Fair',
    min: 41,
    max: 60,
  },
  {
    color: colors.green250,
    label: 'Good',
    min: 61,
    max: 80,
  },
  {
    color: colors.green400,
    label: 'Excellent',
    min: 81,
    max: 100,
  },
];

export const noScoreSegment = {
  color: colors.gray400,
  label: 'No score',
  min: 0,
  max: 0,
};
