import { Col, Row } from 'antd';

import InputWithLabel from 'components/inputs/InputWithLabel';
import { SkullInput } from 'components/skull';

export type SkullColumnsType = {
  span: number;
  items: SkullInput[];
}[];

export interface SkullColumnsProps {
  columns: SkullColumnsType;
}

const SkullColumns = ({ columns, ...props }: SkullColumnsProps) => {
  return (
    <Row gutter={32}>
      {columns.map((columnInputs, index) => {
        return (
          <Col key={index} span={columnInputs.span}>
            {columnInputs.items.map((column) => {
              return (
                <div key={column.id}>
                  <InputWithLabel {...props} {...column} />
                </div>
              );
            })}
          </Col>
        );
      })}
    </Row>
  );
};
//
export default SkullColumns;
