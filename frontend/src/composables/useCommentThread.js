import { nextTick, onScopeDispose, ref, unref } from 'vue';

import { apiErrorMessage } from '../utils/apiError.js';
import { reportClientError } from '../utils/reportClientError.js';

/** 读取函数、ref 或普通值形式的当前输入。 */
function resolve(value) {
  return typeof value === 'function' ? value() : unref(value);
}

/** 将 true 或非空对象响应视为评论写入成功。 */
function isSuccessfulMutation(response) {
  const result = response?.data?.data;
  return result === true || (result !== null && typeof result === 'object');
}

/** 保留雪花 ID 的十进制字符串精度；缺失值和已失真的数值一律视为无效身份。 */
function commentIdentity(value) {
  if (typeof value === 'string') return /^[1-9]\d*$/.test(value) ? value : '';
  return Number.isSafeInteger(value) && value > 0 ? String(value) : '';
}

/**
 * 为课程和博客页面提供一致的两级评论状态机；请求构造仍由页面闭包负责。
 */
export function useCommentThread({
  subjectId,
  fetchPage,
  createComment,
  createReply,
  currentUserId,
  deleteComment,
  confirmDelete,
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
  const deletingCommentId = ref(null);
  const deleteError = ref('');
  let scopeVersion = 0;

  // 离开详情组件时使未确认的删除失效，避免全局确认弹窗在旧页面销毁后仍发送请求。
  onScopeDispose(() => {
    scopeVersion += 1;
  });

  /** 仅控制本人删除入口的展示；后端仍必须独立验证登录身份与评论归属。 */
  function canDeleteComment(commentItem) {
    const userId = commentIdentity(resolve(currentUserId));
    return Boolean(
      userId &&
      userId === commentIdentity(commentItem?.userId) &&
      commentIdentity(commentItem?.commentId) &&
      typeof deleteComment === 'function' &&
      typeof confirmDelete === 'function',
    );
  }

  /** 在当前已加载的评论树中定位目标，避免确认弹窗结束后操作已经切走的评论。 */
  function findComment(commentId, items = commentList.value) {
    for (const item of items) {
      if (commentIdentity(item.commentId) === commentId) return item;
      const replyItem = findComment(commentId, item.subCommentList || []);
      if (replyItem) return replyItem;
    }
    return null;
  }

  /**
   * 串行完成本人评论的确认、删除和列表刷新；不做乐观移除，也不自动重试写请求。
   * 页面切换或身份变化会使待确认操作失效；删除成功后的刷新失败会单独提示，避免误删第二次。
   */
  async function removeComment(commentItem) {
    if (deletingCommentId.value !== null || !canDeleteComment(commentItem)) return false;
    const commentId = commentIdentity(commentItem.commentId);
    const userId = commentIdentity(resolve(currentUserId));
    const version = scopeVersion;
    deletingCommentId.value = commentId;
    deleteError.value = '';
    try {
      await confirmDelete(commentItem);
      if (
        version !== scopeVersion ||
        userId !== commentIdentity(resolve(currentUserId)) ||
        !canDeleteComment(findComment(commentId))
      ) {
        return false;
      }
      const response = await deleteComment(commentId);
      if (version !== scopeVersion) return false;
      if (response?.data?.data !== true) {
        deleteError.value = response?.data?.msg || '评论删除失败，请稍后重试。';
        return false;
      }
      if (commentIdentity(replyingTo.value) === commentId) {
        replyingTo.value = null;
        replyText.value = '';
        replyInputError.value = '';
      }
      // 当前页只剩被删的顶层评论时回到上一页；删除回复不改变顶层分页。
      if (
        commentPage.value > 1 &&
        commentList.value.length === 1 &&
        commentIdentity(commentList.value[0].commentId) === commentId
      ) {
        commentPage.value -= 1;
      }
      const refreshed = await loadComments();
      if (version !== scopeVersion) return false;
      if (!refreshed) {
        deleteError.value = '评论已删除，但列表刷新失败，请点击重新加载。';
        return false;
      }
      return true;
    } catch (error) {
      if (version !== scopeVersion || error === 'cancel' || error === 'close') return false;
      deleteError.value = apiErrorMessage(error, '评论删除失败，请稍后重试。');
      reportClientError(error, 'useCommentThread.removeComment');
      return false;
    } finally {
      if (version === scopeVersion) deletingCommentId.value = null;
    }
  }

  /** 按当前主题和页码读取评论，失败时清空列表并保存提示；缺少主题时返回空结果。 */
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
      deleteError.value = '';
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

  /** 校验非空评论并提交，成功后清空输入、回到第一页并重新加载评论。 */
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
      if (!isSuccessfulMutation(response)) {
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

  /** 切换当前回复目标，并清空回复正文和校验提示。 */
  function toggleReply(commentId) {
    replyingTo.value = replyingTo.value === commentId ? null : commentId;
    replyText.value = '';
    replyInputError.value = '';
  }

  /** 提交对指定父评论的回复；成功后刷新评论树，等待渲染并聚焦该回复区域。 */
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
      if (!isSuccessfulMutation(response)) {
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

  /** 切换评论页码，收起回复输入并重新读取该页。 */
  function changeCommentPage(page) {
    commentPage.value = page;
    replyingTo.value = null;
    return loadComments();
  }

  /** 使待确认删除失效，并清空评论、回复、分页及错误状态。 */
  function resetComments() {
    scopeVersion += 1;
    deletingCommentId.value = null;
    deleteError.value = '';
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
    deletingCommentId,
    deleteError,
    canDeleteComment,
    removeComment,
    loadComments,
    comment,
    toggleReply,
    reply,
    changeCommentPage,
    resetComments,
  };
}
