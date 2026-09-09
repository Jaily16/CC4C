import { computed } from 'vue';
import store from '../store/index.js';

/** 暴露当前业务会话的只读用户、角色和认证派生状态。 */
export function useCurrentUser(source = store.state.user) {
  /** 包装当前用户状态引用。 */
  const user = computed(() => source);
  /** 读取当前角色，缺失时返回空字符串。 */
  const role = computed(() => source?.role || '');
  /** 仅当会话状态明确为 true 时视为已认证。 */
  const isAuthenticated = computed(() => source?.authenticated === true);
  /** 判断当前角色是否为普通用户。 */
  const isUser = computed(() => role.value === 'USER');
  /** 判断当前角色是否为管理员。 */
  const isAdmin = computed(() => role.value === 'ADMIN');

  return {
    user,
    isAuthenticated,
    isUser,
    isAdmin,
    role,
  };
}
