import { getCurrentInstance, onBeforeUnmount, ref } from 'vue';

/**
 * 管理验证码请求的防重复提交和倒计时；邮件请求、表单数据和提示由页面提供。
 */
export function useVerificationCode({ requestCode, buildRequest, cooldownSeconds = 60 }) {
  const sending = ref(false);
  const countdown = ref(0);
  let timer = null;

  /** 处理 clearTimer 清理操作，仅影响当前功能明确指向的状态或资源。 */
  function clearTimer() {
    if (timer) {
      globalThis.clearInterval(timer);
      timer = null;
    }
  }

  /** startCountdown 封装当前组件的一项语义操作，并保持既有状态与错误处理边界。 */
  function startCountdown() {
    clearTimer();
    countdown.value = cooldownSeconds;
    /** 按既定时间安排下一次动作，并由所属组件或 composable 负责取消。 */
    timer = globalThis.setInterval(() => {
      countdown.value -= 1;
      if (countdown.value <= 0) {
        clearTimer();
        countdown.value = 0;
      }
    }, 1000);
  }

  /** 读取 request 所需数据并更新加载、成功或失败状态，不改变业务数据。 */
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
    /** 组件卸载前取消计时器、监听或未完成请求，避免资源泄漏和过期写回。 */
    onBeforeUnmount(clearTimer);
  }

  return { sending, countdown, request };
}
