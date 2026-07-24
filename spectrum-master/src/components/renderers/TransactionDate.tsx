//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import moment from 'moment';

export default function TransactionDate({ text: dateText }: any) {
  if (dateText) {
    return <div>{moment(dateText).format('MMM Do YYYY, h:mm:ss A')}</div>;
  }

  return null;
}

export const rendererWrapper = (value: any) => <TransactionDate text={value} />;
