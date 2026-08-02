<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue';

const API_PREFIX = import.meta.env.DEV ? '/api' : '';
const selectedTaskKey = 'taxroom.executionTaskId';
const state = reactive({
  taskId: localStorage.getItem(selectedTaskKey) || '',
  task: null,
  result: null,
  tasks: [],
  options: [],
  loading: false,
  listLoading: false,
  notice: '',
  noticeType: '',
  backendOnline: false,
  showCreate: false,
  showTaskEntry: false,
  showRerunConfirm: false,
  createMode: '',
  selectedIncomeType: '',
  activeItemIndex: 0,
  uploadingMaterial: '',
  chatLoading: false
});
const review = reactive({ needHumanReview: false, reviewer: '', comment: '', records: '' });
const chatDraft = ref('');
const chatStreamRef = ref(null);
const chatMessages = ref([]);
let pollingTimer;

const activeItem = computed(() => state.result?.items?.[state.activeItemIndex] || {});
const parsedResult = computed(() => {
  if (!activeItem.value.change_result) return {};
  try {
    return typeof activeItem.value.change_result === 'string'
      ? JSON.parse(activeItem.value.change_result)
      : activeItem.value.change_result;
  } catch {
    return {};
  }
});
const records = computed(() => {
  const result = parsedResult.value;
  return result.records?.length ? result.records : (result.globalParam?.dividendExtractRecords || []);
});
const routeSummary = computed(() => activeItem.value.route_summary || {});
const statusClass = computed(() => ({
  COLLECTING: 'pending', PROCESSING: 'running', COMPLETED: 'success', FAILED: 'fail'
}[state.task?.status] || 'pending'));
const canSubmit = computed(() => state.task?.status === 'COLLECTING'
  && state.task.fileCount > 0
  && !state.loading
  && !state.uploadingMaterial);
const canRerun = computed(() => ['FAILED', 'COMPLETED'].includes(state.task?.status) && !state.loading);
const resultLabel = computed(() => records.value.length
  ? `${records.value.length} 条记录`
  : (state.task?.status === 'COMPLETED' ? '无结构化记录' : '等待处理结果'));
const materialProgress = computed(() => {
  const expected = Number(state.task?.expectedMaterialCount || 0);
  return expected ? Math.round((Number(state.task?.uploadedMaterialCount || 0) / expected) * 100) : 0;
});
const taskPhase = computed(() => ({
  COLLECTING: 2,
  PROCESSING: 3,
  COMPLETED: 4,
  FAILED: 3
}[state.task?.status] || 1));
const reviewWarnings = computed(() => {
  const warnings = [];
  const append = (value) => {
    if (Array.isArray(value)) warnings.push(...value.filter(Boolean).map(String));
    else if (value) warnings.push(String(value));
  };
  append(parsedResult.value.warnings);
  append(parsedResult.value.globalParam?.reviewReasons);
  append(activeItem.value.review_reasons);
  records.value.forEach((record) => append(record.qualityWarnings || record.warnings));
  return [...new Set(warnings)];
});
const evidenceRows = computed(() => records.value.flatMap((record, recordIndex) => {
  const rowIds = Array.isArray(record.evidenceRowIds) ? record.evidenceRowIds : [];
  return rowIds.map((rowId) => ({
    rowId,
    recordIndex: recordIndex + 1,
    payer: record.payer || `记录 ${recordIndex + 1}`,
    detail: formatEvidenceDetail(record.evidence?.[rowId])
  }));
}));
const routeConfidence = computed(() => routeSummary.value.confidence ?? activeItem.value.route_confidence);

async function request(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (options.body && !(options.body instanceof FormData) && !headers['content-type']) {
    headers['content-type'] = 'application/json';
  }
  const response = await fetch(`${API_PREFIX}${path}`, { ...options, headers });
  const payload = response.headers.get('content-type')?.includes('json') ? await response.json() : await response.text();
  if (!response.ok || (payload && payload.code && payload.code !== 200)) {
    throw new Error(payload?.message || payload || `请求失败 (${response.status})`);
  }
  return payload?.data ?? payload;
}

function notify(message, type = '') {
  state.notice = message;
  state.noticeType = type;
  window.setTimeout(() => {
    if (state.notice === message) state.notice = '';
  }, 5000);
}

function initialChatMessages() {
  return [{
    role: 'assistant',
    content: '你可以先说说这笔收入是怎么产生的、由谁支付。我会通过简单追问帮你判断可能的境外所得类型。',
    createdAt: new Date().toISOString()
  }];
}

function openTaskEntry() {
  state.showTaskEntry = true;
}

function closeTaskEntry() {
  state.showTaskEntry = false;
}

function openRerunConfirm() {
  if (!canRerun.value) return;
  state.showRerunConfirm = true;
}

function closeRerunConfirm() {
  if (!state.loading) state.showRerunConfirm = false;
}

