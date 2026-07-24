// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Fragment } from 'react';

function TransactionRecordIdRenderer(props) {
  const { text: colValue } = props;

  if (colValue !== undefined) {
    return (
      <Fragment>
        {colValue.map((element) => (
          <span key={`connector-${colValue.externalId}`}>
            {element['externalId']}
            <br />
          </span>
        ))}
      </Fragment>
    );
  }

  return null;
}

export const rendererWrapper = (value, data, index) => <TransactionRecordIdRenderer text={value} />;

export default TransactionRecordIdRenderer;
