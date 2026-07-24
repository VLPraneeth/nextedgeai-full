//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

/**
 * Returns true if Cmd or Ctrl are depressed for a keyboard event
 */
export const isCmdOrCtrlPressed = (event?: KeyboardEvent | MouseEvent | React.MouseEvent) =>
  event?.metaKey || event?.ctrlKey;
