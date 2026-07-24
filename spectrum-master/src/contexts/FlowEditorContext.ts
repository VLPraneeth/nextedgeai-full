//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { createContext } from 'react';

export default createContext<{
  editorReady: boolean;
  selectedModel: Record<string, any>;
  currentZoom: number;
  editor: any;
} | null>(null);
