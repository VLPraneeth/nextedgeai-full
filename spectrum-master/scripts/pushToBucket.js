/**
 * Push the built assets to CDN.
 *
 * Required environment variables:
 * SYNCARI_ASSET_CDN = Url assets will be served from
 * CDN_BUCKET = GCP bucket the assets will be pushed and pulled from
 * GCP_CREDENTIALS_KEY = credentials use for pushing the assets to the CDN bucket
 * GCP_PROJECT = project the bucket is located
 */

const fs = require('fs');

const { Storage } = require('@google-cloud/storage');

const {
  CDN_BUCKET,
  SYNCARI_ASSET_CDN,
  GCP_CREDENTIALS_KEY,
  GCP_PROJECT,
  isCdnBuild,
  SPECTRUM_SUB_DIR,
} = require('../config/workbox');

// Directories that will be pushed to CDN
const ASSET_DIR = ['css', 'js', 'media'];

const GCP_KEY_FILENAME = 'gcp.json';

// GCP bucket setup for CDN

const initializeBucket = () => {
  console.log(`Pushing the assets to the bucket: ${CDN_BUCKET}`);
  console.log(`Assets will be available here: ${SYNCARI_ASSET_CDN}`);

  // create the temporary key file
  fs.writeFileSync(GCP_KEY_FILENAME, Buffer.from(GCP_CREDENTIALS_KEY, 'base64').toString());

  const storage = new Storage({
    projectId: GCP_PROJECT,
    keyFilename: GCP_KEY_FILENAME,
  });

  return storage.bucket(CDN_BUCKET);
};

const pushToBucket = async (directoryPath, bucketSubDir) => {
  const bucketBasePath = `${SPECTRUM_SUB_DIR}/${bucketSubDir}`;

  const bucket = initializeBucket();

  ASSET_DIR.forEach((directory) => {
    const fullDir = `${directoryPath}/static/${directory}`;
    let files = fs.readdirSync(fullDir);
    files.forEach(async (file) => {
      // Skip map files
      if (file.match(/.*map$/)) {
        return;
      }
      const fullPath = `${fullDir}/${file}`;
      const bucketPath = `${bucketBasePath}/static/${directory}/${file}`;

      try {
        await bucket.upload(fullPath, { destination: bucketPath });
      } catch (error) {
        console.error('Error uploading file to bucket.', error);
        process.exit(1);
      }
      console.log(`Done pushing ${fullPath} to ${bucketPath}`);
    });
  });
};

module.exports = {
  pushToBucket,
};

// Remove the temporary key file
process.on('exit', () => isCdnBuild() && fs.unlinkSync(GCP_KEY_FILENAME));
