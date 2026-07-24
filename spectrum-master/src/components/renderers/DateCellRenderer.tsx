import Tooltip from 'antd/lib/tooltip';

import useUserLocalMoment, { ISO_8601, Moment } from 'hooks/moment';
import { SHORT_DATE_TIME_FORMAT } from 'utils/DateUtil';
import { tNamespaced } from 'utils/i18nUtil';

import './DateCell.less';

const tn = tNamespaced('TableCellRenderers.DateCell');

interface DateCellProps {
  date: Moment;
  fallbackText?: string;
}

const DateCell = ({ date, fallbackText = '' }: DateCellProps) => {
  const userMoment = useUserLocalMoment();

  try {
    const dateStr = userMoment(date, ISO_8601).format(SHORT_DATE_TIME_FORMAT);

    if (dateStr === 'Invalid date') {
      return <span className="synri-date-fallback-text">{fallbackText}</span>;
    }

    return <Tooltip title={dateStr}>{dateStr}</Tooltip>;
  } catch (err) {
    console.log('ERR', err);
    return <>{date}</>;
  }
};

interface DateCellRendererConfig {
  /*
   * this is weird, but we need to delay tn resolution until render time and not import time :/
   * this allows you to pass any fn that will return your text
   */
  fallbackText?: () => string | string;
}

/** factory for dateCellRenderers */
const makeDateCellRendererWithConfig = ({ fallbackText }: DateCellRendererConfig = {}) => (date: Moment) => {
  const text = typeof fallbackText === 'function' ? fallbackText() : fallbackText;

  return <DateCell date={date} fallbackText={text} />;
};

/** default renderer, no fallback text */
const DefaultDateCellRenderer = makeDateCellRendererWithConfig();

/** renderer for last updated cells, shows "Never Updated" as fallback */
const LastUpdatedDateCellRenderer = makeDateCellRendererWithConfig({
  fallbackText: () => tn('never_updated'),
});

export default DefaultDateCellRenderer;
export { makeDateCellRendererWithConfig, LastUpdatedDateCellRenderer };
