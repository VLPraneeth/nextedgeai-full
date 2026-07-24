/**
 * Update the manifest to pull the assets from CDN.
 */

// Subdirectory in the bucket that contains all spectrum built assets
const SPECTRUM_SUB_DIR = 'spectrum-assets';

// Url in which the assets will be served from
const SYNCARI_ASSET_CDN = process.env.SYNCARI_ASSET_CDN;

// GCP storage bucket the assets will be pushed. This bucket should be configured for CDN
const CDN_BUCKET = process.env.CDN_BUCKET;

// Build gcp credentials. Credential need to have object.storage.create permission
const GCP_CREDENTIALS_KEY = process.env.GCP_CREDENTIALS_KEY;

// Project in GCP
const GCP_PROJECT = process.env.GCP_PROJECT;

const manifestTransform = (originalManifest) => {
  if (!isCdnBuild()) {
    console.log('Environment variables for CDN build not found. Continuing with a local build.');
    return { manifest: originalManifest };
  }
  // Assets will be loaded from this path
  const CDN_PREFIX = `${SYNCARI_ASSET_CDN}/${SPECTRUM_SUB_DIR}/${process.env.BUILD_NOW}`;

  const manifest = originalManifest
    .map((entry) => {
      // Replace the manifest entry with our cdn URL
      entry.url = `${CDN_PREFIX}${entry.url}`;
      return entry;
    })
    .filter(Boolean);

  return { manifest };
};

const isCdnBuild = () => SYNCARI_ASSET_CDN && CDN_BUCKET && GCP_CREDENTIALS_KEY && GCP_PROJECT;

module.exports = {
  manifestTransform,
  isCdnBuild,
  SPECTRUM_SUB_DIR,
  SYNCARI_ASSET_CDN,
  CDN_BUCKET,
  GCP_CREDENTIALS_KEY,
  GCP_PROJECT,
};
