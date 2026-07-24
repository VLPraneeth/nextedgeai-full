//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import G6Editor from 'sg6-editor';

import { FONT_FAMILY, MAX_LABEL_COUNT, NODE_STROKE } from 'components/graph/constants';
import { ENTITY_ICON_MAP, SELECTED_ENTITY_ICON_MAP } from 'components/icons/Icons';
import AppConstants from 'utils/AppConstants';
import { getRelativeDate } from 'utils/DateUtil';
import { getNodeShadowStyles } from 'utils/GraphUtil';
import { tNamespaced } from 'utils/i18nUtil';
import { colors, variables } from 'utils/LessConstants';
import { ellipsis } from 'utils/StringUtil';

import { BASE_NODE_CONSTANTS } from './Base';
import getTagDataForEntityNode from './getTagDataForEntityNode';
import { addTags } from './GraphTags';

export const WARNING_ICON = '/assets/icons/warning.svg';

const tn = tNamespaced('EntityNode');

const { Flow } = G6Editor;

const { BOTTOM_BAR_HEIGHT, TAG_MARGIN } = BASE_NODE_CONSTANTS;

const CARD_WIDTH = 241;
const CARD_HEIGHT = 66;

const registerEntityNode = () => {
  Flow.registerNode(AppConstants.GRAPH_NODE_SHAPES.ENTITY_NODE, {
    draw(item: any) {
      const group = item.getGraphicGroup();
      const model = item.getModel();
      const x = -CARD_WIDTH / 2;
      const y = -CARD_HEIGHT / 2;
      const borderRadius = 4;

      const keyShape = group.addShape('rect', {
        attrs: {
          ...getNodeShadowStyles(y + 10),
          x,
          y,
          width: CARD_WIDTH,
          height: CARD_HEIGHT,
          radius: borderRadius,
          fill: 'white',
          stroke: NODE_STROKE,
        },
      });

      // Adding a second shape that matches the shape above but without the
      // shadow. For some reason this makes the selected border around the
      // entire node even.
      group.addShape('rect', {
        attrs: {
          x,
          y,
          width: CARD_WIDTH,
          height: CARD_HEIGHT,
          radius: borderRadius,
          fill: 'white',
          stroke: NODE_STROKE,
        },
      });

      // Add bottom strip
      group.addShape(
        'path',
        getBottomStrip({
          x,
          y,
          borderRadius,
          cardHeight: CARD_HEIGHT,
          bottomStripHeight: BOTTOM_BAR_HEIGHT,
          isSelected: item.isSelected,
        })
      );

      const tags = getTagDataForEntityNode(model.syncStatus, model.metadata.pipelineStatus);

      const tagY = y + CARD_HEIGHT - BOTTOM_BAR_HEIGHT + TAG_MARGIN;

      const nextTagPosition = addTags({
        x: x + TAG_MARGIN,
        y: tagY,
        group,
        tags,
      });

      if (model.warningCount) {
        group.addShape('image', {
          attrs: {
            img: WARNING_ICON,
            x: nextTagPosition,
            y: tagY + 2,
            width: 16,
            height: 16,
          },
        });
      }

      // Ideally we would have a single set of icons and use currentColor to
      // change the color but there's not a way to do that with the addShape function
      const iconMap = item.isSelected ? SELECTED_ENTITY_ICON_MAP : ENTITY_ICON_MAP;
      const icon = iconMap[model.label as keyof typeof iconMap] || iconMap.Custom;
      group.addShape('image', {
        attrs: {
          img: icon,
          x: x + 9,
          y: y + 9,
          width: 26,
          height: 26,
        },
      });

      // Name text
      const label = ellipsis((model.label ? model.label : this.label) || '', MAX_LABEL_COUNT);

      group.addShape('text', {
        attrs: {
          text: label,
          x: x + 44,
          y: y + 8,
          fontSize: 14,
          fontWeight: variables.fontWeights.semibold,
          fontFamily: FONT_FAMILY,
          textAlign: 'start',
          textBaseline: 'top',
          fill: colors.black,
        },
      });

      // Show last synced timestamp
      const syncTimestamp = model.lastSyncTime
        ? tn('synced_time', { time: getRelativeDate(model.lastSyncTime), interpolation: { escapeValue: false } })
        : tn('never_synced');

      group.addShape('text', {
        attrs: {
          text: syncTimestamp,
          x: x + 44,
          y: y + 24,
          fontSize: 12,
          fontFamily: FONT_FAMILY,
          textAlign: 'start',
          textBaseline: 'top',
          fill: item.isSelected ? colors.blue600 : colors.gray600,
          section: 'statusText',
        },
      });

      return keyShape;
    },

    // Set anchor point
    anchor: [
      [0.5, 0], // Midpoint above
      [1, 0.5], // Midpoint left
      [0.5, 1], // Midpoint of the bottom
      [0, 0.5], // Midpoint of the right
    ],

    getLeftStripColor(item: any) {
      const model = item.getModel();
      return model.typeColor || this.color_type;
    },
  });
};

export interface BottomStripProps {
  x: number;
  y: number;
  cardHeight: number;
  bottomStripHeight: number;
  borderRadius: number;
  isSelected: boolean;
  cardWidth?: number;
}

export function getBottomStrip({
  x,
  y,
  cardHeight,
  bottomStripHeight,
  borderRadius,
  isSelected,
  cardWidth = CARD_WIDTH,
}: BottomStripProps) {
  const margin = 0.5;

  return {
    attrs: {
      path: [
        ['M', x + margin, y + margin + cardHeight - bottomStripHeight], // Start - top left
        ['L', x + margin, y + margin + cardHeight - borderRadius], // Going down
        ['A', borderRadius, borderRadius, 0, 0, 0, x - margin + borderRadius, y - margin + cardHeight], // Arc bottom left
        ['L', x + cardWidth - margin - borderRadius, y - margin + cardHeight], // Line to right
        ['A', borderRadius, borderRadius, 0, 0, 0, x + cardWidth - margin, y - margin + cardHeight - borderRadius], // Arc bottom right
        ['L', x + cardWidth - margin, y + margin + cardHeight - bottomStripHeight], // Line to top
        ['L', x + margin, y + margin + cardHeight - bottomStripHeight], // End where we started
      ],
      fill: isSelected ? colors.blue250 : colors.gray200,
    },
  };
}

export default registerEntityNode;
