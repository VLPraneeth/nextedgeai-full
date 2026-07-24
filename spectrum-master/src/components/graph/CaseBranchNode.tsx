//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { toString } from 'lodash';
import G6Editor from 'sg6-editor';

import { FONT_FAMILY, NODE_STROKE } from 'components/graph/constants';
import { EDGE_OPTIONS } from 'components/graph/registerNodeKebab';
import AppConstants from 'utils/AppConstants';
import { getNodeShadowStyles } from 'utils/GraphUtil';
import { variables } from 'utils/LessConstants';
import { ellipsis } from 'utils/StringUtil';

import { GRAPH_MODE } from '../GraphPage';
import { CHECK_ICON, CHEVRON_DOWN_ICON, UNCHECK_ICON_BORDER } from '../icons/Icons';
const { Flow } = G6Editor;

const MAX_CASE_BRANCH_LABEL_COUNT = 7;

export function registerCaseBranchNode(config: any) {
  Flow.registerNode(
    AppConstants.GRAPH_NODE_SHAPES.CASE_BRANCH_FUNCTION,
    {
      draw(item: any) {
        const group = item.getGraphicGroup();
        const model = item.model;
        const width = 120;
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

        const text = ellipsis(
          toString(model.value ?? model.metadata?.configuration?.value) || '',
          MAX_CASE_BRANCH_LABEL_COUNT
        );

        group.addShape('text', {
          attrs: {
            text,
            x: x + 20,
            y: y + 10,
            fontSize: 16,
            fontWeight: variables.fontWeights.medium,
            fontFamily: FONT_FAMILY,
            textAlign: 'start',
            textBaseline: 'top',
            fill: 'rgba(0,0,0,0.65)',
            section: EDGE_OPTIONS,
          },
        });

        let imageType;
        let iconWidth = 15;
        let iconHeight = 9;

        let img: string | null = CHEVRON_DOWN_ICON;

        // Don't show dropdown arrow when in published pipeline
        const showDropDown = config.graphMode === GRAPH_MODE.DEFAULT;
        if (!showDropDown) {
          img = null;
        }

        let iconX = x + width - 30;
        let iconY = y + 14;

        if (model.selectableNode) {
          if (model.checkedNode) {
            img = CHECK_ICON;
          } else {
            img = UNCHECK_ICON_BORDER;
          }
          iconWidth = 18;
          iconHeight = 18;
          iconY = iconY - 5;
          imageType = 'selectableNode';
        }

        // Chevron icon for actions
        group.addShape('image', {
          attrs: {
            img,
            x: iconX,
            y: iconY,
            width: iconWidth,
            height: iconHeight,
            section: EDGE_OPTIONS,
          },
        });

        // This is a transparent shape on top with the `section` attribute
        // to make the whole shape clickable
        group.addShape('rect', {
          attrs: { ...baseShapeAttrs, fill: 'rgba(255,255,255,0)', section: EDGE_OPTIONS, imageType },
        });

        return keyShape;
      },
      // Set anchor point
      anchor: [
        [0, 0.5], // Midpoint left
        [1, 0.5], // Midpoint of the right
      ],
    },
    AppConstants.GRAPH_NODE_SHAPES.BASE
  );
}
