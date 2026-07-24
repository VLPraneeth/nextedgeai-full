//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import moment from 'moment';

const DATE_FORMAT = 'L';
const TIME_FORMAT = 'h:mmA';
const DATE_AND_TIME_FORMAT = `${DATE_FORMAT} ${TIME_FORMAT}`;

export default function DateTimeRenderer({ text: dateText }: any) {
  if (dateText) {
    return <div>{moment(dateText).format(DATE_AND_TIME_FORMAT)}</div>;
  }

  return null;
}
