export const waitForWindowClose = (win: Window): Promise<void> => {
  return new Promise((resolve) => {
    const intervalId = setInterval(() => {
      if (win.closed) {
        clearInterval(intervalId);
        resolve();
      }
    }, 1000);
  });
};
