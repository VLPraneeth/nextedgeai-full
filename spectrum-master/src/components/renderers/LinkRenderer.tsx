//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Link } from '@reach/router';
import cx from 'classnames';
import * as React from 'react';

import './LinkRenderer.scss';

export type LinkRendererProps = {
  className?: string;
  text: string;
  children?: React.ReactNode;
  url?: string;
};

export const LinkRenderer = ({ className, children, text, url }: LinkRendererProps) => {
  const cls = cx('synri-link-renderer', className);

  if (!url) {
    return (
      <span className={cls}>
        {text}
        {children}
      </span>
    );
  }

  return (
    <Link className={cls} to={url}>
      {text}
      {children}
    </Link>
  );
};

type MaybeRecordWithLink = { url?: string; link?: string };

export type RecordLinkRendererProps<
  RecordItem extends MaybeRecordWithLink = MaybeRecordWithLink
> = LinkRendererProps & {
  record: RecordItem;
};

const RecordLinkRenderer = ({ record, ...props }: RecordLinkRendererProps) => {
  const url = record.url || record.link;

  return <LinkRenderer url={url} {...props} />;
};

export default RecordLinkRenderer;
