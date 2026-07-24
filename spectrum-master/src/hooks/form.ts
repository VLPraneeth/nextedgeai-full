import { uniqueId } from 'lodash';

import { ensureElementById } from 'utils/DownloadUtil';

export const useForm = () => {
  // Use to redirect the whole page from a form post
  const formPostToPage = (
    url: string,
    formBody: Record<string, string> = {},
    encType: string = 'x-www-form-urlencoded'
  ) => {
    const formId = `formPostPage${uniqueId()}`;
    const form = ensureElementById('form', formId, (form) => {
      form.id = formId;
      form.method = 'POST';
      form.enctype = encType;
    });

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
  };

  return {
    formPostToPage,
  };
};
