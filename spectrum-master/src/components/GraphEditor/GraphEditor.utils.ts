//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { sum } from 'lodash';
import G6Editor from 'sg6-editor';

import { registerBase } from 'components/graph/Base';
import { registerCaseBranchNode } from 'components/graph/CaseBranchNode';
import { FONT_FAMILY, MAX_LABEL_COUNT, NODE_STROKE } from 'components/graph/constants';
import { registerLoopNodes } from 'components/graph/LoopNodes';
import { registerPredicateNode } from 'components/graph/PredicateNode';
import registerConnector from 'components/graph/registerConnector';
import registerNodeKebab, { GROUP_NODE_KEBAB } from 'components/graph/registerNodeKebab';
import { registerSyncariCircle } from 'components/graph/SyncariCircle';
import { GroupColor } from 'store/pipeline/types';
import AppConstants from 'utils/AppConstants';
import { getNodeShadowStyles } from 'utils/GraphUtil';
import { variables } from 'utils/LessConstants';
import { ellipsis } from 'utils/StringUtil';

import DatabaseCustomEntityIcon from '../../assets/database-custom-entity.svg';
import DatabaseEntityIcon from '../../assets/database-entity.svg';
import registerEntityNode from '../graph/EntityNode';
import { addTags, SPACE_BETWEEN_TAGS, TagData } from '../graph/GraphTags';
import { registerSyncariCircleWithIntro } from '../graph/SyncariCircleWithIntro';
import { COLLAPSE_GROUP_ICON, EXPAND_GROUP_ICON, GROUP_GRAY_ICON, SETTINGS_KEBAB_ICON } from '../icons/Icons';

