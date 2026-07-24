//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import classNames from 'classnames';
import './ListItemRenderer.less';

export default function ListItemRenderer(props: any) {
  const { className, text, record } = props;
  const cls = classNames('synri-list-item-renderer', className);
  let el = text;
  if (record.url) {
    el = (
      <a href={record.url} target="_blank" rel="noopener noreferrer">
        {text}
      </a>
    );
  }
  return (
    <ul className={cls}>
      <li>{el}</li>
    </ul>
  );
}
