//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

// Helpful resources when building SVGs by hand
// https://g6.antv.vision/en/docs/manual/middle/elements/shape/shape-and-properties
// https://developer.mozilla.org/en-US/docs/Web/SVG/Attribute/d

import { navigate } from '@reach/router';
import { isEmpty, isUndefined, sumBy } from 'lodash';
import G6Editor from 'sg6-editor';

import { setNodeTooltipMessage } from 'actions/connectorActions';
import { FONT_FAMILY, NODE_STROKE } from 'components/graph/constants';
import {
  CHECK_ICON,
  ERROR_ICON,
  SETTINGS_KEBAB_ICON,
  TIME_TICKER_ENTITY_ICON,
  UNCHECK_ICON,
} from 'components/icons/Icons';
import { syncariConnectorNodeTypes } from 'pages/sync-studio/node-config/Config';
import { setNodeCheck } from 'store/fragment/actions';
import { moveGraphTooltip } from 'store/pipeline/actions';
import AppConstants from 'utils/AppConstants';
import { getNodeShadowStyles, splitNodeText, truncateNodeText } from 'utils/GraphUtil';
import { variables } from 'utils/LessConstants';
import { getDefaultGraphVersion } from 'utils/PipelineUtil';
import RouteConstants from 'utils/RouteConstants';
import { replaceToken } from 'utils/UrlUtil';

import { addTags } from './GraphTags';
import { NODE_KEBAB } from './registerNodeKebab';

const { Flow } = G6Editor;

let isMouseDown = false;
window.addEventListener('mousedown', () => (isMouseDown = true));
window.addEventListener('mouseup', () => (isMouseDown = false));

const { ENTITY_SINK, ENTITY_SOURCE, CONNECTOR, LOGO_ONLY } = AppConstants.GRAPH_NODE_SHAPES;

export const BASE_NODE_CONSTANTS = {
  WIDTH: 220,
  HEIGHT: 52,
  RIGHT_PADDING: 36,
  BORDER_RADIUS: 4,

  // Sub-elements
  LEFT_STRIP_WIDTH: 38,
  BOTTOM_BAR_HEIGHT: 26,
  TAG_MARGIN: 4,

  // Label
  LABEL_LEFT_MARGIN: 52,
  LABEL_TOP_MARGIN: 10,

  // Description
  DESCRIPTION_TOP_MARGIN: 30,
  TAG_TOP_MARGIN: 28,

  // Kebab
  KEBAB_WIDTH: 28,
  KEBAB_HEIGHT: 28,
  KEBAB_LEFT_MARGIN: 8,
  KEBAB_TOP_MARGIN: 11,
};