function chooseCreateMode(mode) {
  state.createMode = mode;
  state.showCreate = true;
  state.showTaskEntry = false;
  if (mode === 'chat') {
    chatMessages.value = initialChatMessages();
    chatDraft.value = '';
    nextTick(scrollChatToBottom);
  }
}

function scrollChatToBottom() {
  if (chatStreamRef.value) chatStreamRef.value.scrollTop = chatStreamRef.value.scrollHeight;
}

async function sendChat() {
  const message = chatDraft.value.trim();
  if (!message || state.chatLoading) return;
  const history = chatMessages.value
    .filter((item) => !item.error)
    .slice(-20)
    .map(({ role, content }) => ({ role, content }));
  chatMessages.value.push({ role: 'user', content: message, createdAt: new Date().toISOString() });
  chatDraft.value = '';
  state.chatLoading = true;
  await nextTick();
  scrollChatToBottom();
  try {
    const response = await request('/ai/chat/query', {
      method: 'POST',
      body: JSON.stringify({ message, history })
    });
    chatMessages.value.push({
      role: 'assistant',
      content: String(response || '我暂时没有获取到有效回复，请换一种方式描述这笔收入。'),
      createdAt: new Date().toISOString()
    });
  } catch (error) {
    chatMessages.value.push({
      role: 'assistant',
      content: '对话服务暂时不可用，请稍后重试。',
      createdAt: new Date().toISOString(),
      error: true
    });
    notify(error.message, 'error');
  } finally {
    state.chatLoading = false;
    await nextTick();
    scrollChatToBottom();
  }
}

function trimId(value) { return String(value || '').slice(0, 10); }
function formatDate(value) {
  if (!value) return '刚刚';
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value));
}
function formatSize(value) {
  const size = Number(value || 0);
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}
function formatConfidence(value) {
  if (value == null || value === '') return '待评估';
  const number = Number(value);
  return Number.isFinite(number) ? `${Math.round(number * 100)}%` : String(value);
}
function formatEvidenceDetail(evidence) {
  if (!evidence || typeof evidence !== 'object') return '已关联原始证据行';
  const location = [
    evidence.sheetName,
    evidence.page != null ? `第 ${Number(evidence.page) + 1} 页` : '',
    evidence.rowIndex != null ? `第 ${evidence.rowIndex} 行` : ''
  ].filter(Boolean).join(' · ');
  const cells = evidence.cells && typeof evidence.cells === 'object'
    ? Object.values(evidence.cells).filter((value) => value != null && value !== '').slice(0, 4).join(' / ')
    : '';
  return [location, cells].filter(Boolean).join(' · ') || '已关联原始证据行';
}

async function loadOptions() {
  const data = await request('/execution-tasks/options');
  state.options = data.incomeTypes || [];
  if (!state.selectedIncomeType && state.options.length) state.selectedIncomeType = state.options[0].code;
}

async function loadTasks(quiet = false) {
  if (!quiet) state.listLoading = true;
  try {
    const data = await request('/execution-tasks?page=1&size=50');
    state.tasks = data.items || [];
  } finally {
    state.listLoading = false;
  }
}

function hydrateReview() {
  review.needHumanReview = Boolean(activeItem.value.need_human_review || parsedResult.value.globalParam?.needHumanReview);
  review.reviewer = parsedResult.value.globalParam?.reviewer || '';
  review.comment = parsedResult.value.globalParam?.reviewComment || '';
  review.records = records.value.length ? JSON.stringify(records.value, null, 2) : '';
}

async function loadResult(quiet = false) {
  if (!state.task?.parseTaskId) {
    state.result = null;
    return;
  }
  try {
    state.result = await request(`/execution-tasks/${encodeURIComponent(state.task.id)}/result`);
    if (state.activeItemIndex >= (state.result.items?.length || 0)) state.activeItemIndex = 0;
    hydrateReview();
  } catch (error) {
    if (!quiet) notify(error.message, 'error');
  }
}

async function loadTask(taskId, quiet = false) {
  if (!taskId) return;
  state.taskId = String(taskId);
  localStorage.setItem(selectedTaskKey, state.taskId);
  state.showCreate = false;
  state.showTaskEntry = false;
  state.showRerunConfirm = false;
  state.createMode = '';
  if (!quiet) state.loading = true;
  try {
    state.task = await request(`/execution-tasks/${encodeURIComponent(taskId)}`);
    await loadResult(true);
    if (state.task.status === 'PROCESSING') startPolling(); else stopPolling();
  } catch (error) {
    notify(error.message, 'error');
  } finally {
    state.loading = false;
  }
}

async function refreshCurrent(quiet = false) {
  if (!state.taskId) return;
  await Promise.all([loadTask(state.taskId, quiet), loadTasks(true)]);
}

