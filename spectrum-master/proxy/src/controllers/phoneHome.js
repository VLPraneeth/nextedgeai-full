import fs from 'fs';
import path from 'path';

import axios from 'axios';
import { json, Router } from 'express';
import yazl from 'yazl';

import { getConfigVariables } from 'utils/ConfigUtil';
import { append, compose, map, prop, unique } from 'utils/Fp';
import { getBucket, makeFilePath } from 'utils/gcs';
import { logger } from 'utils/LogUtil';
import { getFramesFlow, getOriginalFrames, getOriginalStackString } from 'utils/PhoneHome';

const { spectrumPort } = getConfigVariables();

const PHONE_HOME_URL = `http://localhost:${spectrumPort}/arcade/api/v1/application/phoneHome`;
const getSevenDaysFromNow = () => new Date(Date.now() + 604800000).getTime();

const getFilesList = compose(
  map((f) => fs.realpathSync(f)),
  unique,
  map(prop('file'))
);
const getSourceMapList = map(append('.map'));

// call Arcade with our enhanced PhoneHome payload
const callArcadePhoneHome = ({ phoneHomeId, phoneHomeData, req }) =>
  axios.post(
    PHONE_HOME_URL,
    {
      phoneHomeId,
      ...phoneHomeData,
    },
    {
      headers: req.headers,
    }
  );

/*
 * Uploads zip file to GCS containing,
 * - relevant scripts and maps
 * - Meta file containing the phoneHome data that was used
 *
 */
const uploadDataToGcs = async (id, data) => {
  const bucket = getBucket();

  try {
    // this will have scripts + maps
    const dataFile = bucket.file(makeFilePath(`${id}_files.zip`));

    // extract frames from the errorStack base64
    const frames = getFramesFlow(data.errorStack);
    // for each frame, extract the original data from the sourcemaps
    const originalFrames = await getOriginalFrames(frames);
    // convert back into a string based stack for uploading
    const originalFramesString = getOriginalStackString(originalFrames);

    const scripts = getFilesList(frames);
    const sourceMaps = getSourceMapList(scripts);

    // create a zip file containing,
    // - relevant scripts
    // - matching sourceMaps
    // - JSON data from Phone Home
    const zipFile = new yazl.ZipFile();

    // add Phone Home data to zip
    zipFile.addBuffer(
      Buffer.from(JSON.stringify({ ...data, originalStack: originalFramesString }, null, 2)),
      'phoneHome.json'
    );

    // add our scripts + maps
    [...scripts, ...sourceMaps].forEach((f) => {
      zipFile.addFile(f, path.basename(f));
    });

    // we're done adding data
    zipFile.end();

    // write zip to GCS
    zipFile.outputStream.pipe(dataFile.createWriteStream({ resumable: false }));

    // return URL to download the file directly
    const [signedUrl] = await dataFile.getSignedUrl({
      action: 'read',
      expires: getSevenDaysFromNow(),
      virtualHostedStyle: true,
    });

    return {
      ...data,
      originalStack: originalFramesString,
      blackboxUrl: signedUrl,
    };
  } catch (error) {
    logger.error(`Failure retrieving or uploading Data Files for Phone Home to GCS: ${error.toString()}`, { error });
  }

  return {
    ...data,
    originalStack: '',
    blackboxUrl: '',
  };
};

const router = Router();

router
  .use(json())
  .route('/')
  .post((req, res) => {
    const { phoneHomeId, ...phoneHomeData } = req.body;

    res.send(JSON.stringify({}));

    // this is an async function so that it's processed after
    // responding to the client and doesn't block req/res
    uploadDataToGcs(phoneHomeId, phoneHomeData)
      .then((enhancedData) =>
        callArcadePhoneHome({
          phoneHomeId,
          phoneHomeData: enhancedData,
          req,
        })
      )
      .catch((err) => {
        logger.error(`Failure encountered while calling Arcade with Phone Home data: ${err.toString()}`, { err });
        console.log(err);
      });
  });

export default router;
