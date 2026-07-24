import { ReactComponent as WarningSign } from 'assets/icons/warning-sign.svg';

import './EmptyPanelContent.less';

export interface EmptyPanelContentProps {
  title?: string;
  body?: string;
  icon?: React.ReactNode;
  children?: React.ReactNode;
}

export const EmptyPanelContent = ({ title, body, children, icon }: EmptyPanelContentProps) => {
  return (
    <div className="empty-panel-content">
      <div className="empty-panel-content__icon">{icon ?? <WarningSign width={32} />}</div>
      <div className="empty-panel-content__title">{title}</div>
      {body && <div className="empty-panel-content__body">{body}</div>}
      <div className="empty-panel-content__children">{children}</div>
    </div>
  );
};
