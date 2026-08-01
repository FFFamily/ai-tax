<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';

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
  selectedIncomeType: '',
  activeItemIndex: 0,
  uploadingMaterial: ''
});
const review = reactive({ needHumanReview: false, reviewer: '', comment: '', records: '' });
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
const canRetry = computed(() => state.task?.status === 'FAILED' && !state.loading);
const resultLabel = computed(() => records.value.length
  ? `${records.value.length} 条记录`
  : (state.task?.status === 'COMPLETED' ? '无结构化记录' : '等待处理结果'));

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

async function retryTask() {
  if (!canRetry.value) return;
  state.loading = true;
  try {
    state.task = await request(`/execution-tasks/${encodeURIComponent(state.task.id)}/retry`, { method: 'POST' });
    state.result = null;
    state.activeItemIndex = 0;
    await Promise.all([loadTasks(true), loadResult(true)]);
    startPolling();
    notify('任务已重新提交', 'success');
  } catch (error) {
    notify(error.message, 'error');
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
    else state.showCreate = true;
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
        <span class="workspace-name">执行任务</span><span class="workspace-separator">/</span><span class="workspace-mode">Agent workspace</span>
      </div>
      <div class="topbar-meta"><span class="live-dot" :class="{ success: state.backendOnline }"></span><span>{{ state.backendOnline ? '服务正常' : '服务未连接' }}</span></div>
    </header>

    <main class="agent-layout">
      <aside class="session-rail">
        <div class="rail-heading">
          <div><span class="section-kicker">TASKS</span><h2>任务记录</h2></div>
          <button class="icon-button" type="button" title="新建任务" @click="state.showCreate = true">+</button>
        </div>
        <div class="recent-list">
          <div v-if="!state.tasks.length && !state.listLoading" class="rail-empty"><span class="empty-glyph">+</span><strong>暂无任务</strong><small>创建第一项境外所得执行任务</small></div>
          <button v-for="task in state.tasks" :key="task.id" class="recent-item" :class="{ active: String(task.id) === state.taskId && !state.showCreate }" @click="loadTask(task.id)">
            <span class="recent-status" :class="({ PROCESSING: 'running', COMPLETED: 'success', FAILED: 'fail' })[task.status] || 'pending'"></span>
            <span class="recent-copy"><strong>{{ task.incomeTypeLabel }}</strong><small>{{ task.uploadedMaterialCount }}/{{ task.expectedMaterialCount }} 项 · {{ formatDate(task.createdAt) }}</small></span>
            <span class="recent-arrow">›</span>
          </button>
        </div>
        <div class="rail-footer"><span class="connection-label">CONNECTION</span><div><span class="live-dot" :class="{ success: state.backendOnline }"></span>{{ state.backendOnline ? 'Spring Boot 已连接' : '等待 localhost:8080' }}</div></div>
      </aside>

      <section class="conversation-panel">
        <header class="conversation-header">
          <div class="agent-identity"><span class="agent-avatar">T</span><div><h1>税务材料助手</h1><p>{{ state.task?.incomeTypeLabel || '创建境外所得任务' }}</p></div></div>
          <span class="agent-state"><i></i>{{ state.task?.statusLabel || '在线' }}</span>
        </header>

        <div v-if="state.showCreate || !state.task" class="creation-view">
          <div class="creation-heading"><span class="section-kicker">NEW TASK</span><h2>选择境外所得类型</h2><p>每项任务对应一种所得，创建后将生成专属材料清单。</p></div>
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

        <template v-else>
          <div class="material-stream">
            <div class="material-overview">
              <div><span class="section-kicker">MATERIALS</span><h2>材料清单</h2></div>
              <strong>{{ state.task.uploadedMaterialCount }}/{{ state.task.expectedMaterialCount }}</strong>
            </div>
            <div class="completion-track"><i :style="{ width: `${Math.round((state.task.uploadedMaterialCount / state.task.expectedMaterialCount) * 100)}%` }"></i></div>

            <article v-for="(material, index) in state.task.materials" :key="material.code" class="material-row" :class="{ uploaded: material.uploaded }">
              <div class="material-row-head">
                <span class="material-number">{{ String(index + 1).padStart(2, '0') }}</span>
                <div><h3>{{ material.label }}</h3><small>{{ material.files.length ? `${material.files.length} 个文件` : '尚未上传' }}</small></div>
                <span class="material-state">{{ material.uploaded ? '已就绪' : '待补充' }}</span>
              </div>
              <div v-if="material.files.length" class="file-list">
                <div v-for="file in material.files" :key="file.id" class="file-item">
                  <a :href="`${API_PREFIX}${file.downloadUrl}`" target="_blank" rel="noopener"><span class="file-extension">{{ file.extension }}</span><span><strong>{{ file.name }}</strong><small>{{ formatSize(file.size) }}</small></span></a>
                  <button v-if="state.task.status === 'COLLECTING'" type="button" title="删除文件" @click="deleteFile(file)">×</button>
                </div>
              </div>
              <label v-if="state.task.status === 'COLLECTING'" class="upload-command" :class="{ busy: state.uploadingMaterial === material.code }">
                <input type="file" multiple accept=".pdf,.xls,.xlsx,.xlsm,.xlsb,.csv,.png,.jpg,.jpeg" :disabled="Boolean(state.uploadingMaterial)" @change="uploadFiles(material, $event)" />
                <span>{{ state.uploadingMaterial === material.code ? '上传中…' : '+ 添加文件' }}</span>
              </label>
            </article>
          </div>

          <footer class="task-submit-bar">
            <div><strong>{{ state.task.complete ? '材料已齐备' : `缺少 ${state.task.missingMaterialCount} 项` }}</strong><small>{{ state.task.status === 'COLLECTING' ? '缺失材料不影响提交' : '任务材料已锁定' }}</small></div>
            <button v-if="state.task.status === 'COLLECTING'" class="submit-command" type="button" :disabled="!canSubmit" @click="submitTask">开始处理 <span>→</span></button>
            <span v-else class="locked-label">已锁定</span>
          </footer>
        </template>

        <div v-if="state.notice" class="toast-message" :class="state.noticeType"><span></span>{{ state.notice }}</div>
      </section>

      <section class="workspace-panel">
        <header class="workspace-header">
          <div><span class="section-kicker">ARTIFACTS</span><h2>{{ state.task ? '任务工作区' : '处理结果' }}</h2></div>
          <div v-if="state.task" class="task-actions">
            <button class="icon-text-button" type="button" title="刷新任务" @click="refreshCurrent()">↻ <span>刷新</span></button>
            <button class="export-button" type="button" :disabled="state.task.status !== 'COMPLETED'" @click="exportTask">↓ <span>导出 Excel</span></button>
          </div>
        </header>

        <div v-if="!state.task || state.showCreate" class="workspace-empty">
          <div class="document-placeholder"><span class="document-corner"></span><b>01</b><i></i><i></i><i></i><small>TAX DOCUMENT</small></div>
          <h3>等待任务</h3><p>创建任务后，材料完整度、处理状态和结构化结果会集中显示在这里。</p>
          <div class="empty-process"><span><b>01</b> 选择所得</span><span><b>02</b> 上传材料</span><span><b>03</b> 提交处理</span><span><b>04</b> 结果复核</span></div>
        </div>

        <div v-else class="workspace-content">
          <section class="task-summary">
            <div class="task-title"><span class="live-dot" :class="statusClass"></span><div><small>EXECUTION TASK</small><strong>{{ state.task.id }}</strong></div></div>
            <div class="summary-metrics"><div><small>所得类型</small><strong>{{ state.task.incomeTypeLabel }}</strong></div><div><small>状态</small><span class="status-label" :class="statusClass">{{ state.task.statusLabel }}</span></div><div><small>文件</small><strong>{{ state.task.fileCount }} 份</strong></div></div>
          </section>

          <section v-if="state.task.status === 'COLLECTING'" class="collection-summary">
            <div class="collection-score"><strong>{{ Math.round((state.task.uploadedMaterialCount / state.task.expectedMaterialCount) * 100) }}%</strong><span>材料完整度</span></div>
            <div class="missing-list"><span class="section-kicker">PENDING</span><h3>{{ state.task.missingMaterialCount ? '仍待补充的材料' : '材料清单已完整' }}</h3><p v-for="material in state.task.missingMaterials" :key="material.code">{{ material.label }}</p><p v-if="!state.task.missingMaterialCount">可以提交进入处理流程。</p></div>
          </section>

          <template v-else>
            <section v-if="state.task.errorMessage" class="processing-error">
              <div><span>处理失败</span><p>{{ state.task.errorMessage }}</p></div>
              <button v-if="state.task.status === 'FAILED'" class="retry-command" type="button" :disabled="!canRetry" @click="retryTask">↻ <span>{{ state.loading ? '提交中' : '重新提交' }}</span></button>
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
                <div><span class="section-number">01</span><h3>结构化记录</h3></div>
                <div class="artifact-tools">
                  <select v-if="state.result?.items?.length > 1" :value="state.activeItemIndex" @change="selectResultItem($event.target.value)"><option v-for="(resultItem, index) in state.result.items" :key="resultItem.id" :value="index">文件 {{ index + 1 }}</option></select>
                  <span class="record-count">{{ resultLabel }}</span>
                </div>
              </div>
              <div class="results-content">
                <div v-if="!records.length" class="empty-result"><span class="empty-icon">∅</span><strong>{{ state.task.status === 'PROCESSING' ? '正在等待解析结果' : '没有可展示的记录' }}</strong><small>{{ parsedResult.warnings?.[0] || activeItem.review_reasons || state.task.errorMessage || '当前材料类型尚未配置专属抽取模板。' }}</small></div>
                <div v-else class="table-wrap"><table><thead><tr><th>日期</th><th>付款方</th><th>币种</th><th>净额</th><th>预扣税</th><th>毛额</th><th>置信度</th></tr></thead><tbody><tr v-for="(record, index) in records" :key="record.record_id || index"><td>{{ record.dividendDate || '—' }}</td><td class="primary-cell">{{ record.payer || '—' }}</td><td>{{ record.currency || '—' }}</td><td>{{ record.netAmount ?? '—' }}</td><td>{{ record.withholdingTax ?? '—' }}</td><td>{{ record.grossAmount ?? '—' }}</td><td>{{ record.confidence != null ? Math.round(Number(record.confidence) * 100) + '%' : '—' }}</td></tr></tbody></table></div>
              </div>
              <div v-if="state.result" class="result-foot"><span>内部任务 {{ trimId(state.task.parseTaskId) }}</span><span>{{ routeSummary.variant || activeItem.route_variant || '等待模板路由' }}</span></div>
            </section>

            <section v-if="activeItem.id" class="artifact-section review-section">
              <div class="artifact-heading"><div><span class="section-number">02</span><h3>人工复核</h3></div><span class="record-count">REVIEW</span></div>
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
  </div>
</template>
