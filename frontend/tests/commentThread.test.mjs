import assert from 'node:assert/strict';
import test from 'node:test';

import { useCommentThread } from '../src/composables/useCommentThread.js';

function page(items = [], total = items.length) {
  return { data: { data: { items, total } } };
}

test('comment thread does not request until explicitly loaded', async () => {
  let fetchCalls = 0;
  const thread = useCommentThread({
    subjectId: 7,
    fetchPage: async () => {
      fetchCalls += 1;
      return page();
    },
    createComment: async () => ({ data: { data: true } }),
    createReply: async () => ({ data: { data: true } }),
  });
  assert.equal(fetchCalls, 0);
  await thread.loadComments();
  assert.equal(fetchCalls, 1);
  assert.equal(thread.commentPage.value, 1);
  assert.equal(thread.commentPageSize.value, 10);
});

test('comment and reply preserve caller-owned request construction', async () => {
  const calls = [];
  const thread = useCommentThread({
    subjectId: 42,
    fetchPage: async (subjectId, query) => {
      calls.push(['fetch', subjectId, query]);
      return page([], 0);
    },
    createComment: async (content) => {
      calls.push(['comment', content]);
      return { data: { data: true } };
    },
    createReply: async (fatherId, content) => {
      calls.push(['reply', fatherId, content]);
      return { data: { data: true } };
    },
  });
  thread.commentText.value = '  hello  ';
  assert.equal(await thread.comment(), true);
  thread.replyText.value = '  world  ';
  assert.equal(await thread.reply(9), true);
  assert.deepEqual(calls, [
    ['comment', 'hello'],
    ['fetch', 42, { page: 1, size: 10 }],
    ['reply', 9, 'world'],
    ['fetch', 42, { page: 1, size: 10 }],
  ]);
});

test('empty comment and reply are rejected locally', async () => {
  const thread = useCommentThread({
    subjectId: 1,
    fetchPage: async () => page(),
    createComment: async () => ({ data: { data: true } }),
    createReply: async () => ({ data: { data: true } }),
  });
  assert.equal(await thread.comment(), false);
  assert.equal(thread.commentInputError.value, '评论内容不能为空。');
  assert.equal(await thread.reply(1), false);
  assert.equal(thread.replyInputError.value, '回复内容不能为空。');
});
