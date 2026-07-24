//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import G6Editor from 'sg6-editor';

import { FONT_FAMILY, NODE_STROKE } from 'components/graph/constants';
import { SETTINGS_KEBAB_ICON } from 'components/icons/Icons';
import AppConstants from 'utils/AppConstants';
import { navigateTo } from 'utils/AppUtil';
import { getNodeShadowStyles, truncateNodeText } from 'utils/GraphUtil';
import { colors, variables } from 'utils/LessConstants';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { humanize } from 'utils/StringUtil';
import { makeUrl } from 'utils/UrlUtil';

import { BASE_NODE_CONSTANTS, getLeftStrip } from './Base';
import { getBottomStrip, WARNING_ICON } from './EntityNode';
import getTagDataForEntityNode from './getTagDataForEntityNode';
import { addTags } from './GraphTags';
import { CONNECTOR_NODE_KEBAB } from './registerNodeKebab';

const { Flow } = G6Editor;

const {
  BOTTOM_BAR_HEIGHT,
  TAG_MARGIN,
  WIDTH,
  HEIGHT,
  BORDER_RADIUS,
  LEFT_STRIP_WIDTH,
  KEBAB_WIDTH,
  RIGHT_PADDING,
  KEBAB_LEFT_MARGIN,
  KEBAB_HEIGHT,
} = BASE_NODE_CONSTANTS;

const STATUS_COLOR_MAP = {
  [AppConstants.CONNECTOR_STATUS.NEW]: colors.gray700,
  [AppConstants.CONNECTOR_STATUS.DELETED]: colors.orange600,
  [AppConstants.CONNECTOR_STATUS.ACTIVE]: colors.green400,
  [AppConstants.CONNECTOR_STATUS.AUTHENTICATED]: colors.blue700,
  [AppConstants.CONNECTOR_STATUS.ACTIVATING]: colors.blue700,
  [AppConstants.CONNECTOR_STATUS.ERROR]: colors.red300,
};

export const CONNECTOR_NODE_CONSTANTS = {
  LABEL_LEFT_MARGIN: 56,
  LABEL_TOP_MARGIN: 10,
};

