//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { render } from '@testing-library/react';

import { Step, Steps } from 'components';

describe('Steps', () => {
  // Doing a snapshot testing since the lower level
  // component rc-steps throwing an error on rtl
  it('Steps renders without problem', () => {
    const { asFragment } = render(
      <Steps direction="vertical">
        <Step title="Basic Settings" key="basicSettings" />
        <Step title="Pipeline Settings" key="pipelineSettings" />
        <Step title="Review" key="review" />
      </Steps>
    );
    expect(asFragment()).toMatchSnapshot();
  });
});
