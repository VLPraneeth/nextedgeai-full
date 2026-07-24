import ObjectID from 'bson-objectid';

import { post } from 'utils/AjaxUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { compose } from 'utils/Fp';

const browserInfo = (function () {
  return {
    userAgent: window.navigator?.userAgent,
    screenSizeW: window.innerWidth,
    screenSizeH: window.innerHeight,
  };
})();

// tryCatch :: Func -> Func -> Func -> a
const tryCatch = (tryFn: any, catchFn: any) => (...args: any) => {
  try {
    return tryFn(...args);
  } catch (err) {
    return catchFn(...args);
  }
};

// stringify :: a -> String
const stringify = (x: any) => JSON.stringify(x, null, 2);

// toB64 :: String -> String'
const toB64 = (str: string) => {
  // Convert Unicode string to UTF-8 bytes, then to base64
  const encoder = new TextEncoder();
  const bytes = encoder.encode(str);
  const binaryString = Array.from(bytes, (byte) => String.fromCharCode(byte)).join('');
  return btoa(binaryString);
};

// packageData :: a -> String
const packageData = (data: any) => {
  const stringified = stringify(data);

  try {
    const base64Result = toB64(stringified);
    return base64Result;
  } catch (error) {
    return '';
  }
};

const phoneHome = ({ error, componentStack = '', state = {}, actions = [], ...moreData }: any) => {
  if (process.env.NODE_ENV !== 'production') {
    console.log(error);
    console.group('ERROR: In dev, Phone Home caught');
    console.trace();
    console.groupEnd();

    // return a promise to be consistent with production
    return Promise.resolve({});
  } else {
    return post(DataUrlConstants.PHONE_HOME, {
      phoneHomeId: ObjectID.generate(),
      errorMessage: error?.toString(),
      errorStack: packageData(error?.stack),
      state: packageData(error?.allState || state),
      actions: packageData(error?.lastActions || actions),
      componentStack: packageData(componentStack),
      browserInfo,
      url: window.location.href,
      originalStack: '',
      blackboxUrl: '',
      ...moreData,
    });
  }
};

export { phoneHome, packageData };
