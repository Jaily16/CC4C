import { nextTick, ref, unref } from 'vue';

import { apiErrorMessage } from '../utils/apiError.js';
import { reportClientError } from '../utils/reportClientError.js';

function resolve(value) {
  return typeof value === 'function' ? value() : unref(value);
}

/**
 * 为课程和博客页面提供一致的两级评论状态机；请求构造仍由页面闭包负责。
 */
export function useCommentThread({
  subjectId,
  fetchPage,
  createComment,
  createReply,
  focusIdPrefix = 'replies-',
  loadErrorMessage = '评论加载失败，请检查网络后重试。',
  commentErrorMessage = '评论发布失败，请稍后重试。',
  replyErrorMessage = '回复发布失败，请稍后重试。',
}) {
  const commentList = ref([]);
  const commentsLoading = ref(false);
  const commentsError = ref('');
  const commentText = ref('');
  const commentInputError = ref('');
  const commentSubmitting = ref(false);
  const replyingTo = ref(null);
  const replyText = ref('');
  const replyInputError = ref('');
  const replySubmitting = ref(false);
  const commentPage = ref(1);
  const commentPageSize = ref(10);
  const commentTotal = ref(0);

  async function loadComments() {
    const currentSubjectId = resolve(subjectId);
    if (!currentSubjectId) {
      commentList.value = [];
      commentTotal.value = 0;
      return null;
    }

    commentsLoading.value = true;
    commentsError.value = '';
    try {
      const response = await fetchPage(currentSubjectId, {
        page: commentPage.value,
        size: commentPageSize.value,
      });
      commentList.value = response?.data?.data?.items || [];
      commentTotal.value = response?.data?.data?.total || 0;
      return response;
    } catch (error) {
      commentList.value = [];
      commentTotal.value = 0;
      commentsError.value = apiErrorMessage(error, loadErrorMessage);
      reportClientError(error, 'useCommentThread.loadComments');
      return null;
    } finally {
      commentsLoading.value = false;
    }
  }

  async function comment() {
    commentInputError.value = '';
    const content = commentText.value.trim();
    if (!content) {
      commentInputError.value = '评论内容不能为空。';
      return false;
    }

    commentSubmitting.value = true;
    try {
      const response = await createComment(content);
      if (response?.data?.data !== true) {
        commentInputError.value = response?.data?.msg || commentErrorMessage;
        return false;
      }
      commentText.value = '';
      commentPage.value = 1;
      await loadComments();
      return true;
    } catch (error) {
      commentInputError.value = apiErrorMessage(error, commentErrorMessage);
      reportClientError(error, 'useCommentThread.comment');
      return false;
    } finally {
      commentSubmitting.value = false;
    }
  }

  function toggleReply(commentId) {
    replyingTo.value = replyingTo.value === commentId ? null : commentId;
    replyText.value = '';
    replyInputError.value = '';
  }

  async function reply(fatherId) {
    replyInputError.value = '';
    const content = replyText.value.trim();
    if (!content) {
      replyInputError.value = '回复内容不能为空。';
      return false;
    }

    replySubmitting.value = true;
    try {
      const response = await createReply(fatherId, content);
      if (response?.data?.data !== true) {
        replyInputError.value = response?.data?.msg || replyErrorMessage;
        return false;
      }
      replyText.value = '';
      replyingTo.value = null;
      await loadComments();
      await nextTick();
      if (typeof document !== 'undefined') {
        document.getElementById(`${focusIdPrefix}${fatherId}`)?.focus();
      }
      return true;
    } catch (error) {
      replyInputError.value = apiErrorMessage(error, replyErrorMessage);
      reportClientError(error, 'useCommentThread.reply');
      return false;
    } finally {
      replySubmitting.value = false;
    }
  }

  function changeCommentPage(page) {
    commentPage.value = page;
    replyingTo.value = null;
    return loadComments();
  }

  function resetComments() {
    commentList.value = [];
    commentsLoading.value = false;
    commentsError.value = '';
    commentText.value = '';
    commentInputError.value = '';
    commentSubmitting.value = false;
    replyingTo.value = null;
    replyText.value = '';
    replyInputError.value = '';
    replySubmitting.value = false;
    commentPage.value = 1;
    commentTotal.value = 0;
  }

  return {
    commentList,
    commentsLoading,
    commentsError,
    commentText,
    commentInputError,
    commentSubmitting,
    replyingTo,
    replyText,
    replyInputError,
    replySubmitting,
    commentPage,
    commentPageSize,
    commentTotal,
    loadComments,
    comment,
    toggleReply,
    reply,
    changeCommentPage,
    resetComments,
  };
}