function startPolling() {
  stopPolling();
  pollingTimer = window.setInterval(() => refreshCurrent(true), 5000);
}
function stopPolling() {
  if (pollingTimer) window.clearInterval(pollingTimer);
  pollingTimer = undefined;
}

async function createTask() {
  if (!state.selectedIncomeType) return;
  state.loading = true;
  try {
    const task = await request('/execution-tasks', {
      method: 'POST',
      body: JSON.stringify({ incomeType: state.selectedIncomeType })
    });
    await loadTasks(true);
    await loadTask(task.id);
    notify(`${task.incomeTypeLabel}任务已创建`, 'success');
  } catch (error) {
    notify(error.message, 'error');
  } finally {
    state.loading = false;
  }
}

async function uploadFiles(material, event) {
  const files = Array.from(event.target.files || []);
  event.target.value = '';
  if (!files.length || !state.task) return;
  state.uploadingMaterial = material.code;
  const body = new FormData();
  files.forEach((file) => body.append('files', file));
  try {
    state.task = await request(`/execution-tasks/${encodeURIComponent(state.task.id)}/materials/${encodeURIComponent(material.code)}/files`, {
      method: 'POST', body
    });
    await loadTasks(true);
    notify(`已上传 ${files.length} 个文件`, 'success');
  } catch (error) {
    notify(error.message, 'error');
  } finally {
    state.uploadingMaterial = '';
  }
}

async function deleteFile(file) {
  if (!state.task) return;
  state.loading = true;
  try {
    await request(`/execution-tasks/${encodeURIComponent(state.task.id)}/files/${encodeURIComponent(file.id)}`, { method: 'DELETE' });
    await refreshCurrent(true);
    notify('材料已移除', 'success');
  } catch (error) {
    notify(error.message, 'error');
  } finally {
    state.loading = false;
  }
}

async function submitTask() {
  if (!canSubmit.value) return;
  state.loading = true;
  try {
    state.task = await request(`/execution-tasks/${encodeURIComponent(state.task.id)}/submit`, { method: 'POST' });
    await loadTasks(true);
    await loadResult(true);
    startPolling();
    notify('材料已锁定，任务开始处理', 'success');
  } catch (error) {
    notify(error.message, 'error');
  } finally {
    state.loading = false;
  }
}

async function rerunTask() {
  if (!canRerun.value) return;
  let rerunAccepted = false;
  state.loading = true;
  state.result = null;
  state.activeItemIndex = 0;
  review.needHumanReview = false;
  review.reviewer = '';
  review.comment = '';
  review.records = '';
  try {
    state.task = await request(`/execution-tasks/${encodeURIComponent(state.task.id)}/retry`, { method: 'POST' });
    rerunAccepted = true;
    state.showRerunConfirm = false;
    startPolling();
    await Promise.all([loadTasks(true), loadResult(true)]);
    notify('旧结果已清理，任务已重新提交', 'success');
  } catch (error) {
    if (rerunAccepted) {
      notify(`任务已重新提交，但列表刷新失败：${error.message}`, 'error');
    } else {
      await loadResult(true);
      notify(error.message, 'error');
    }
  } finally {
    state.loading = false;
  }
}

async function saveReview() {
  if (!activeItem.value.id) return;
  let recordsPayload = [];
  if (review.records.trim()) {
    try {
      recordsPayload = JSON.parse(review.records);
      if (!Array.isArray(recordsPayload)) throw new Error('records 必须是数组');
    } catch (error) {
      notify(`JSON 无法保存：${error.message}`, 'error');
      return;
    }
  }
  state.loading = true;
  try {
    await request(`/tasks/items/${encodeURIComponent(activeItem.value.id)}/review`, {
      method: 'PUT',
      body: JSON.stringify({
        needHumanReview: review.needHumanReview,
        records: recordsPayload,
        reviewer: review.reviewer.trim(),
        comment: review.comment.trim(),
        reviewReasons: review.comment.trim() ? [review.comment.trim()] : []
      })
    });
    await loadResult(true);
    notify('复核结论已保存', 'success');
  } catch (error) {
    notify(error.message, 'error');
  } finally {
    state.loading = false;
  }
}

async function exportTask() {
  if (!state.task?.parseTaskId) return;
  try {
    const result = await request(`/exports/records/${encodeURIComponent(state.task.parseTaskId)}`);
    const url = result.url?.startsWith('/files') ? `${API_PREFIX}${result.url}` : result.url;
    window.open(url, '_blank', 'noopener');
  } catch (error) {
    notify(error.message, 'error');
  }
}

async function initialize() {
  try {
    await Promise.all([loadOptions(), loadTasks()]);
    state.backendOnline = true;
    if (state.taskId) await loadTask(state.taskId);
    else {
      state.showCreate = true;
      state.showTaskEntry = true;
    }
  } catch (error) {
    state.backendOnline = false;
    notify(error.message, 'error');
  }
}

