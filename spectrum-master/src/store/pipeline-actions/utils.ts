//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { PipelineAction } from './types';

const DEFAULT_ICON_PATH = '/assets/icons/actions/generic-action.svg';

export function getResponsePipelineActions(data: PipelineAction[]): PipelineAction[] {
  return data.map((action) => ({
    ...action,
    iconPath: action.iconPath || DEFAULT_ICON_PATH,
  }));
}
