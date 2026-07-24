//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import G6Editor from 'sg6-editor';

import { SYNCARI_CORE_NODE_INTRO } from 'components/icons/Icons';
import AppConstants from 'utils/AppConstants';
import { SYNCARI_CIRCLE_RADIUS } from 'utils/ConnectorUtil';

import { generateAnchors } from './SyncariCircle';
import { getNodeShadowStyles } from 'utils/GraphUtil';

const { Flow } = G6Editor;

// This is not the actual radius but more a %.
// SGEditor converts this internally to the actual radius and points.
const RADIUS = 0.5;

// Plot a point in the circle every 10 degrees
const POINT_DEGREES_INCREMENT = 10;

export function registerSyncariCircleWithIntro() {
  Flow.registerNode(AppConstants.GRAPH_NODE_SHAPES.SYNCARI_CIRCLE_WITH_INTRO, {
    draw(item: any) {
      const group = item.getGraphicGroup();
      const model = item.getModel();
      const radius = SYNCARI_CIRCLE_RADIUS;
      const x = 0;
      const y = 0;

      // This circle is used to anchor the edges to so it doesn't wrap around
      // the intro image
      const mainShape = group.addShape('circle', {
        attrs: {
          x: radius / 2,
          y: radius / 2,
          r: radius,
        },
      });

      const shouldRenderDefaultLabel = model.label === '' || model.label === 'Syncari';
      const iconPath = model.iconUrl ? model.iconUrl : model.icon || this.type_icon_url;

      let iconWidth = shouldRenderDefaultLabel ? 332 : 62;
      let iconHeight = shouldRenderDefaultLabel ? 326 : 62;

      if (shouldRenderDefaultLabel) {
        group.addShape('image', {
          attrs: {
            img: SYNCARI_CORE_NODE_INTRO,
            x: x - 52,
            y: y - 222,
            width: iconWidth,
            height: iconHeight,
          },
        });
      } else {
        group.addShape('circle', {
          attrs: {
            ...getNodeShadowStyles(),
            shadowOffsetX: 6,
            shadowOffsetY: y + 10,
            shadowBlur: 20,
            x: radius / 2,
            y: radius / 2,
            r: radius,
            fill: 'white',
          },
        });

        group.addShape('image', {
          attrs: {
            img: iconPath,
            x: x,
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
}