function selectResultItem(index) {
  state.activeItemIndex = Number(index);
  hydrateReview();
}

onMounted(initialize);
onBeforeUnmount(stopPolling);
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <a class="brand" href="/workbench/">
        <span class="brand-mark">T</span>
        <span><strong>Taxroom</strong><small>境外所得材料工作台</small></span>
      </a>
      <div class="topbar-center">
        <span class="workspace-name">Agent 协作台</span><span class="workspace-separator">/</span><span class="workspace-mode">Dialogue + Data workspace</span>
      </div>
      <div class="topbar-meta"><span class="live-dot" :class="{ success: state.backendOnline }"></span><span>{{ state.backendOnline ? '服务正常' : '服务未连接' }}</span></div>
    </header>

    <main class="agent-layout">
      <aside class="session-rail">
        <div class="rail-heading">
          <div><span class="section-kicker">TASKS</span><h2>任务记录</h2></div>
          <button class="icon-button" type="button" title="新建任务" aria-label="新建任务" @click="openTaskEntry">+</button>
        </div>
        <div class="recent-list">
          <div v-if="!state.tasks.length && !state.listLoading" class="rail-empty"><span class="empty-glyph">+</span><strong>暂无任务</strong><small>创建第一项境外所得执行任务</small></div>
          <button v-for="task in state.tasks" :key="task.id" class="recent-item" :class="{ active: String(task.id) === state.taskId && !state.showCreate }" @click="loadTask(task.id)">
            <span class="recent-status" :class="({ PROCESSING: 'running', COMPLETED: 'success', FAILED: 'fail' })[task.status] || 'pending'"></span>
            <span class="recent-copy"><strong>{{ task.incomeTypeLabel }}</strong><small>{{ task.uploadedMaterialCount }}/{{ task.expectedMaterialCount }} 项 · {{ formatDate(task.createdAt) }}</small></span>
            <span class="recent-arrow">›</span>
          </button>
        </div>
        <div class="rail-footer"><span class="connection-label">CONNECTION</span><div><span class="live-dot" :class="{ success: state.backendOnline }"></span>{{ state.backendOnline ? '服务已连接' : '等待服务连接' }}</div></div>
      </aside>

      <section class="conversation-panel">
        <header class="conversation-header">
          <div class="agent-identity"><span class="agent-avatar">T</span><div><h1>税务材料助手</h1><p>{{ state.createMode === 'chat' ? '协助识别所得类型' : (state.task?.incomeTypeLabel || '创建境外所得任务') }}</p></div></div>
          <span class="agent-state"><i></i>{{ state.chatLoading ? '思考中' : (state.task?.statusLabel || '在线') }}</span>
        </header>

        <div v-if="(state.showCreate || !state.task) && state.createMode === 'known'" class="creation-view">
          <div class="creation-heading"><span class="section-kicker">INTENT</span><h2>这次要处理哪类所得？</h2><p>选择所得类型后，Agent 会建立任务并在右侧工作台生成材料与数据区。</p></div>
          <div class="income-options">
            <label v-for="(option, index) in state.options" :key="option.code" class="income-option" :class="{ selected: state.selectedIncomeType === option.code }">
              <input v-model="state.selectedIncomeType" type="radio" :value="option.code" />
              <span class="income-index">{{ String(index + 1).padStart(2, '0') }}</span>
              <span class="income-copy"><strong>{{ option.label }}</strong><small>{{ option.materials.length }} 项预期材料</small></span>
              <span class="radio-mark"></span>
            </label>
          </div>
          <button class="primary-command" type="button" :disabled="state.loading || !state.selectedIncomeType" @click="createTask">创建任务 <span>→</span></button>
        </div>

        <section v-else-if="(state.showCreate || !state.task) && state.createMode === 'chat'" class="chat-setup-view">
          <header class="chat-setup-header">
            <div><span class="section-kicker">AI DIALOGUE</span><h2>一起确认所得类型</h2><p>描述收入的来源和产生方式，Agent 会继续追问。</p></div>
            <button class="text-command" type="button" @click="openTaskEntry">重新选择</button>
          </header>
          <div ref="chatStreamRef" class="chat-message-list" aria-live="polite">
            <article v-for="(message, index) in chatMessages" :key="`${message.createdAt}-${index}`" class="chat-message" :class="[message.role, { error: message.error }]">
              <span v-if="message.role === 'assistant'" class="message-avatar">T</span>
              <div class="chat-bubble"><div class="message-meta"><strong>{{ message.role === 'assistant' ? 'Tax Agent' : '你' }}</strong><span>{{ formatDate(message.createdAt) }}</span></div><p>{{ message.content }}</p></div>
            </article>
            <article v-if="state.chatLoading" class="chat-message assistant loading-message"><span class="message-avatar">T</span><div class="chat-bubble"><span class="typing-dot"></span><span class="typing-dot"></span><span class="typing-dot"></span><small>正在整理问题</small></div></article>
          </div>
          <form class="chat-composer" @submit.prevent="sendChat">
            <label class="visually-hidden" for="chat-message">描述这笔境外收入</label>
            <textarea id="chat-message" v-model="chatDraft" rows="2" maxlength="2000" :disabled="state.chatLoading" placeholder="例如：我持有一家境外公司的股票，今年收到一笔分红…" @keydown.enter.exact.prevent="sendChat"></textarea>
            <button class="chat-send-button" type="submit" title="发送消息" aria-label="发送消息" :disabled="state.chatLoading || !chatDraft.trim()">↑</button>
            <small>Shift + Enter 换行</small>
          </form>
        </section>

        <div v-else-if="state.showCreate || !state.task" class="creation-pending">
          <span class="pending-mark">?</span><h2>如何开始这次任务？</h2><p>请先选择是否已经知道所得类型。</p><button class="primary-command" type="button" @click="openTaskEntry">选择开始方式 <span>→</span></button>
        </div>

        <div v-else class="conversation-stream">
          <div class="date-divider">CURRENT SESSION</div>

          <article class="message user-message">
            <div class="message-body"><div class="message-meta"><strong>你</strong><span>{{ formatDate(state.task.createdAt) }}</span></div><p>需要处理 <b>{{ state.task.incomeTypeLabel }}</b> 相关材料，并整理成可导出的结构化记录。</p></div>
          </article>

          <article class="message agent-message">
            <span class="message-avatar">T</span>
            <div class="message-body">
              <div class="message-meta"><strong>Tax Agent</strong><span>INTENT</span></div>
              <p>已识别为“{{ state.task.incomeTypeLabel }}”处理任务。我会结合材料完整度、文档路由和质量规则推进处理。</p>
              <div class="capability-row"><span>意图已确认</span><span>{{ state.task.expectedMaterialCount }} 项材料</span><span>{{ state.task.fileCount }} 份文件</span></div>
            </div>
          </article>

          <article v-if="state.task.status === 'COLLECTING'" class="message agent-message followup-message">
            <span class="message-avatar">?</span>
            <div class="message-body"><div class="message-meta"><strong>追问与补充</strong><span>FOLLOW-UP</span></div><p v-if="state.task.missingMaterialCount">目前还有 <b>{{ state.task.missingMaterialCount }}</b> 项材料未补充。请在右侧“文件与材料”区上传，也可按当前材料直接开始处理。</p><p v-else>材料清单已完整，可在右侧工作台开始处理。</p></div>
          </article>

          <section class="agent-block progress-block">
            <div class="agent-block-heading"><div><span class="section-kicker">PROGRESS</span><h2>处理进度</h2></div><strong>{{ taskPhase }}/4</strong></div>
            <ol class="phase-list">
              <li :class="{ done: taskPhase >= 1 }"><span>01</span><div><strong>理解意图</strong><small>所得类型与任务范围</small></div></li>
              <li :class="{ done: taskPhase >= 2 }"><span>02</span><div><strong>收集材料</strong><small>{{ state.task.uploadedMaterialCount }}/{{ state.task.expectedMaterialCount }} 项已就绪</small></div></li>
              <li :class="{ done: taskPhase >= 3, active: state.task.status === 'PROCESSING' || state.task.status === 'FAILED' }"><span>03</span><div><strong>解析与校验</strong><small>{{ state.task.status === 'FAILED' ? '等待重试' : state.task.status === 'PROCESSING' ? '正在处理' : '路由、抽取与质量检查' }}</small></div></li>
              <li :class="{ done: taskPhase >= 4 }"><span>04</span><div><strong>复核与导出</strong><small>{{ state.task.status === 'COMPLETED' ? '结果已生成' : '等待结构化结果' }}</small></div></li>
            </ol>
          </section>

          <section class="agent-block decision-block">
            <div class="agent-block-heading"><div><span class="section-kicker">AGENT DECISION</span><h2>Agent 决策</h2></div><span class="decision-confidence">{{ formatConfidence(routeConfidence) }}</span></div>
            <dl class="decision-grid">
              <div><dt>处理路径</dt><dd>{{ routeSummary.variant || activeItem.route_variant || (state.task.status === 'COLLECTING' ? '等待提交后路由' : '正在确定模板') }}</dd></div>
              <div><dt>人工介入</dt><dd>{{ review.needHumanReview ? '需要复核' : '尚未要求' }}</dd></div>
              <div v-if="routeSummary.reason || activeItem.route_reason" class="decision-reason"><dt>决策依据</dt><dd>{{ routeSummary.reason || activeItem.route_reason }}</dd></div>
            </dl>
          </section>

          <div v-if="state.task.status === 'FAILED'" class="system-message error"><span class="system-rule"></span><p>本轮处理未完成，请在右侧异常区查看详情并重新提交。</p></div>
          <div v-else-if="state.task.status === 'COMPLETED'" class="system-message success"><span class="system-rule"></span><p>处理已完成，右侧已生成 {{ resultLabel }}，可继续复核或导出。</p></div>
        </div>

        <div v-if="state.notice" class="toast-message" :class="state.noticeType"><span></span>{{ state.notice }}</div>
      </section>

      <section class="workspace-panel">
        <header class="workspace-header">
          <div><span class="section-kicker">DATA WORKSPACE</span><h2>{{ state.task ? '文件与数据工作台' : '处理结果' }}</h2></div>
          <div v-if="state.task" class="task-actions">
            <button class="icon-text-button" type="button" title="刷新任务" aria-label="刷新任务" @click="refreshCurrent()">↻ <span>刷新</span></button>
            <button v-if="state.task.status === 'COMPLETED'" class="rerun-command" type="button" :disabled="!canRerun" title="重新执行任务" @click="openRerunConfirm">↻ <span>重新执行</span></button>
            <button class="export-button" type="button" :disabled="state.task.status !== 'COMPLETED'" @click="exportTask">↓ <span>导出 Excel</span></button>
          </div>
        </header>

        <div v-if="!state.task || state.showCreate" class="workspace-empty">
          <div class="document-placeholder"><span class="document-corner"></span><b>DATA</b><i></i><i></i><i></i><small>TAX WORKSPACE</small></div>
          <h3>{{ state.createMode === 'chat' ? '正在确认所得类型' : '等待任务' }}</h3><p>{{ state.createMode === 'chat' ? '完成对话后，可再回到所得类型选择流程。' : '完成左侧意图选择后，文件、结构化记录、证据行、异常与导出结果将集中在这里。' }}</p>
          <div class="empty-process"><span><b>01</b> 文件</span><span><b>02</b> 结构化记录</span><span><b>03</b> 证据与异常</span><span><b>04</b> 复核导出</span></div>
        </div>

        <div v-else class="workspace-content">
          <section class="task-summary">
            <div class="task-title"><span class="live-dot" :class="statusClass"></span><div><small>EXECUTION TASK</small><strong>{{ state.task.id }}</strong></div></div>
            <div class="summary-metrics"><div><small>所得类型</small><strong>{{ state.task.incomeTypeLabel }}</strong></div><div><small>状态</small><span class="status-label" :class="statusClass">{{ state.task.statusLabel }}</span></div><div><small>文件</small><strong>{{ state.task.fileCount }} 份</strong></div></div>
          </section>

          <div class="workspace-index" aria-label="工作台内容概览">
            <span><b>01</b>文件 {{ state.task.fileCount }}</span><span><b>02</b>记录 {{ records.length }}</span><span><b>03</b>证据 {{ evidenceRows.length }}</span><span :class="{ alert: reviewWarnings.length || state.task.errorMessage }"><b>04</b>异常 {{ reviewWarnings.length + (state.task.errorMessage ? 1 : 0) }}</span>
          </div>

          <section class="artifact-section files-section">
            <div class="artifact-heading">
              <div><span class="section-number">01</span><h3>文件与材料</h3></div>
              <span class="record-count">{{ state.task.uploadedMaterialCount }}/{{ state.task.expectedMaterialCount }} 项 · {{ materialProgress }}%</span>
            </div>
            <div class="completion-track"><i :style="{ width: `${materialProgress}%` }"></i></div>
            <div class="material-grid">
              <article v-for="(material, index) in state.task.materials" :key="material.code" class="material-row" :class="{ uploaded: material.uploaded }">
                <div class="material-row-head">
                  <span class="material-number">{{ String(index + 1).padStart(2, '0') }}</span>
                  <div><h3>{{ material.label }}</h3><small>{{ material.files.length ? `${material.files.length} 个文件` : '尚未上传' }}</small></div>
                  <span class="material-state">{{ material.uploaded ? '已就绪' : '待补充' }}</span>
                </div>
                <div v-if="material.files.length" class="file-list">
                  <div v-for="file in material.files" :key="file.id" class="file-item">
                    <a :href="`${API_PREFIX}${file.downloadUrl}`" target="_blank" rel="noopener"><span class="file-extension">{{ file.extension }}</span><span><strong>{{ file.name }}</strong><small>{{ formatSize(file.size) }}</small></span></a>
                    <button v-if="state.task.status === 'COLLECTING'" type="button" title="删除文件" aria-label="删除文件" @click="deleteFile(file)">×</button>
                  </div>
                </div>
                <label v-if="state.task.status === 'COLLECTING'" class="upload-command" :class="{ busy: state.uploadingMaterial === material.code }">
                  <input type="file" multiple accept=".pdf,.xls,.xlsx,.xlsm,.xlsb,.csv,.png,.jpg,.jpeg" :disabled="Boolean(state.uploadingMaterial)" @change="uploadFiles(material, $event)" />
                  <span>{{ state.uploadingMaterial === material.code ? '上传中…' : '+ 添加文件' }}</span>
                </label>
              </article>
            </div>
            <footer class="task-submit-bar">
              <div><strong>{{ state.task.complete ? '材料已齐备' : `缺少 ${state.task.missingMaterialCount} 项` }}</strong><small>{{ state.task.status === 'COLLECTING' ? '可继续补充，也可按当前文件提交' : '任务材料已锁定' }}</small></div>
              <button v-if="state.task.status === 'COLLECTING'" class="submit-command" type="button" :disabled="!canSubmit" @click="submitTask">开始处理 <span>→</span></button>
              <span v-else class="locked-label">已锁定</span>
            </footer>
          </section>

          <template v-if="state.task.status !== 'COLLECTING'">
            <section v-if="state.task.errorMessage" class="processing-error">
              <div><span>处理失败</span><p>{{ state.task.errorMessage }}</p></div>
              <button v-if="state.task.status === 'FAILED'" class="retry-command" type="button" :disabled="!canRerun" @click="openRerunConfirm">↻ <span>重新执行</span></button>
            </section>

            <section v-if="state.task.attempts?.length" class="attempt-history">
              <div class="attempt-history-heading"><div><span class="section-kicker">ATTEMPTS</span><h3>解析记录</h3></div><span>{{ state.task.attempts.length }} 次</span></div>
              <div class="attempt-list">
                <article v-for="attempt in state.task.attempts" :key="attempt.parseTaskId" class="attempt-row">
                  <span class="attempt-number">{{ String(attempt.attemptNo).padStart(2, '0') }}</span>
                  <div class="attempt-main"><strong>内部任务 {{ trimId(attempt.parseTaskId) }}</strong><small>{{ formatDate(attempt.startedAt) }}<template v-if="attempt.finishedAt"> · 完成于 {{ formatDate(attempt.finishedAt) }}</template></small><p v-if="attempt.errorMessage">{{ attempt.errorMessage }}</p></div>
                  <span class="status-label" :class="({ PROCESSING: 'running', COMPLETED: 'success', FAILED: 'fail' })[attempt.status] || 'pending'">{{ attempt.statusLabel }}</span>
                </article>
              </div>
            </section>
            <section class="artifact-section">
              <div class="artifact-heading">
                <div><span class="section-number">02</span><h3>结构化记录</h3></div>
                <div class="artifact-tools">
                  <select v-if="state.result?.items?.length > 1" :value="state.activeItemIndex" @change="selectResultItem($event.target.value)"><option v-for="(resultItem, index) in state.result.items" :key="resultItem.id" :value="index">文件 {{ index + 1 }}</option></select>
                  <span class="record-count">{{ resultLabel }}</span>
                </div>
              </div>
              <div class="results-content">
                <div v-if="!records.length" class="empty-result"><span class="empty-icon">∅</span><strong>{{ state.task.status === 'PROCESSING' ? '正在等待解析结果' : '没有可展示的记录' }}</strong><small>{{ parsedResult.warnings?.[0] || activeItem.review_reasons || state.task.errorMessage || '当前材料类型尚未配置专属抽取模板。' }}</small></div>
                <div v-else class="table-wrap"><table><thead><tr><th>#</th><th>日期</th><th>付款方</th><th>币种</th><th>净额</th><th>预扣税</th><th>毛额</th><th>证据行</th><th>置信度</th><th>质量</th></tr></thead><tbody><tr v-for="(record, index) in records" :key="record.record_id || index"><td class="row-index">{{ String(index + 1).padStart(2, '0') }}</td><td>{{ record.dividendDate || '—' }}</td><td class="primary-cell">{{ record.payer || '—' }}</td><td>{{ record.currency || '—' }}</td><td>{{ record.netAmount ?? '—' }}</td><td>{{ record.withholdingTax ?? '—' }}</td><td>{{ record.grossAmount ?? '—' }}</td><td><span class="evidence-chip">{{ record.evidenceRowIds?.length || 0 }} 行</span></td><td>{{ formatConfidence(record.confidence) }}</td><td><span class="quality-label" :class="{ alert: record.needHumanReview || record.qualityWarnings?.length }">{{ record.needHumanReview || record.qualityWarnings?.length ? '待复核' : '通过' }}</span></td></tr></tbody></table></div>
              </div>
              <div v-if="state.result" class="result-foot"><span>内部任务 {{ trimId(state.task.parseTaskId) }}</span><span>{{ routeSummary.variant || activeItem.route_variant || '等待模板路由' }}</span></div>
            </section>

            <section class="artifact-section evidence-section">
              <div class="artifact-heading"><div><span class="section-number">03</span><h3>证据行</h3></div><span class="record-count">{{ evidenceRows.length }} ROWS</span></div>
              <div v-if="evidenceRows.length" class="evidence-list">
                <article v-for="evidence in evidenceRows" :key="`${evidence.recordIndex}-${evidence.rowId}`" class="evidence-row"><span class="evidence-id">{{ evidence.rowId }}</span><div><strong>{{ evidence.payer }}</strong><small>{{ evidence.detail }}</small></div><span class="record-link">记录 {{ evidence.recordIndex }}</span></article>
              </div>
              <div v-else class="compact-empty">当前结果尚未关联可展示的原始证据行。</div>
            </section>

            <section class="artifact-section exception-section" :class="{ clear: !reviewWarnings.length && !state.task.errorMessage }">
              <div class="artifact-heading"><div><span class="section-number">04</span><h3>异常与质量提示</h3></div><span class="record-count">{{ reviewWarnings.length + (state.task.errorMessage ? 1 : 0) }} ISSUES</span></div>
              <ul v-if="reviewWarnings.length || state.task.errorMessage" class="exception-list"><li v-if="state.task.errorMessage">{{ state.task.errorMessage }}</li><li v-for="warning in reviewWarnings" :key="warning">{{ warning }}</li></ul>
              <div v-else class="quality-pass"><span>✓</span><div><strong>未发现已记录的质量异常</strong><small>可继续人工复核或导出当前结果。</small></div></div>
            </section>

            <section v-if="activeItem.id" class="artifact-section review-section">
              <div class="artifact-heading"><div><span class="section-number">05</span><h3>人工复核</h3></div><span class="record-count">REVIEW</span></div>
              <form class="review-form" @submit.prevent="saveReview">
                <label class="toggle-row"><input v-model="review.needHumanReview" type="checkbox" /><span class="toggle-ui"></span><span>标记为需要人工复核</span></label>
                <div class="review-fields"><label><span>复核人</span><input v-model="review.reviewer" type="text" placeholder="姓名或工号" /></label><label><span>复核备注</span><textarea v-model="review.comment" rows="2" placeholder="记录材料或结果问题"></textarea></label></div>
                <label class="record-editor"><span>修正后的 records JSON <small>可选</small></span><textarea v-model="review.records" rows="7" spellcheck="false" placeholder="提交完整 records 数组"></textarea></label>
                <div class="review-footer"><button class="save-button" type="submit" :disabled="state.loading">保存复核结论 <b>✓</b></button></div>
              </form>
            </section>
          </template>
        </div>
      </section>
    </main>

    <div v-if="state.showTaskEntry" class="modal-backdrop" role="presentation" @click.self="closeTaskEntry">
      <section class="task-entry-modal" role="dialog" aria-modal="true" aria-labelledby="task-entry-title">
        <header class="modal-header"><div><span class="section-kicker">NEW TASK</span><h2 id="task-entry-title">新建任务</h2></div><button class="modal-close" type="button" title="关闭" aria-label="关闭" @click="closeTaskEntry">×</button></header>
        <p class="modal-intro">先确认你是否知道这笔境外收入对应的所得类型。</p>
        <div class="task-entry-options">
          <button type="button" @click="chooseCreateMode('known')"><span class="entry-index">01</span><span><strong>我已知晓所得类型</strong><small>直接选择所得类型并创建材料清单</small></span><b>→</b></button>
          <button type="button" @click="chooseCreateMode('chat')"><span class="entry-index">02</span><span><strong>我不知道所得类型</strong><small>通过简单对话梳理收入来源和产生方式</small></span><b>→</b></button>
        </div>
      </section>
    </div>

    <div v-if="state.showRerunConfirm" class="modal-backdrop" role="presentation" @click.self="closeRerunConfirm">
      <section class="task-entry-modal rerun-confirm-modal" role="dialog" aria-modal="true" aria-labelledby="rerun-confirm-title" aria-describedby="rerun-confirm-description">
        <header class="modal-header"><div><span class="section-kicker">RERUN TASK</span><h2 id="rerun-confirm-title">确认重新执行</h2></div><button class="modal-close" type="button" title="关闭" aria-label="关闭" :disabled="state.loading" @click="closeRerunConfirm">×</button></header>
        <div class="rerun-confirm-content">
          <p id="rerun-confirm-description">将删除当前结构化结果、人工复核记录和解析历史，并使用现有源文件重新提交。</p>
          <dl><div><dt>保留</dt><dd>已上传的原始材料文件</dd></div><div><dt>删除</dt><dd>解析结果、复核记录、异常和尝试历史</dd></div></dl>
        </div>
        <footer class="rerun-confirm-actions"><button class="text-command" type="button" :disabled="state.loading" @click="closeRerunConfirm">取消</button><button class="confirm-rerun-command" type="button" :disabled="!canRerun" @click="rerunTask">↻ <span>{{ state.loading ? '正在重新执行' : '确认重新执行' }}</span></button></footer>
      </section>
    </div>
  </div>
</template>
