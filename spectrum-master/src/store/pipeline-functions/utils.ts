//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { ArcadePipelineFunction, PipelineFunction } from './types';

const DEFAULT_ICON_PATH = '/assets/icons/functions/generic-function.svg';

export function getResponsePipelineFunctions(data: ArcadePipelineFunction[]): PipelineFunction[] {
  return data.map((func) => ({
    ...func,
    iconPath: func.iconPath || DEFAULT_ICON_PATH,
    title: func.name,
    key: func.id,
    iconAlt: func.name,
    icon: func.iconPath || DEFAULT_ICON_PATH,
  }));
}
