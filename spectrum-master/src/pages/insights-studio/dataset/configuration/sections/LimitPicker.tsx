import { ChangeEvent } from 'react';

import InputWithLabel from 'components/inputs/InputWithLabel';
import { useUnifiedDataCardAuthoring } from 'pages/insights-studio/utils/useUnifiedDataCardAuthoring';

export const LimitPicker = () => {
  const { limit, setLimit } = useUnifiedDataCardAuthoring();

  return (
    <div style={{ width: '20%' }}>
      <InputWithLabel
        datatype="string"
        defaultValue={limit}
        value={limit}
        onChange={(evt: ChangeEvent<HTMLInputElement>) => {
          setLimit(evt.target.value);
        }}
      />
    </div>
  );
};
