import { Storage } from '@google-cloud/storage';

import { getConfigVariables } from 'utils/ConfigUtil';

const { gcsBucketName, gcsPathPrefix, gcpCredentialsKey } = getConfigVariables();

const getBucket = () => {
  // GCS Storage client
  const storage = new Storage({
    keyFilename: gcpCredentialsKey,
  });

  return storage.bucket(gcsBucketName);
};

/**
 * getFileUrl
 * constructs the proper URL for a public file on GCS using the
 * bucketname configured
 *
 * @param {string} fileName
 * @returns {string}
 */
const getFileUrl = (fileName) => `https://storage.googleapis.com/${gcsBucketName}/${fileName}`;

/**
 * makeFilePath
 * creates a filepath using the configured path prefix
 *
 * @param {string} fileName
 * @returns {string}
 */
const makeFilePath = (fileName) => `${gcsPathPrefix}/${fileName}`;

export { getBucket, getFileUrl, makeFilePath };
