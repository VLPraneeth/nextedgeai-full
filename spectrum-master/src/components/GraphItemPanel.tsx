import cx from 'classnames';
import * as React from 'react';
import { useEffect, useRef } from 'react';
import G6Editor from 'sg6-editor';

import './GraphItemPanel.less';

function createItemPanel(container: HTMLElement) {
  return new G6Editor.Itempanel({ container });
}

export interface GraphItemPanelProps {
  editor: typeof G6Editor;
  className?: string;
  renderGraph?: boolean;
  children?: React.ReactNode;
}

const GraphItemPanel = ({ className, children, editor, renderGraph = true }: GraphItemPanelProps) => {
  const itemPanelContainerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (itemPanelContainerRef?.current) {
      const itemPanel = createItemPanel(itemPanelContainerRef.current);
      if (renderGraph) {
        editor.add(itemPanel);
      }
    }
  }, [editor, renderGraph]);

  return (
    <div className={cx('flow-right-content', className)} ref={itemPanelContainerRef}>
      {children}
    </div>
  );
};

export default GraphItemPanel;
