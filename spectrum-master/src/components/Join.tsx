import * as React from 'react';

interface JoinProps {
  delimiter: React.ReactNode;
  children?: React.ReactNode;
}

const Join = ({ children, delimiter }: JoinProps) => {
  const kidCount = React.Children.count(children);
  return (
    <>
      {React.Children.map(children, (child, idx) => {
        if (idx === kidCount - 1) {
          return <>{child}</>;
        }

        return (
          <>
            <span>{child}</span>
            <span>{delimiter}</span>
          </>
        );
      })}
    </>
  );
};

export default Join;
