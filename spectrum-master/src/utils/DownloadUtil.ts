//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { uniqueId } from 'lodash';

import { get } from 'utils/AjaxUtil';
import { xsrfToken, XSRF_TOKEN_KEY } from 'utils/AjaxUtil';

const HIDDEN_DOWNLOAD_IFRAME_ID = 'hiddenDownloadiFrame';
const HIDDEN_DOWNLOAD_FORM_ID = 'hiddenDownloadForm';

export function ensureElementById<
  TagName extends keyof JSX.IntrinsicElements & keyof HTMLElementTagNameMap,
  ElementType extends unknown = HTMLElementTagNameMap[TagName]
>(element: TagName, id: string, onCreate?: (el: ElementType) => void) {
  const el = document.getElementById(id);
  if (el) {
    return el as ElementType;
  }

  const newEl = document.createElement(element);
  onCreate?.(newEl as ElementType);
  return newEl;
}

export function downloadFile(url: string, formBody: Record<string, string> = {}) {
  const iframeId = `${HIDDEN_DOWNLOAD_IFRAME_ID}${uniqueId()}`;
  const formId = `${HIDDEN_DOWNLOAD_FORM_ID}${uniqueId()}`;

  ensureElementById('iframe', iframeId, (iFrame) => {
    iFrame.style.display = 'none';
    iFrame.id = iframeId;
    iFrame.name = iframeId;
    document.body.appendChild(iFrame);
  });

  const form = ensureElementById('form', formId, (form) => {
    form.target = iframeId;
    form.id = formId;
    form.method = 'POST';
  });

  // Add our xsrf token
  formBody[XSRF_TOKEN_KEY] = xsrfToken || '';
  Object.keys(formBody)
    .filter((key) => Boolean(formBody[key]))
    .forEach((key) => {
      const hiddenInput = document.createElement('input');
      hiddenInput.setAttribute('type', 'hidden');
      hiddenInput.value = formBody[key];
      hiddenInput.name = key;
      form.appendChild(hiddenInput);
    });

  document.body.appendChild(form);

  form.action = url;
  form.submit();
}

/**
 * Download the csv data returned from the url
 */
export const downloadCsvDataAsFile = async (fileName: string, url: string) => {
  const data = await get(url);

  const downloadLink = document.createElement('a');
  downloadLink.href = `data:text/csv;charset=utf-8,${encodeURIComponent(data.data)}`;
  downloadLink.target = '_blank';
  downloadLink.download = fileName;
  downloadLink.click();
};

/**
 * Download a file using GET HTTP method. This is a simple way to download
 * smaller files where we need to show a loading state before the file begins to
 * download. In most cases use downloadFile instead.
 */
export const downloadGetFile = (url: string, fileName: string) => {
  return fetch(url)
    .then((resp) => resp.blob())
    .then((blob) => {
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.style.display = 'none';
      a.href = url;
      a.download = fileName;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
    });
};
