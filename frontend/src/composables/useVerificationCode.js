import { getCurrentInstance, onBeforeUnmount, ref } from 'vue';

/**
 * 管理验证码请求的防重复提交和倒计时；邮件请求、表单数据和提示由页面提供。
 */
export function useVerificationCode({ requestCode, buildRequest, cooldownSeconds = 60 }) {
  const sending = ref(false);
  const countdown = ref(0);
  let timer = null;

  function clearTimer() {
    if (timer) {
      globalThis.clearInterval(timer);
      timer = null;
    }
  }

  function startCountdown() {
    clearTimer();
    countdown.value = cooldownSeconds;
    timer = globalThis.setInterval(() => {
      countdown.value -= 1;
      if (countdown.value <= 0) {
        clearTimer();
        countdown.value = 0;
      }
    }, 1000);
  }

  async function request() {
    if (sending.value || countdown.value > 0) return null;

    sending.value = true;
    try {
      const response = await requestCode(buildRequest());
      if (response?.data?.data === true) startCountdown();
      return response;
    } finally {
      sending.value = false;
    }
  }

  if (getCurrentInstance()) {
    onBeforeUnmount(clearTimer);
  }

  return { sending, countdown, request };
}
