//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { cloneDeep } from 'lodash';
import G6Editor from 'sg6-editor';

import { setNodeForKebabMenu } from 'store/app/actions';
import { Node } from 'store/pipeline/types';

const { Flow } = G6Editor;

export const CONNECTOR_NODE_KEBAB = 'connectorNodeKebab';
export const GROUP_NODE_KEBAB = 'groupNodeKebab';
export const NODE_KEBAB = 'nodeKebab';
export const EDGE_OPTIONS = 'edgeOptions';

const KEBAB_SECTIONS = [CONNECTOR_NODE_KEBAB, GROUP_NODE_KEBAB, NODE_KEBAB, EDGE_OPTIONS];

export const isKebabSection = (section?: string) => {
  return section && KEBAB_SECTIONS.includes(section);
};

// Add the behavior to show the menu when clicking on a kebab on a node in the
// graph. Can open various menus including connector or group menu.
const registerNodeKebab = (config: { dispatch: any }) => {
  const { dispatch } = config;

  Flow.registerBehaviour('nodeKebabMenu', function (page: any) {
    var graph = page.getGraph();

    function getKebabMenu() {
      return document.getElementById('nodeKebabMenu');
    }

    function showMenu(evt: any) {
      const nodeKebabMenu = getKebabMenu();

      const userHoldingShift = evt.domEvent.shiftKey;

      // Don't open any menus while the user is holding shift
      if (nodeKebabMenu && !userHoldingShift) {
        nodeKebabMenu.style.top = `${evt.domEvent.clientY}px`;
        nodeKebabMenu.style.left = `${evt.domEvent.clientX}px`;

        const section = evt?.shape?._cfg?.attrs?.section;
        const model = cloneDeep(evt.item.getModel());

        if (section === CONNECTOR_NODE_KEBAB) {
          dispatch(
            setNodeForKebabMenu({
              nodeType: 'connector',
              connector: model,
            })
          );
        } else if (section === GROUP_NODE_KEBAB) {
          dispatch(
            setNodeForKebabMenu({
              nodeType: 'group',
              group: model,
            })
          );
        } else if (section === NODE_KEBAB) {
          // Convert the node model to match the Node type
          const node: Node = { ...model, groupId: model.parent };
          dispatch(
            setNodeForKebabMenu({
              nodeType: 'node',
              node,
            })
          );
        } else if (section === EDGE_OPTIONS) {
          nodeKebabMenu.style.left = `${evt.domEvent.clientX - 48}px`;
          nodeKebabMenu.style.top = `${evt.domEvent.clientY + 25}px`;

          const sourceEdge = graph.getEdges().find((edge: any) => edge.target.id === model.id);
          if (sourceEdge) {
            const sourceFunctionId = sourceEdge?.source?.model?.metadata?.configuration?.configId;

            if (sourceFunctionId) {
              dispatch(
                setNodeForKebabMenu({
                  nodeType: 'predicate-node',
                  node: model,
                  sourceFunctionId,
                  sourceConfiguration: sourceEdge?.source?.model?.metadata?.configuration,
                })
              );
            }
          }
        }

        nodeKebabMenu.click();
      }
    }

    graph.behaviourOn('mouseenter', function (evt: any) {
      if (isKebabSection(evt?.shape?._cfg?.attrs?.section)) {
        page.css({ cursor: 'pointer' });
      }
    });

    graph.behaviourOn('click', function (evt: any) {
      if (isKebabSection(evt?.shape?._cfg?.attrs?.section)) {
        showMenu(evt);
      }
    });
  });
};

export default registerNodeKebab;
