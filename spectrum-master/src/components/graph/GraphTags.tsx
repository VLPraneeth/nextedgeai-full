import { FONT_FAMILY } from 'components/graph/constants';
import { colors } from 'utils/LessConstants';

const TAG_HEIGHT = 18;
export const SPACE_BETWEEN_TAGS = 4;
const BORDER_RADIUS = 3;

const tagColorMap = {
  green: {
    background: colors.green150,
    text: colors.green600,
  },
  blue: {
    background: colors.blue250,
    text: colors.blue800,
  },
  orange: {
    background: colors.orange250,
    text: colors.orange800,
  },
  gray: {
    background: colors.gray250,
    text: colors.gray800,
  },
  red: {
    background: colors.red150,
    text: colors.red800,
  },
  purple: {
    background: colors.purple200,
    text: colors.purple600,
  },
} as const;

export type TagColors = keyof typeof tagColorMap;

export interface TagData {
  label: string;
  color: TagColors;
  tagWidth: number;
}

export interface AddTagsProps {
  group: any;
  x: number;
  y: number;
  tags: TagData[];
}

export const addTags = ({ group, x, y, tags }: AddTagsProps) => {
  let startingX = x;

  tags.forEach((tag) => {
    group.addShape('path', buildTag({ x: startingX, y, color: tag.color, tagWidth: tag.tagWidth }));

    group.addShape('text', {
      attrs: {
        text: tag.label,
        x: startingX + SPACE_BETWEEN_TAGS,
        y: y + SPACE_BETWEEN_TAGS,
        fontSize: 11,
        fontFamily: FONT_FAMILY,
        textAlign: 'start',
        textBaseline: 'top',
        fill: tagColorMap[tag.color].text,
        section: 'statusText',
      },
    });

    startingX = startingX + tag.tagWidth + SPACE_BETWEEN_TAGS;
  });

  return startingX;
};

export interface BuiltTagProps {
  x: number;
  y: number;
  color: TagColors;
  tagWidth: number;
}

export const buildTag = ({ x, y, tagWidth, color }: BuiltTagProps) => {
  return {
    attrs: {
      path: [
        ['M', x, y + BORDER_RADIUS], // Start - top left
        ['L', x, y + TAG_HEIGHT - BORDER_RADIUS], // Going down
        ['A', BORDER_RADIUS, BORDER_RADIUS, 0, 0, 0, x + BORDER_RADIUS, y + TAG_HEIGHT], // Arc bottom left
        ['L', x + tagWidth - BORDER_RADIUS, y + TAG_HEIGHT], // Line to right
        ['A', BORDER_RADIUS, BORDER_RADIUS, 0, 0, 0, x + tagWidth, y + TAG_HEIGHT - BORDER_RADIUS], // Arc bottom right
        ['L', x + tagWidth, y + BORDER_RADIUS], // Line to top
        ['A', BORDER_RADIUS, BORDER_RADIUS, 0, 0, 0, x + tagWidth - BORDER_RADIUS, y], // Arc top right
        ['L', x + BORDER_RADIUS, y], // Line to top left
        ['A', BORDER_RADIUS, BORDER_RADIUS, 0, 0, 0, x, y + BORDER_RADIUS], // Arc to finish on top left
      ],
      fill: tagColorMap[color].background,
      stroke: colors.white,
      lineWidth: 1,
    },
  };
};
