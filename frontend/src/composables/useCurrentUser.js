import { computed } from 'vue';
import store from '../store/index.js';

export function useCurrentUser(source = store.state.user) {
  const user = computed(() => source);
  const role = computed(() => source?.role || '');
  const isAuthenticated = computed(() => source?.authenticated === true);
  const isUser = computed(() => role.value === 'USER');
  const isAdmin = computed(() => role.value === 'ADMIN');

  return {
    user,
    isAuthenticated,
    isUser,
    isAdmin,
    role,
  };
}
