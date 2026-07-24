import { List } from 'antd';
import { CSSProperties } from 'react';

import InputWithLabel from 'components/inputs/InputWithLabel';
import { Stack } from 'components/layout';

interface ListWithSearchProps {
  label: string;
  listItems: (string | undefined)[];
  style?: CSSProperties;
  placeholder?: string;
  className?: string;
}

// removing search for now

function ListWithSearch({ label, listItems, placeholder, style, className }: ListWithSearchProps) {
  return (
    <Stack>
      <InputWithLabel label={label} input={<div />} />
      <div className="add-role__list">
        <List
          className={className}
          style={style}
          dataSource={listItems}
          renderItem={(item) => <List.Item>{item}</List.Item>}
        />
      </div>
    </Stack>
  );
}

export default ListWithSearch;
