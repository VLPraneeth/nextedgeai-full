//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Fragment } from 'react';

export default function TransactionOriginalSources(props: any) {
  const { text: colValue } = props;

  if (colValue !== undefined) {
    return (
      <Fragment>
        {colValue.map((element: any) => (
          <span key={`connector-${element.connectorId}`}>
            {element['connectorName']}
            <br />
          </span>
        ))}
      </Fragment>
    );
  }

  return null;
}

export const rendererWrapper = (value: any) => <TransactionOriginalSources text={value} />;
