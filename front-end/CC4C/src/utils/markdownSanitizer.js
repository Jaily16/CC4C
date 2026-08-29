import sanitizeHtml from 'sanitize-html';

const HEADING_ID_PATTERN = /^cc4c-md-\d+-[a-z0-9-]{1,64}$/;

const allowedTags = [
  ...sanitizeHtml.defaults.allowedTags,
  'img',
  'input',
  'del',
  'details',
  'summary',
];

const headingTransform = (tagName, attribs) => {
  const nextAttributes = { ...attribs };
  if (!HEADING_ID_PATTERN.test(nextAttributes.id || '')) {
    delete nextAttributes.id;
  }
  return { tagName, attribs: nextAttributes };
};

const markdownSanitizeOptions = {
  allowedTags,
  allowedAttributes: {
    '*': ['class'],
    a: ['href', 'title', 'target', 'rel'],
    img: ['src', 'alt', 'title', 'width', 'height', 'loading'],
    input: ['type', 'checked', 'disabled'],
    h1: ['id', 'class'],
    h2: ['id', 'class'],
    h3: ['id', 'class'],
    h4: ['id', 'class'],
    h5: ['id', 'class'],
    h6: ['id', 'class'],
    td: ['colspan', 'rowspan', 'class'],
    th: ['colspan', 'rowspan', 'scope', 'class'],
    ol: ['start', 'class'],
    time: ['datetime', 'class'],
  },
  allowedSchemes: ['http', 'https', 'mailto'],
  allowProtocolRelative: false,
  disallowedTagsMode: 'discard',
  nestingLimit: 64,
  transformTags: {
    a: (tagName, attribs) => {
      const nextAttributes = {
        ...attribs,
        rel: 'nofollow noopener noreferrer',
      };
      if (nextAttributes.target !== '_blank') {
        delete nextAttributes.target;
      }
      return { tagName, attribs: nextAttributes };
    },
    input: (tagName, attribs) => ({
      tagName,
      attribs: {
        ...(attribs.checked === '' || attribs.checked === 'checked' ? { checked: '' } : {}),
        type: 'checkbox',
        disabled: '',
      },
    }),
    h1: headingTransform,
    h2: headingTransform,
    h3: headingTransform,
    h4: headingTransform,
    h5: headingTransform,
    h6: headingTransform,
  },
};

export const markdownHeadingId = (text, _level, index) => {
  const slug = String(text ?? '')
    .replace(/<[^>]*>/g, ' ')
    .normalize('NFKC')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 64) || 'section';

  return `cc4c-md-${Number.isInteger(index) && index > 0 ? index : 1}-${slug}`;
};

export const sanitizeMarkdownHtml = (html) => sanitizeHtml(String(html ?? ''), markdownSanitizeOptions);
