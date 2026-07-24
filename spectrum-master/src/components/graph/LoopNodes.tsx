//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import G6Editor from 'sg6-editor';

import { FONT_FAMILY, NODE_STROKE } from 'components/graph/constants';
import AppConstants from 'utils/AppConstants';
import { getNodeShadowStyles } from 'utils/GraphUtil';
import { variables } from 'utils/LessConstants';

import { CHECK_ICON } from '../icons/Icons';

const { Flow } = G6Editor;

export function registerLoopNodes(config: any) {
  Flow.registerNode(
    AppConstants.GRAPH_NODE_SHAPES.LOOP_SIDE_FUNCTION,
    {
      draw(item: any) {
        const group = item.getGraphicGroup();
        const model = item.model;
        const width = 110;
        const height = 37;
        const x = -width / 2;
        const y = -height / 2;
        const borderRadius = 4;

        const baseShapeAttrs = {
          x,
          y,
          width,
          height,
          radius: borderRadius,
          fill: 'white',
          stroke: NODE_STROKE,
        };
        const keyShape = group.addShape('rect', {
          attrs: {
            ...getNodeShadowStyles(y - 10),
            ...baseShapeAttrs,
          },
        });

        // Adding a second shape that matches the shape above but without the
        // shadow. For some reason this makes the selected border around the
        // entire node even.
        group.addShape('rect', {
          attrs: { ...baseShapeAttrs },
        });

        group.addShape('text', {
          attrs: {
            text: model?.label,
            x: x + 14,
            y: y + 10,
            fontSize: 14,
            fontWeight: variables.fontWeights.semibold,
            fontFamily: FONT_FAMILY,
            textAlign: 'start',
            textBaseline: 'top',
            fill: 'rgba(0,0,0,0.65)',
          },
        });

        let iconWidth = 15;
        let iconHeight = 9;

        const img = CHECK_ICON;

        let iconX = x + width - 25;
        let iconY = y + 14;

        if (model.selectableNode) {
          if (model.checkedNode) {
            iconWidth = 18;
            iconHeight = 18;
            iconY = iconY - 5;

            // Chevron icon for actions
            group.addShape('image', {
              attrs: {
                img,
                x: iconX,
                y: iconY,
                width: iconWidth,
                height: iconHeight,
              },
            });
          }
        }

        return keyShape;
      },
      // Set anchor point
      anchor: [
        [0.5, 0], // Midpoint above
        [1, 0.5], // Midpoint left
        [0.5, 1], // Midpoint of the bottom
        [0, 0.5], // Midpoint of the right
      ],
    },
    AppConstants.GRAPH_NODE_SHAPES.BASE
  );
}
