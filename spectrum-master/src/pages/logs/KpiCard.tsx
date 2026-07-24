//
// Copyright (c) 2021 Syncari All rights reserved.
//
import { Col } from 'antd';
import Card, { CardProps } from 'antd/lib/card';
import Spin from 'antd/lib/spin';
import Tooltip from 'antd/lib/tooltip';

import SIcon from 'components/icons/SIcon';
import { useIsTextTruncated } from 'components/typography';

import './Transactions.less';

export interface KpiCardProps extends CardProps {
  title?: string | number;
  subtitle: string;
  iconSrc: string;
  isLoading?: boolean;
  colWidth?: number;
}

const KpiCard = ({ title = '——', subtitle, iconSrc, isLoading = false, colWidth = 4 }: KpiCardProps) => {
  const [ref, isTruncated] = useIsTextTruncated<HTMLDivElement>();

  return (
    <Col span={colWidth} className="transaction-card">
      <Card className="transaction-stats">
        <div className="transaction-icon">
          <SIcon src={iconSrc} size={SIcon.SIZE.L_LARGE} />
        </div>
        <div ref={ref} key={`${title}-${isLoading ? 'loading' : ''}`} className="transaction-title">
          {isLoading ? (
            <Spin size="small" spinning={isLoading} />
          ) : isTruncated ? (
            <Tooltip title={title}>{title}</Tooltip>
          ) : (
            title
          )}
        </div>
        <div className="transaction-subtitle">{subtitle}</div>
      </Card>
    </Col>
  );
};

export default KpiCard;
