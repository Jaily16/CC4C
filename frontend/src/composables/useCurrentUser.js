import { computed } from 'vue';
import store from '../store/index.js';

/** 暴露当前业务会话的只读用户、角色和认证派生状态。 */
export function useCurrentUser(source = store.state.user) {
  /** 从现有响应式状态派生 user，不发起请求或写入外部数据。 */
  const user = computed(() => source);
  /** 从现有响应式状态派生 role，不发起请求或写入外部数据。 */
  const role = computed(() => source?.role || '');
  /** 从现有响应式状态派生 isAuthenticated，不发起请求或写入外部数据。 */
  const isAuthenticated = computed(() => source?.authenticated === true);
  /** 从现有响应式状态派生 isUser，不发起请求或写入外部数据。 */
  const isUser = computed(() => role.value === 'USER');
  /** 从现有响应式状态派生 isAdmin，不发起请求或写入外部数据。 */
  const isAdmin = computed(() => role.value === 'ADMIN');

  return {
    user,
    isAuthenticated,
    isUser,
    isAdmin,
    role,
  };
}