export function registerBase(config: any) {
  const { dispatch, graphMode } = config;

  Flow.registerNode(AppConstants.GRAPH_NODE_SHAPES.BASE, {
    draw(item: any) {
      const group = item.getGraphicGroup();
      const model = item.getModel();
      const width = BASE_NODE_CONSTANTS.WIDTH;
      const height = BASE_NODE_CONSTANTS.HEIGHT;
      const x = -width / 2;
      const y = -height / 2;
      const borderRadius = BASE_NODE_CONSTANTS.BORDER_RADIUS;
      const leftStripWidth = BASE_NODE_CONSTANTS.LEFT_STRIP_WIDTH;
      const configuration = model?.metadata?.configuration;
      const configId = configuration?.configId;
      const nodeType = model?.nodeType;
      const graphModeIsEditable = graphMode === 'default';

      const iconPath = model?.metadata?.iconPath || undefined;
      const displayName = model?.metadata?.displayName || '';
      const subLabel = model?.metadata?.subLabel || '';
      const backgroundColor = model?.metadata?.backgroundColor || undefined;

      const isCoreNode = [AppConstants.NODE_TYPE.CORE_ENTITY, AppConstants.NODE_TYPE.CORE_ATTRIBUTE].includes(nodeType);

      // Override the anchorPointStyle for published pipelines so we don't show
      // the anchor circles around the node when selected.
      this.anchorPointStyle = {
        radius: graphModeIsEditable ? 11 : 0,
        fill: '#fff',
        stroke: '#1890FF',
        lineAppendWidth: 12,
        fillOpacity: graphModeIsEditable ? 0.25 : 0,
        strokeOpacity: graphModeIsEditable ? 0.8 : 0,
      };

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
          height,
          width,
          x,
          y,
          fill: 'white',
          radius: borderRadius,
        },
      });

      const nodeSupportsKebab = [
        // EP
        AppConstants.NODE_TYPE.ENTITY_SOURCE,
        AppConstants.NODE_TYPE.ENTITY_SINK,
        AppConstants.NODE_TYPE.CONNECTOR_ENTITY,
        // FP
        AppConstants.NODE_TYPE.ATTRIBUTE_SOURCE,
        AppConstants.NODE_TYPE.ATTRIBUTE_SINK,
        AppConstants.NODE_TYPE.CONNECTOR_ATTRIBUTE,
        // Both
        AppConstants.NODE_TYPE.ACTION,
        AppConstants.NODE_TYPE.FUNCTION,
      ].includes(model.nodeType);

      const showKebabOption = nodeSupportsKebab && graphModeIsEditable;

      if (showKebabOption) {
        // Kebab for actions
        group.addShape('image', {
          attrs: {
            img: SETTINGS_KEBAB_ICON,
            x: x + width - BASE_NODE_CONSTANTS.RIGHT_PADDING,
            y: y + BASE_NODE_CONSTANTS.KEBAB_TOP_MARGIN,
            width: BASE_NODE_CONSTANTS.KEBAB_WIDTH,
            height: BASE_NODE_CONSTANTS.KEBAB_HEIGHT,
            section: NODE_KEBAB,
          },
        });
      }

      let iconWidth = 24;
      let iconHeight = 24;
      let iconY = y;
      let iconX = x;

      // Left color strip
      let isOpaque = false;
      if (String(model.hideLeftStrip) !== 'true') {
        let colorType = this.getLeftStripColor(item);
        if (isCoreNode && backgroundColor) {
          colorType = backgroundColor;
          isOpaque = true;
        }
        group.addShape(
          'path',
          getLeftStrip(model.shape, { x, y, borderRadius, height, leftStripWidth, colorType }, isOpaque)
        );
      }

      iconWidth = 32;
      iconHeight = 32;
      iconY = iconY - 4;
      iconX = iconX - 5;

      let icon = '';
      let imageType;

      // Selectable node icon will replace the
      // regular node icon when on that mode
      if (model.selectableNode) {
        if (model.checkedNode) {
          icon = CHECK_ICON;
        } else {
          icon = UNCHECK_ICON;
        }
        iconWidth = 18;
        iconHeight = 18;
        iconX = iconX + 18;
        iconY = iconY + 20;
        imageType = 'selectableNode';
      } else if (model.noicon !== 'noicon') {
        // Custom icon logic for the Time Ticker entity
        // TODO: this bool logic is used in multiple places; isolate it and turn
        // it into a helper function.
        if (
          configId === config.syncariConnectorEntity?.id &&
          syncariConnectorNodeTypes.includes(nodeType) &&
          !isUndefined(configuration?.entityDefinition)
        ) {
          icon = TIME_TICKER_ENTITY_ICON;
        } else {
          icon = model.iconUrl ? model.iconUrl : model.icon || this.type_icon_url;
        }

        // Types of logo
        iconX = this.getLogoX(iconX, item);
        iconY = this.getLogoY(iconY, item);
      }
      if (isCoreNode && iconPath) {
        icon = iconPath;
      }
      group.addShape('image', {
        attrs: {
          img: icon,
          x: iconX,
          y: iconY,
          width: iconWidth,
          height: iconHeight,
          imageType,
        },
      });

      // We show a second line of text only if the node is a function or action
      // and if there are no errors or warnings to show.
      const showSecondLine =
        [AppConstants.NODE_TYPE.ACTION, AppConstants.NODE_TYPE.FUNCTION].includes(nodeType) &&
        !model.errorCount &&
        !model.warningCount;

      let nodeLabel = model.label ? model.label : this.label;
      if (isCoreNode && displayName) {
        nodeLabel = displayName;
      }

      let label = '';
      let labelSecondLine = '';

      const nodeTextOptions = {
        nodeText: nodeLabel || '',
        leftOffset: BASE_NODE_CONSTANTS.LABEL_LEFT_MARGIN,
        rightOffset:
          BASE_NODE_CONSTANTS.KEBAB_WIDTH + BASE_NODE_CONSTANTS.KEBAB_LEFT_MARGIN + BASE_NODE_CONSTANTS.RIGHT_PADDING,
      };

      if (showSecondLine) {
        [label, labelSecondLine] = splitNodeText(nodeTextOptions);
      } else {
        label = truncateNodeText(nodeTextOptions);
      }

      group.addShape('text', {
        attrs: {
          text: label,
          x: x + BASE_NODE_CONSTANTS.LABEL_LEFT_MARGIN,
          y: y + BASE_NODE_CONSTANTS.LABEL_TOP_MARGIN,
          fontSize: 14,
          fontWeight: variables.fontWeights.semibold,
          fontFamily: FONT_FAMILY,
          textAlign: 'start',
          textBaseline: 'top',
          fill: 'rgba(0,0,0,0.65)',
        },
      });

      if (labelSecondLine && showSecondLine) {
        group.addShape('text', {
          attrs: {
            text: labelSecondLine,
            x: x + BASE_NODE_CONSTANTS.LABEL_LEFT_MARGIN,
            y: y + 20 + BASE_NODE_CONSTANTS.LABEL_TOP_MARGIN,
            fontSize: 14,
            fontWeight: variables.fontWeights.semibold,
            fontFamily: FONT_FAMILY,
            textAlign: 'start',
            textBaseline: 'top',
            fill: 'rgba(0,0,0,0.65)',
          },
        });
      }

      const errorCount = model.errorCount || 0;
      const warningCount = model.warningCount || 0;
      const customSynapseDraft = model.customSynapseDraft;

      const tags = [];
      if (errorCount || warningCount || customSynapseDraft) {
        if (customSynapseDraft) {
          tags.push({
            label: 'Draft',
            color: 'orange',
            tagWidth: 32,
          });
        }

        if (errorCount) {
          tags.push({
            label: `${errorCount} Error${errorCount === 1 ? '' : 's'}`,
            color: 'red',
            tagWidth: (errorCount === 1 ? 42 : 50) + 6 * (`${errorCount}`.length - 1),
          });
        }

        if (warningCount) {
          tags.push({
            label: `${warningCount} Warning${warningCount === 1 ? '' : 's'}`,
            color: 'orange',
            tagWidth: (warningCount === 1 ? 56 : 64) + 6 * (`${warningCount}`.length - 1),
          });
        }

        addTags({
          x: x + BASE_NODE_CONSTANTS.LABEL_LEFT_MARGIN,
          y: y + BASE_NODE_CONSTANTS.TAG_TOP_MARGIN,
          group,
          tags: tags as any,
        });
      }

      if (!errorCount && !warningCount) {
        const description = truncateNodeText({
          nodeText: (model.description ? model.description : this.description) || '',
          leftOffset: BASE_NODE_CONSTANTS.LABEL_LEFT_MARGIN,
          rightOffset:
            BASE_NODE_CONSTANTS.KEBAB_WIDTH + BASE_NODE_CONSTANTS.KEBAB_LEFT_MARGIN + BASE_NODE_CONSTANTS.RIGHT_PADDING,
        });

        if (model.description?.localeCompare(description) !== 0) {
          model.tooltipMessage = model.description;
        }

        let tagsLeftOffset = 0;
        if (customSynapseDraft && tags.length === 1) {
          tagsLeftOffset = sumBy(tags, 'tagWidth') + BASE_NODE_CONSTANTS.TAG_MARGIN;
        }

        const descrptionOptions = {
          nodeText: description,
          leftOffset: BASE_NODE_CONSTANTS.LABEL_LEFT_MARGIN + tagsLeftOffset,
          rightOffset: BASE_NODE_CONSTANTS.KEBAB_WIDTH + BASE_NODE_CONSTANTS.RIGHT_PADDING,
        };
        let truncatedDescription = truncateNodeText(descrptionOptions);

        if (isCoreNode && subLabel) {
          truncatedDescription = subLabel;
        }
        group.addShape('text', {
          attrs: {
            text: truncatedDescription,
            x: x + BASE_NODE_CONSTANTS.LABEL_LEFT_MARGIN + tagsLeftOffset,
            y: y + BASE_NODE_CONSTANTS.DESCRIPTION_TOP_MARGIN,
            fontSize: 14,
            fontFamily: FONT_FAMILY,
            textAlign: 'start',
            textBaseline: 'top',
            fill: 'rgb(137,145,150)',
            section: 'statusText',
          },
        });
      }

      // Node Status Icon
      if (model.statusIcon) {
        group.addShape('image', {
          attrs: {
            img: model.statusIcon,
            x: x + 190,
            y: y + 15,
            width: 22,
            height: 22,
          },
        });
      }

      // Validation error
      if (model.validationError) {
        group.addShape('image', {
          attrs: {
            img: ERROR_ICON,
            x: x + 186,
            y: y + 14,
            width: 24,
            height: 24,
            section: 'validationError',
          },
        });
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

    getLeftStripColor(item: any) {
      const model = item.getModel();
      return model.typeColor || this.color_type;
    },

    getLogoX(baseX: number) {
      return baseX + 10;
    },

    getLogoY(baseY: number) {
      return baseY + 13;
    },
  });

  Flow.registerBehaviour('pointerGotoPipeline', function (page: any) {
    var graph = page.getGraph();

    graph.behaviourOn('mouseenter', function (evt: any) {
      if (evt?.shape?._cfg?.attrs?.imageType === 'gotoPipeline') {
        page.css({
          cursor: 'pointer',
        });
      }
    });

    graph.behaviourOn('click', function (evt: any) {
      if (evt?.shape?._cfg?.attrs?.imageType === 'gotoPipeline') {
        evt.domEvent.stopPropagation();
        const entityId = evt.item.model.id;
        const graphVersion = getDefaultGraphVersion(
          evt.item.model?.metadata?.pipelineStatus?.toUpperCase()
        )?.toLowerCase();
        const path = replaceToken(RouteConstants.ENTITY_PIPELINE_GRAPH_VERSION, { entityId, graphVersion });
        navigate(path);
      }
    });

    graph.behaviourOn('mouseleave', function (evt: any) {
      if (evt?.shape?._cfg?.attrs?.imageType === 'gotoPipeline') {
        page.css({
          cursor: 'default',
        });
      }
    });
  });

  Flow.registerBehaviour('pointerStatusHover', function (page: any) {
    const graph = page.getGraph();
    let currentMessage: string;

    function showTooltip(evt: any) {
      if (isMouseDown) {
        return;
      }
      const model = evt.item.getModel();
      let { tooltipMessage } = model;
      if (tooltipMessage) {
        tooltipMessage = model.tooltipMessage;
      } else if (model?.statusErrorDetails) {
        tooltipMessage = model?.statusErrorDetails;
      } else {
        tooltipMessage = model?.statusErrorMessage;
      }
      if (isEmpty(tooltipMessage)) {
        return;
      }

      dispatch(
        moveGraphTooltip({
          top: `${evt.domEvent.clientY - 4}px`,
          left: `${evt.domEvent.clientX - 4}px`,
        })
      );

      if (tooltipMessage !== currentMessage) {
        currentMessage = tooltipMessage;
        dispatch(setNodeTooltipMessage(tooltipMessage));
      }
    }

    graph.behaviourOn('mouseenter', function (evt: any) {
      if (evt?.shape?._cfg?.attrs?.section === 'statusText') {
        showTooltip(evt);
      }
    });
  });

  Flow.registerBehaviour('selectableNode', function (page: any) {
    const graph = page.getGraph();

    graph.behaviourOn('mouseenter', function (evt: any) {
      if (evt?.shape?._cfg?.attrs?.imageType === 'selectableNode') {
        page.css({
          cursor: 'pointer',
        });
      }
    });

    graph.behaviourOn('click', function (evt: any) {
      if (evt?.shape?._cfg?.attrs?.imageType === 'selectableNode') {
        dispatch(setNodeCheck(evt.item?.model?.id, !evt.item?.model?.checkedNode));
      }
    });

    graph.behaviourOn('mouseleave', function (evt: any) {
      if (evt?.shape?._cfg?.attrs?.imageType === 'selectableNode') {
        page.css({
          cursor: 'default',
        });
      }
    });
  });

  Flow.registerBehaviour('nodeError', function (page: any) {
    const graph = page.getGraph();
    let currentMessage: string;

    function showTooltip(evt: any) {
      if (isMouseDown) {
        return;
      }
      const model = evt.item.getModel();
      let { validationErrorMsg: tooltipMessage } = model;

      dispatch(
        moveGraphTooltip({
          top: `${evt.domEvent.clientY - 4}px`,
          left: `${evt.domEvent.clientX - 4}px`,
        })
      );

      if (tooltipMessage !== currentMessage) {
        currentMessage = tooltipMessage;
        dispatch(setNodeTooltipMessage(tooltipMessage));
      }
    }

    graph.behaviourOn('mouseenter', function (evt: any) {
      if (evt?.shape?._cfg?.attrs?.section === 'validationError') {
        showTooltip(evt);
      }
    });
  });
}

export function getLeftStrip(
  shape: any,
  { x, y, borderRadius, height, leftStripWidth, colorType }: any,
  isOpaque = false
) {
  const margin = [ENTITY_SINK, ENTITY_SOURCE, CONNECTOR, LOGO_ONLY].includes(shape) ? 1 : 0;
  return {
    attrs: {
      path: [
        ['M', x + margin, y + margin + borderRadius], // Start
        ['L', x + margin, y + height - borderRadius], // Going down
        ['A', borderRadius, borderRadius, 0, 0, 0, x - margin + borderRadius, y - margin + height], // Arc bottom left
        ['L', x + borderRadius + leftStripWidth, y + height - margin], // Line to right
        ['L', x + borderRadius + leftStripWidth, y + margin], // Line to top
        ['L', x + borderRadius, y + margin], // Line to left
        ['A', borderRadius, borderRadius, 0, 0, 0, x + margin, y + margin + borderRadius], // Arc top left
        // Close
      ],
      fill: colorType,
      ...(isOpaque ? { fillOpacity: '.1' } : {}),
    },
  };
}
