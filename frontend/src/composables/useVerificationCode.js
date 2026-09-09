import { getCurrentInstance, onBeforeUnmount, ref } from 'vue';

/**
 * 管理验证码请求的防重复提交和倒计时；邮件请求、表单数据和提示由页面提供。
 */
export function useVerificationCode({ requestCode, buildRequest, cooldownSeconds = 60 }) {
  const sending = ref(false);
  const countdown = ref(0);
  let timer = null;

  /** 取消当前验证码倒计时并清空计时器引用。 */
  function clearTimer() {
    if (timer) {
      globalThis.clearInterval(timer);
      timer = null;
    }
  }

  /** 清除旧倒计时，设置冷却秒数并启动每秒递减。 */
  function startCountdown() {
    clearTimer();
    countdown.value = cooldownSeconds;
    /** 每秒递减剩余时间，到零时取消计时器并将显示值归零。 */
    timer = globalThis.setInterval(() => {
      countdown.value -= 1;
      if (countdown.value <= 0) {
        clearTimer();
        countdown.value = 0;
      }
    }, 1000);
  }

  /** 发送验证码请求；发送中或冷却期内不重复发送，仅在后端明确成功后开始倒计时。 */
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
    /** 组件卸载前取消验证码倒计时。 */
    onBeforeUnmount(clearTimer);
  }

  return { sending, countdown, request };
}
