//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import G6Editor from 'sg6-editor';

import { FONT_FAMILY, NODE_STROKE } from 'components/graph/constants';
import { SYNCARI_ICON } from 'components/icons/Icons';
import AppConstants from 'utils/AppConstants';
import { navigateTo } from 'utils/AppUtil';
import { SYNCARI_CIRCLE_RADIUS } from 'utils/ConnectorUtil';
import { getNodeShadowStyles } from 'utils/GraphUtil';
import { tc } from 'utils/i18nUtil';
import { variables } from 'utils/LessConstants';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

const { Flow } = G6Editor;

// This is not the actual radius but more a %.
// SGEditor converts this internally to the actual radius and points.
const RADIUS = 0.5;

// Plot a point in the circle every 10 degrees
const POINT_DEGREES_INCREMENT = 10;

export function registerSyncariCircle(config: any) {
  Flow.registerNode(AppConstants.GRAPH_NODE_SHAPES.SYNCARI_CIRCLE, {
    draw(item: any) {
      const group = item.getGraphicGroup();
      const model = item.getModel();
      const radius = SYNCARI_CIRCLE_RADIUS;
      const x = 0;
      const y = 0;

      const mainShape = group.addShape('circle', {
        attrs: {
          ...getNodeShadowStyles(),
          shadowOffsetX: 6,
          shadowOffsetY: y + 10,
          shadowBlur: 20,
          x: radius / 2,
          y: radius / 2,
          r: radius,
          fill: 'white',
          stroke: NODE_STROKE,
        },
      });

      group.addShape('circle', {
        attrs: {
          x: radius / 2,
          y: radius / 2,
          r: radius,
          fill: 'white',
        },
      });

      let iconWidth = 62;
      let iconHeight = 62;

      const shouldRenderLabel = model.label === '' || model.label === 'Syncari';
      const iconPath = model.iconUrl ? model.iconUrl : model.icon || this.type_icon_url;

      if (shouldRenderLabel) {
        group.addShape('image', {
          attrs: {
            img: iconPath,
            x: x - 1,
            y: y - 16,
            width: iconWidth,
            height: iconHeight,
          },
        });

        group.addShape('text', {
          attrs: {
            text: tc('syncari'),
            x: x + 4,
            y: y + 32,
            fontSize: 16,
            fontWeight: variables.fontWeights.semibold,
            fontFamily: FONT_FAMILY,
            textAlign: 'start',
            textBaseline: 'top',
            fill: 'rgba(0,0,0,0.65)',
          },
        });
      } else {
        const iconPath = model.iconUrl ? model.iconUrl : model.icon || this.type_icon_url;
        group.addShape('image', {
          attrs: {
            img: iconPath,
            x: x - 1,
            y: y,
            width: iconWidth,
            height: iconHeight,
          },
        });
      }
      return mainShape;
    },

    // Set anchor point
    anchor() {
      // Plot points every 10 degrees
      return generateAnchors(RADIUS, POINT_DEGREES_INCREMENT);
    },
  });

  Flow.registerBehaviour('doubleClickSyncariCircle', (page: any) => {
    const graph = page.getGraph();

    graph.behaviourOn('dblclick', (evt: any) => {
      const shape = evt?.item?.model?.shape;
      if (
        shape === AppConstants.GRAPH_NODE_SHAPES.SYNCARI_CIRCLE ||
        shape === AppConstants.GRAPH_NODE_SHAPES.SYNCARI_CIRCLE_WITH_INTRO
      ) {
        const requiredPermissions = [AllPermissions.READ_STUDIO, AllPermissions.READ_CONNECTOR];
        const canNavigate = requiredPermissions.every((permission) => config?.userPermissions?.includes(permission));

        if (canNavigate) {
          navigateTo(makeUrl(RouteConstants.SCHEMA_STUDIO_ROOT));
        }
      }
    });
  });
}

/**
 * Get the point in the circle
 */
const getCirclePoint = (degrees: number, radius: number) => {
  const radians = degrees * (Math.PI / 180);
  // Add a radius to the x and y since center is not zero. Its at the
  // top right of the quadrant.
  return [Math.cos(radians) * radius + radius, Math.sin(radians) * radius + radius];
};

/**
 * Generate anchors every x degrees
 * @param {Integer} radius Radius of the circle
 * @param {Integer} degIncr Plot a point in the circle every N degrees
 * @returns
 */
export const generateAnchors = (radius: number, degIncr: number) => {
  const anchors = [];

  for (let degrees = 0; degrees < 360; degrees += degIncr) {
    anchors.push(getCirclePoint(degrees, radius));
  }
  return anchors;
};
