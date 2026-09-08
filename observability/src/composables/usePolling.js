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

  /** 从现有响应式状态派生 paused，不发起请求或写入外部数据。 */
  const paused = computed(() => manuallyPaused.value || documentHidden.value);

  /** 处理 clearTimer 清理操作，仅影响当前功能明确指向的状态或资源。 */
  function clearTimer() {
    if (timer !== null) window.clearTimeout(timer);
    timer = null;
  }

  /** schedule 封装当前组件的一项语义操作，并保持既有状态与错误处理边界。 */
  function schedule() {
    clearTimer();
    /** 按既定时间安排下一次动作，并由所属组件或 composable 负责取消。 */
    if (!disposed && !paused.value) timer = window.setTimeout(() => void refresh(), intervalMilliseconds);
  }

  /** 读取 refresh 所需数据并更新加载、成功或失败状态，不改变业务数据。 */
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

  /** 响应 togglePause 导航或界面事件，更新当前组件的受控展示状态。 */
  function togglePause() {
    manuallyPaused.value = !manuallyPaused.value;
    if (manuallyPaused.value) clearTimer();
    else void refresh();
  }

  /** handleVisibility 封装当前组件的一项语义操作，并保持既有状态与错误处理边界。 */
  function handleVisibility() {
    documentHidden.value = document.hidden;
    if (documentHidden.value) {
      clearTimer();
      controller?.abort();
    } else if (!manuallyPaused.value) {
      void refresh(true);
    }
  }

  /** 组件挂载后执行首次只读加载并登记当前页面需要的运行资源。 */
  onMounted(() => {
    disposed = false;
    /** 登记页面可见性监听，以便隐藏时暂停轮询并在恢复后安全刷新。 */
    document.addEventListener('visibilitychange', handleVisibility);
    void refresh();
  });
  /** 组件卸载前取消计时器、监听或未完成请求，避免资源泄漏和过期写回。 */
  onBeforeUnmount(() => {
    disposed = true;
    clearTimer();
    controller?.abort();
    /** 移除页面可见性监听，防止组件卸载后继续接收浏览器事件。 */
    document.removeEventListener('visibilitychange', handleVisibility);
  });

  return { paused, manuallyPaused, refreshing, lastUpdatedAt, refresh, togglePause };
}
