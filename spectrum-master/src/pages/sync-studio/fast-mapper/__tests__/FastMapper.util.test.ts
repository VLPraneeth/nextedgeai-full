//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { isMappingEmpty } from '../FastMapper.util';

describe('FastMapper.util.test', () => {
  it('should validate empty mappings', () => {
    expect(isMappingEmpty([])).toBe(true);
    expect(
      isMappingEmpty([
        {
          id: 'xyz1234',
          synapseId: '',
          synapseEntityId: '',
          synapseFieldId: '',
          syncDirectionId: '',
          syncariFieldId: '',
        },
      ])
    ).toBe(true);
    expect(
      isMappingEmpty([
        {
          id: 'xyz1234',
          synapseId: 'abcd1234',
          synapseEntityId: '',
          synapseFieldId: '',
          syncDirectionId: '',
          syncariFieldId: '',
        },
      ])
    ).toBe(false);
  });
});
