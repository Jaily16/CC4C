import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

/** 封装无重叠轮询、手动刷新、隐藏暂停和卸载清理，并中止过期请求。 */
export function usePolling(load, intervalMilliseconds = 30000) {
  const manuallyPaused = ref(false);
  const documentHidden = ref(document.hidden);
  const refreshing = ref(false);
  const lastUpdatedAt = ref(null);
  let timer = null;
  let controller = null;
  let activePromise = null;
  let disposed = false;

  /** 合并手动暂停与页面隐藏状态，决定是否安排下一次轮询。 */
  const paused = computed(() => manuallyPaused.value || documentHidden.value);

  /** 取消尚未触发的轮询计时器并清空句柄，不中止正在执行的请求。 */
  function clearTimer() {
    if (timer !== null) window.clearTimeout(timer);
    timer = null;
  }

  /** 先清除旧计时器；仅在未卸载且未暂停时安排下一轮刷新，避免叠加轮询。 */
  function schedule() {
    clearTimer();
    /** 在轮询间隔结束后请求刷新，计时器由 clearTimer 统一取消。 */
    if (!disposed && !paused.value) timer = window.setTimeout(() => void refresh(), intervalMilliseconds);
  }

  /** 复用正在执行的刷新；替换刷新先中止并等待旧请求。加载返回且未取消时记录完成时间，最后安排下一轮。 */
  async function refresh(replace = false) {
    if (activePromise) {
      if (!replace) return activePromise;
      controller?.abort();
      await activePromise;
    }
    clearTimer();
    const currentController = new AbortController();
    controller = currentController;
    refreshing.value = true;
    const operation = (async () => {
      await load(currentController.signal);
      if (!currentController.signal.aborted) lastUpdatedAt.value = new Date();
    })();
    activePromise = operation;
    try {
      await operation;
    } finally {
      if (activePromise === operation) {
        refreshing.value = false;
        controller = null;
        activePromise = null;
        schedule();
      }
    }
  }

  /** 切换手动暂停；暂停只取消下一轮计时，恢复时立即请求刷新。 */
  function togglePause() {
    manuallyPaused.value = !manuallyPaused.value;
    if (manuallyPaused.value) clearTimer();
    else void refresh();
  }

  /** 页面隐藏时取消计时并中止请求；重新可见且未手动暂停时执行替换刷新。 */
  function handleVisibility() {
    documentHidden.value = document.hidden;
    if (documentHidden.value) {
      clearTimer();
      controller?.abort();
    } else if (!manuallyPaused.value) {
      void refresh(true);
    }
  }

  /** 挂载时登记可见性监听并发起首次刷新。 */
  onMounted(() => {
    disposed = false;
    /** 登记页面可见性监听，以便隐藏时暂停轮询并在恢复后安全刷新。 */
    document.addEventListener('visibilitychange', handleVisibility);
    void refresh();
  });
  /** 标记已卸载，清除计时器、中止当前请求并移除可见性监听。 */
  onBeforeUnmount(() => {
    disposed = true;
    clearTimer();
    controller?.abort();
    /** 移除页面可见性监听，防止组件卸载后继续接收浏览器事件。 */
    document.removeEventListener('visibilitychange', handleVisibility);
  });

  return { paused, manuallyPaused, refreshing, lastUpdatedAt, refresh, togglePause };
}
