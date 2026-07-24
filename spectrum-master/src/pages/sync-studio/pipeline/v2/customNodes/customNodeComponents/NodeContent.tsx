import { ReactNode } from 'react';

import { HStack } from 'components/layout';

import './NodeContent.scss';

export const NodeContentContainer = ({ children }: { children: ReactNode }) => {
  return <div className="pipeline-node-content">{children}</div>;
};

export interface NodeContentProps {
  header: string;
  label?: string;
  icon?: ReactNode;
}

export const NodeContentBody = ({ header, label, icon }: NodeContentProps) => {
  return (
    <>
      <HStack spacing="xxs">
        {Boolean(icon) && icon}
        <span className="pipeline-node-content__label">{header}</span>
      </HStack>
      <span className="pipeline-node-content__sublabel">{label || '-'}</span>
    </>
  );
};

const NodeContent = (props: NodeContentProps) => {
  return (
    <NodeContentContainer>
      <NodeContentBody {...props} />
    </NodeContentContainer>
  );
};

export default NodeContent;