// take in config of shape { dispatch } so we can have nodes/graph items dispatch
// actions into our store
export const registerGraphComponents = (config: any) =>
  new Promise<void>((resolve, reject) => {
    try {
      const { Flow } = G6Editor;

      // Registration model card base class
      registerBase(config);

      Flow.registerGroup(
        AppConstants.GRAPH_NODE_SHAPES.CUSTOM_GROUP,
        {
          draw(item: any) {
            const group = item.getGraphicGroup();
            const model = item.getModel();
            const childrenBox = item.getChildrenBBox();

            const collapsed = model.collapsed;
            const expanded = !collapsed;

            const collapsedWidth = 280;
            const topNodeHeight = 52;
            const y = -topNodeHeight / 2;
            const borderRadius = 4;
            const groupColor = AppConstants.GROUP_COLORS[model.color as GroupColor] || AppConstants.GROUP_COLORS.GRAY;

            const childNodesPadding = 40;
            const labelCharacters = collapsed ? MAX_LABEL_COUNT : childrenBox.width / 11;

            const baseX = expanded
              ? childrenBox.x - childNodesPadding / 2
              : childrenBox.x + childrenBox.width - collapsedWidth + childNodesPadding / 2;
            const baseY = childrenBox.y - topNodeHeight - childNodesPadding / 2;
            const containerWidth = expanded ? childrenBox.width + childNodesPadding : collapsedWidth;
            const containerHeight = expanded ? topNodeHeight + childrenBox.height + childNodesPadding : topNodeHeight;

            // Outer containing rectangle
            const keyShape = group.addShape('rect', {
              attrs: {
                height: containerHeight,
                width: containerWidth,
                x: baseX,
                y: baseY,
                radius: borderRadius,
                stroke: NODE_STROKE,
              },
            });

            // Adding a second shape that matches the shape above, but with shadow.
            // Adding the shadow here persists the shadow after node selection for
            // some reason.
            group.addShape('rect', {
              attrs: {
                ...getNodeShadowStyles(y),
                height: containerHeight,
                width: containerWidth,
                x: baseX,
                y: baseY,
                radius: borderRadius,
                fill: 'rgba(239,239,239,0.4)',
                stroke: NODE_STROKE,
              },
            });

            // Top node details shape
            group.addShape('rect', {
              attrs: {
                height: topNodeHeight,
                width: containerWidth,
                x: baseX,
                y: baseY,
                fill: 'white',
                radius: expanded ? [borderRadius, borderRadius, 0, 0] : borderRadius,
              },
            });

            // Color box around group icon
            const groupIconBoxWidth = 40;
            group.addShape('rect', {
              attrs: {
                x: baseX,
                y: baseY,
                fill: groupColor,
                radius: [borderRadius, 0, 0, expanded ? 0 : borderRadius],
                height: topNodeHeight,
                width: groupIconBoxWidth,
              },
            });

            // Group icon
            const iconWidth = 20;
            group.addShape('image', {
              attrs: {
                img: GROUP_GRAY_ICON,
                x: baseX + 10,
                y: baseY + 15,
                width: iconWidth,
                height: iconWidth,
              },
            });

            // Name text
            const titleX = baseX + groupIconBoxWidth + 10;
            const titleY = baseY + 10;
            const label = ellipsis((model.label ? model.label : this.label) || '', labelCharacters);
            group.addShape('text', {
              attrs: {
                text: label,
                x: titleX,
                y: baseY + 10,
                fontSize: 14,
                fontWeight: variables.fontWeights.semibold,
                fontFamily: FONT_FAMILY,
                textBaseline: 'top',
                fill: 'rgba(0,0,0,0.65)',
              },
            });

            const errorCount = model.errorCount || 0;
            const warningCount = model.warningCount || 0;
            const tags: TagData[] = [];

            if (errorCount || warningCount) {
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
                x: titleX,
                y: titleY + 18,
                group,
                tags,
              });
            }

            const tagsWidth = sum(tags.map((tag) => tag.tagWidth));
            const tagsPadding = SPACE_BETWEEN_TAGS * tags.length;
            const totalTagsWidth = tagsWidth + tagsPadding;
            const nodeSummaryCharacters =
              ((collapsed ? collapsedWidth : childrenBox.width) - totalTagsWidth) / (tags.length === 0 ? 11 : 13);
            group.addShape('text', {
              attrs: {
                text: ellipsis(model.childNodeSummary || '', nodeSummaryCharacters),
                x: titleX + totalTagsWidth,
                y: titleY + 20,
                fontSize: 14,
                fontFamily: FONT_FAMILY,
                textAlign: 'start',
                textBaseline: 'top',
                fill: 'rgb(137,145,150)',
                section: 'statusText',
              },
            });

            const graphModeIsEditable = config.graphMode === 'default';

            // Expand/collapse group icon
            group.addShape('image', {
              attrs: {
                img: item.model.collapsed ? EXPAND_GROUP_ICON : COLLAPSE_GROUP_ICON,
                x: baseX + containerWidth - (graphModeIsEditable ? 58 : 34),
                y: baseY + 16,
                width: 18,
                height: 18,
                section: 'expandCollapse',
              },
            });

            if (graphModeIsEditable) {
              // Group node kebab
              group.addShape('image', {
                attrs: {
                  img: SETTINGS_KEBAB_ICON,
                  x: baseX + containerWidth - 36,
                  y: baseY + 11,
                  width: 28,
                  height: 28,
                  section: GROUP_NODE_KEBAB,
                },
              });
            }

            if (expanded) {
              // Bottom border for node details box
              group.addShape('rect', {
                attrs: {
                  x: baseX,
                  y: baseY + topNodeHeight - 1,
                  height: 1,
                  fill: NODE_STROKE,
                  width: containerWidth,
                },
              });

              // Box around the child nodes
              group.addShape('rect', {
                attrs: {
                  ...childrenBox,
                  height: childrenBox.height + childNodesPadding,
                  width: childrenBox.width + childNodesPadding,
                  x: childrenBox.x - childNodesPadding / 2,
                  y: childrenBox.y - childNodesPadding / 2,
                  radius: [0, 0, borderRadius, borderRadius],
                },
              });
            }

            return keyShape;
          },
        },
        AppConstants.GRAPH_NODE_SHAPES.BASE
      );

      Flow.registerBehaviour('expandCollapseGroup', function (page: any) {
        var graph = page.getGraph();

        graph.behaviourOn('mouseenter', function (evt: any) {
          if (evt?.shape?._cfg?.attrs?.section === 'expandCollapse') {
            page.css({
              cursor: 'pointer',
            });
          }
        });

        graph.behaviourOn('mouseleave', function (evt: any) {
          if (evt?.shape?._cfg?.attrs?.section === 'expandCollapse') {
            page.css({
              cursor: 'default',
            });
          }
        });

        graph.behaviourOn('click', function ({ item, shape }: any) {
          if (shape?._cfg?.attrs?.section === 'expandCollapse') {
            page.editor.executeCommand(() => {
              item.update();
              page.update(item, {
                collapsed: !item.model.collapsed,
              });
            });
          }
        });
      });

      Flow.registerNode(
        AppConstants.GRAPH_NODE_SHAPES.FUNCTION,
        {
          getLeftStripColor(item: any) {
            return AppConstants.FLOW_TYPE_COLOR.FUNCTION;
          },
        },
        AppConstants.GRAPH_NODE_SHAPES.BASE
      );

      registerPredicateNode(config);

      registerCaseBranchNode(config);

      registerLoopNodes(config);

      Flow.registerNode(
        AppConstants.GRAPH_NODE_SHAPES.ACTION,
        {
          getLeftStripColor(item: any) {
            return AppConstants.FLOW_TYPE_COLOR.ACTION;
          },
        },
        AppConstants.GRAPH_NODE_SHAPES.BASE
      );

      Flow.registerNode(AppConstants.GRAPH_NODE_SHAPES.CORE_ENTITY, {}, AppConstants.GRAPH_NODE_SHAPES.BASE);

      Flow.registerNode(
        AppConstants.GRAPH_NODE_SHAPES.LOGO_ONLY,
        {
          getLogoX(baseX: any, item: any) {
            return baseX + 12;
          },
          getLogoY(baseY: any, item: any) {
            return baseY + 14;
          },
        },
        AppConstants.GRAPH_NODE_SHAPES.BASE
      );

      Flow.registerNode(AppConstants.GRAPH_NODE_SHAPES.ENTITY_SINK, {}, AppConstants.GRAPH_NODE_SHAPES.LOGO_ONLY);

      Flow.registerNode(AppConstants.GRAPH_NODE_SHAPES.ENTITY_SOURCE, {}, AppConstants.GRAPH_NODE_SHAPES.LOGO_ONLY);

      Flow.registerBehaviour('pointerNodeCursor', function (page: any) {
        var graph = page.getGraph();
        graph.behaviourOn('mouseenter', function (evt: any) {
          if (evt.shape && evt.shape.eventPreFix === 'node') {
            page.css({
              cursor: 'pointer',
            });
          }
        });
      });

      // k Mean clustering
      Flow.registerNode(
        'standard-entity',
        {
          label: 'standard-entity',
          color_type: '#1890FF',
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

      // Random forest
      Flow.registerNode(
        'custom-entity',
        {
          label: 'Custom Entity',
          color_type: '#E7B554',
          type_icon_url: DatabaseCustomEntityIcon,
          //  Set anchor point
          anchor: [
            [0.5, 0], // Midpoint above
            [1, 0.5], // Midpoint left
            [0.5, 1], // Midpoint of the bottom
            [0, 0.5], // Midpoint of the right
          ],
        },
        AppConstants.GRAPH_NODE_SHAPES.BASE
      );

      // PS-SMART classification
      Flow.registerNode(
        'PS-SMART',
        {
          label: 'PS-SMART',
          color_type: '#1890FF',
          type_icon_url: DatabaseEntityIcon,
          //  Set anchor point
          anchor: [
            [
              0.5,
              0,
              {
                type: 'input',
              },
            ],
            [
              0.33,
              1,
              {
                type: 'output',
              },
            ],
            [
              0.66,
              1,
              {
                type: 'output',
              },
            ],
          ],
        },
        'model-card'
      );

      //  Naive Bayes
      Flow.registerNode(
        'BayesNow',
        {
          label: 'bayes',
          color_type: '#FF0000',
          type_icon_url: DatabaseEntityIcon,
          //  Set anchor point
          anchor: [
            [
              0.5,
              0,
              {
                type: 'input',
              },
            ],
            [
              0.5,
              1,
              {
                type: 'output',
              },
            ],
          ],
        },
        'model-card'
      );

      // Straight line edge
      Flow.registerEdge('line-arrow', {
        itemType: 'edge',
        draw(cfg: any) {
          const keyShape = cfg.group.addShape('path', {
            attrs: {
              path: this.getPath(cfg),
              stroke: AppConstants.EDGE_COLOR.PIPELINE,
              lineWidth: 1,
              startArrow: {
                path: 'M 10,0 L -10,-10 L -10,10 Z',
                d: 10,
              },
              endArrow: {
                path: 'M 10,0 L -10,-10 L -10,10 Z',
                d: 10,
              },
            },
          });
          return keyShape;
        },
      });

      Flow.registerEdge(
        'bi-direction-arrow',
        {
          getStyle(item: any) {
            return {
              stroke: AppConstants.EDGE_COLOR.PIPELINE,
              strokeOpacity: 0.92,
              lineWidth: 1,
              lineAppendWidth: 8,
              endArrow: true,
              startArrow: true,
            };
          },
        },
        'flow-smooth'
      );

      registerEntityNode();
      registerSyncariCircle(config);
      registerSyncariCircleWithIntro();
      registerConnector(config);
      registerNodeKebab(config);

      resolve();
    } catch (err) {
      reject(err);
    }
  });
