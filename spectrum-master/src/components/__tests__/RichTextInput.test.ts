import { serializeNode, deserializeNode } from '../rich-text-input';

test.each([
  [
    [
      {
        type: 'p',
        children: [
          {
            text: 'Hello {{test}}',
          },
        ],
      },
      {
        type: 'p',
        children: [
          {
            text: '{hello}',
          },
        ],
      },
    ],
    `<p>Hello {{test}}</p><p>{hello}</p>`,
  ],
  [
    [
      {
        type: 'p',
        children: [
          {
            text: 'default value',
          },
        ],
      },
    ],
    `<p>default value</p>`,
  ],
])('Serializer works', (json, html) => {
  expect(serializeNode(json)).toEqual(html);
  expect(serializeNode(deserializeNode(html))).toEqual(html);
});