const registerConnector = (config: any) => {
  Flow.registerNode(AppConstants.GRAPH_NODE_SHAPES.CONNECTOR, {
    draw(item: any) {
      const group = item.getGraphicGroup();
      const model = item.getModel();

      const isCustomSynapse = Boolean(model.customSynapseStatus);
      const width = WIDTH;
      const height = isCustomSynapse ? HEIGHT + BOTTOM_BAR_HEIGHT - 2 : HEIGHT;

      const x = -width / 2;
      const y = -HEIGHT / 2;
      const borderRadius = BORDER_RADIUS;
      const leftStripWidth = LEFT_STRIP_WIDTH;

      const keyShape = group.addShape('rect', {
        attrs: {
          ...getNodeShadowStyles(y),
          x,
          y,
          width,
          height,
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
          width,
          height,
          radius: borderRadius,
          fill: 'white',
          stroke: NODE_STROKE,
        },
      });

      let iconWidth = 24;
      let iconHeight = 24;
      let iconY = y;
      let iconX = x;

      // Left color strip
      if (String(model.hideLeftStrip) !== 'true') {
        const colorType = this.getLeftStripColor(item);
        group.addShape(
          'path',
          getLeftStrip(model.shape, {
            x,
            y,
            borderRadius,
            height: HEIGHT,
            leftStripWidth,
            colorType,
          })
        );
      } else {
        iconWidth = 32;
        iconHeight = 32;
        iconY = iconY - 4;
      }

      iconWidth = 32;
      iconHeight = 32;
      iconY = iconY - 4;
      iconX = iconX - 5;

      if (model.noicon !== 'noicon') {
        // Types of logo
        const icon = model.iconUrl ? model.iconUrl : model.icon || this.type_icon_url;
        group.addShape('image', {
          attrs: {
            img: icon,
            x: this.getLogoX(iconX, item),
            y: this.getLogoY(iconY, item),
            width: iconWidth,
            height: iconHeight,
          },
        });
      }

      // Name text
      const label = truncateNodeText({
        nodeText: (model.label ? model.label : this.label) || '',
        leftOffset: CONNECTOR_NODE_CONSTANTS.LABEL_LEFT_MARGIN,
        rightOffset: KEBAB_WIDTH + RIGHT_PADDING + KEBAB_LEFT_MARGIN,
      });

      group.addShape('text', {
        attrs: {
          text: label,
          x: x + CONNECTOR_NODE_CONSTANTS.LABEL_LEFT_MARGIN,
          y: y + CONNECTOR_NODE_CONSTANTS.LABEL_TOP_MARGIN,
          fontSize: 14,
          fontWeight: variables.fontWeights.semibold,
          fontFamily: FONT_FAMILY,
          textAlign: 'start',
          textBaseline: 'top',
          fill: 'rgba(0,0,0,0.65)',
        },
      });

      // Status circle
      group.addShape('circle', {
        attrs: {
          x: x + 60,
          y: y + 35,
          r: 4,
          fill: this.getStatusFill(model.status),
          stroke: this.getStatusFill(model.status),
        },
      });

      // Status text
      const status = model.status === AppConstants.CONNECTOR_STATUS.ACTIVATING ? 'Activating…' : humanize(model.status);
      group.addShape('text', {
        attrs: {
          text: status,
          x: x + 68,
          y: y + 28,
          fontSize: 14,
          fontFamily: FONT_FAMILY,
          textAlign: 'start',
          textBaseline: 'top',
          fill: this.getStatusFill(model.status),
          section: 'statusText',
        },
      });

      // Status Image
      group.addShape('image', {
        attrs: {
          img: SETTINGS_KEBAB_ICON,
          x: x + 186,
          y: y + 12,
          width: KEBAB_WIDTH,
          height: KEBAB_HEIGHT,
          section: CONNECTOR_NODE_KEBAB,
        },
      });

      // Bottom tag bar
      if (isCustomSynapse) {
        group.addShape(
          'path',
          getBottomStrip({
            x,
            y,
            cardHeight: height,
            cardWidth: width,
            bottomStripHeight: BOTTOM_BAR_HEIGHT,
            borderRadius,
            isSelected: item.isSelected,
          })
        );

        const tags = getTagDataForEntityNode(null, model.customSynapseStatus);

        const tagY = y + height - BOTTOM_BAR_HEIGHT + TAG_MARGIN;

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
      }

      return keyShape;
    },
    // prettier-ignore
    // Set anchor points
    // Note: Anchor points are the bubbles on the graph node where edges attached to.
    // Synapse edges are now autoconnecting so I added alot so it would look nicer.
    // Theres no easy way to hide them but ideally hide them  on synapse nodes.
    anchor: [
      [0.5, 0],   // Top Mid
      [0.4, 0],
      [0.3, 0],
      [0.2, 0],
      [0.1, 0],
      [0, 0],    // Top Left
      [0, 0.25],
      [0, 0.5],  // Left Mid
      [0, 0.75],
      [0, 1],    // Bottom Left
      [0.1, 1],
      [0.2, 1],
      [0.3, 1],
      [0.4, 1],
      [0.5, 1],  // Bottom Mid
      [0.6, 1],
      [0.7, 1],
      [0.8, 1],
      [0.9, 1],
      [1, 1],    // Bottom Right
      [1, 0.75],
      [1, 0.5],  // Right Mid
      [1, 0.25],
      [1, 0],    // Top right
      [0.9, 0],
      [0.8, 0],
      [0.7, 0],
      [0.6, 0],
    ],

    getLeftStripColor(item: any) {
      const model = item.getModel();
      return model.typeColor || this.color_type;
    },

    getLogoX(baseX: number) {
      return baseX + 11;
    },

    getLogoY(baseY: number) {
      return baseY + 14;
    },

    getStatusFill(status: any) {
      return (
        STATUS_COLOR_MAP[status.toUpperCase() as keyof typeof STATUS_COLOR_MAP] ||
        STATUS_COLOR_MAP[AppConstants.CONNECTOR_STATUS.NEW]
      );
    },
  });

  Flow.registerBehaviour('doubleClickConnector', (page: any) => {
    const graph = page.getGraph();

    graph.behaviourOn('dblclick', (evt: any) => {
      if (evt?.item?.model?.shape === AppConstants.GRAPH_NODE_SHAPES.CONNECTOR) {
        const requiredPermissions = [AllPermissions.READ_STUDIO, AllPermissions.READ_CONNECTOR];
        const canNavigate = requiredPermissions.every((permission) => config?.userPermissions?.includes(permission));
        const connectorId = evt.item.model.id;

        if (canNavigate) {
          navigateTo(makeUrl(RouteConstants.SCHEMA_STUDIO_SYNAPSE, { connectorId }));
        }
      }
    });
  });
};

export default registerConnector;
