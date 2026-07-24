import G6Editor from 'sg6-editor';

import { UNSELECTABLE_NODES } from 'pages/sync-studio/pipeline/PipelineEditor.constants';

export const createPage = (container: any) => {
  return new G6Editor.Flow({
    graph: {
      container,
      mode: 'default',
      modes: {
        default: [
          'panBlank',
          'hoverGroupActived',
          'clickEdgeSelected',
          'clickNodeSelected',
          'clickCanvasSelected',
          'clickGroupSelected',
          'hoverNodeActived',
          'hoverEdgeActived',
          'hoverButton',
          'clickCollapsedButton',
          'clickExpandedButton',
          'keydownShiftMultiSelected',
          'dragNodeAddToGroup',
          'dragOutFromGroup',
          'panItem',
          'hoverEdgeControlPoint',
          'dragEdgeControlPoint',
          'doubleClickConnector',
          'nodeKebabMenu',
          'expandCollapseGroup',
          'pointerStatusHover',
          'selectableNode',
          'nodeError',
        ],
        updateOnly: [
          'panBlank',
          'clickNodeSelected',
          'clickGroupSelected',
          'clickCanvasSelected',
          'panItem',
          'expandCollapseGroup',
          'pointerGotoPipeline',
        ],
        readSelectNodeOnly: [
          'panCanvas',
          'expandCollapseGroup',
          'clickNodeSelected',
          'clickGroupSelected',
          'clickCanvasSelected',
        ],
        updateOnlyConnector: [
          'panBlank',
          'clickNodeSelected',
          'clickGroupSelected',
          'clickCanvasSelected',
          'panItem',
          'doubleClickConnector',
          'doubleClickSyncariCircle',
          'nodeKebabMenu',
          'expandCollapseGroup',
          'pointerStatusHover',
        ],
        readCheckNodeOnly: ['panCanvas', 'expandCollapseGroup', 'clickCanvasSelected', 'selectableNode'],
        dragSelect: [
          'dragMultiSelect',
          'hoverGroupActived',
          'clickNodeSelected',
          'clickCanvasSelected',
          'clickGroupSelected',
          'keydownShiftMultiSelected',
          'nodeKebabMenu',
          'expandCollapseGroup',
          'nodeError',
        ],
      },
    },
    align: {
      grid: true,
    },
  });
};

export const enableNodeCheck = (enable: boolean, editor: any) => {
  // Iterate through the graph and enable the mode
  const page = editor?.getCurrentPage();
  const nodes = page?.getNodes();
  nodes &&
    nodes.forEach((node: any) => {
      editor.executeCommand(() => {
        const page = editor.getCurrentPage();
        const item = page.find(node.id);
        if (item && !UNSELECTABLE_NODES.includes(item.model.nodeType)) {
          page.update(item, {
            selectableNode: enable,
            // Default unchecked
            checkedNode: false,
          });
        }
      });
    });
};
