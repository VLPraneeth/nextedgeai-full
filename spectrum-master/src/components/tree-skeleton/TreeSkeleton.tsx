import cx from 'classnames';
import classNames from 'classnames';
import { keyBy, mapValues } from 'lodash';
import { useEffect, useState } from 'react';
import * as React from 'react';

import usePreviousValue from 'hooks/usePreviousValue';
import useSetState from 'utils/useSetState';

export interface TreeItem {
  key: string;
  label: React.ReactNode;
  children: React.ReactNode;
  hidden?: boolean;
}

export interface BorderOptions {
  labelBottom?: boolean;
  contentBottom?: boolean;
}

export interface TreeSkeletonProps {
  items: TreeItem[];
  filterActive?: boolean;
  defaultOpen?: boolean;
  timeline?: boolean;
  borderOptions?: BorderOptions;
  // Pixel count to adjust left icons with label height
  expandIconsOffset?: number;
}

const defaultBorderOptions: BorderOptions = {
  labelBottom: true,
  contentBottom: true,
};

const TreeSkeleton = ({
  items,
  filterActive = false,
  defaultOpen = false,
  timeline = false,
  borderOptions,
  expandIconsOffset = 6,
}: TreeSkeletonProps) => {
  const borderSettings = { ...defaultBorderOptions, ...borderOptions };
  const [expandedItems, setExpandedItems] = useSetState(() => mapValues(keyBy(items, 'key'), () => defaultOpen));
  const [previousExpandedItems, setPreviousExpandedItems] = useState(expandedItems);

  const previousFilterActive = usePreviousValue(filterActive);

  // Store the expanded items when the top level filter is active so we can
  // restore it after clearing the filter
  useEffect(() => {
    const filterStatusChanged = previousFilterActive !== filterActive;
    if (filterStatusChanged) {
      if (filterActive) {
        setPreviousExpandedItems(expandedItems);
        setExpandedItems(mapValues(expandedItems, () => true));
      } else {
        setExpandedItems(previousExpandedItems);
      }
    }
  }, [expandedItems, filterActive, previousExpandedItems, previousFilterActive, setExpandedItems]);

  return (
    <div className="synri-tree-skeleton-container">
      {items.map(({ label, key, children, hidden }, index) => {
        const open = expandedItems[key];
        const isLastItem = index + 1 === items.length;
        const showExpandBorder = !isLastItem || open;

        return (
          <div
            key={key}
            className={classNames(
              'synri-tree-skeleton-item-container',
              // We use the hidden flag to set `display: none` on items that
              // don't match filter results but we still want rendered to
              // preserve internal state (like the filter)
              hidden && 'synri-tree-skeleton-item-container-hidden'
            )}>
            <div className="synri-tree-skeleton-expansion-container" style={{ top: expandIconsOffset }}>
              <button
                aria-label={open ? 'collapse-branch' : 'expand-branch'}
                className="synri-tree-skeleton-expand-button"
                onClick={() => setExpandedItems({ [key]: !open })}>
                <div className={cx('synri-tree-skeleton-expand-icon', timeline && 'timeline')}>
                  <span className="synri-tree-skeleton-expand-icon-text">{open ? '-' : '+'}</span>
                </div>
              </button>
              <div
                className={cx(
                  'synri-tree-skeleton-expand-border',
                  showExpandBorder && 'synri-tree-skeleton-expand-border-show',
                  timeline && 'timeline'
                )}
              />
            </div>
            <div className="synri-tree-skeleton-container">
              <div
                className={cx(
                  'synri-tree-skeleton-label',
                  borderSettings.labelBottom && 'synri-tree-skeleton-label-border-bottom'
                )}>
                {label}
              </div>
              {open && (
                <>
                  {children}
                  <div className={cx(borderSettings.contentBottom && 'synri-tree-skeleton-body-bottom-border')} />
                </>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
};

export default TreeSkeleton;
