import cx from 'classnames';
import * as React from 'react';

import './ModalTable.less';

function ModalTable({ className, flex, ...props }: React.HTMLAttributes<HTMLTableElement> & { flex?: boolean }) {
  return <table className={cx('syncari-modal-table', { 'as-flex': flex, padded: !flex }, className)} {...props} />;
}

function makeTableComponent<E, P extends React.HTMLAttributes<E> = React.HTMLAttributes<E>>(tag: string) {
  return React.forwardRef(({ className, children, ...props }: P, ref) =>
    React.createElement(tag, { ref, className: cx(`syncari-modal-table-${tag}`, className), ...props }, children)
  );
}

export const THead = makeTableComponent<HTMLDivElement>('thead');
ModalTable.THead = THead;

export const TBody = makeTableComponent<HTMLDivElement>('tbody');
ModalTable.TBody = TBody;

export const TFoot = makeTableComponent<HTMLDivElement>('tfoot');
ModalTable.TFoot = TFoot;

export const TH = makeTableComponent<HTMLTableHeaderCellElement>('th');
ModalTable.TH = TH;

export const TR = makeTableComponent<HTMLTableRowElement>('tr');
ModalTable.TR = TR;

export const TD = makeTableComponent<HTMLTableDataCellElement>('td');
ModalTable.TD = TD;

export default ModalTable;
