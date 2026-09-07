import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

export function usePolling(load, intervalMilliseconds = 30000) {
  const manuallyPaused = ref(false);
  const documentHidden = ref(document.hidden);
  const refreshing = ref(false);
  const lastUpdatedAt = ref(null);
  let timer = null;
  let controller = null;
  let activePromise = null;
  let disposed = false;

  const paused = computed(() => manuallyPaused.value || documentHidden.value);

  function clearTimer() {
    if (timer !== null) window.clearTimeout(timer);
    timer = null;
  }

  function schedule() {
    clearTimer();
    if (!disposed && !paused.value) timer = window.setTimeout(() => void refresh(), intervalMilliseconds);
  }

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

  function togglePause() {
    manuallyPaused.value = !manuallyPaused.value;
    if (manuallyPaused.value) clearTimer();
    else void refresh();
  }

  function handleVisibility() {
    documentHidden.value = document.hidden;
    if (documentHidden.value) {
      clearTimer();
      controller?.abort();
    } else if (!manuallyPaused.value) {
      void refresh(true);
    }
  }

  onMounted(() => {
    disposed = false;
    document.addEventListener('visibilitychange', handleVisibility);
    void refresh();
  });
  onBeforeUnmount(() => {
    disposed = true;
    clearTimer();
    controller?.abort();
    document.removeEventListener('visibilitychange', handleVisibility);
  });

  return { paused, manuallyPaused, refreshing, lastUpdatedAt, refresh, togglePause };
}
