<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';

const API_PREFIX = import.meta.env.DEV ? '/api' : '';
const state = reactive({ taskId: localStorage.getItem('taxroom.taskId') || '', task: null, loading: false, notice: '', noticeType: '', backendOnline: false });
const form = reactive({ fileUrl: '', documentType: '', documentId: '' });
const review = reactive({ needHumanReview: false, reviewer: '', comment: '', records: '' });
const recentTasks = ref(JSON.parse(localStorage.getItem('taxroom.tasks') || '[]'));
let pollingTimer;

const item = computed(() => state.task?.items?.[0] || {});
const parsedResult = computed(() => {
  if (!item.value.change_result) return {};
  try { return typeof item.value.change_result === 'string' ? JSON.parse(item.value.change_result) : item.value.change_result; } catch { return {}; }
});
const records = computed(() => {
  const result = parsedResult.value;
  return result.records?.length ? result.records : (result.globalParam?.dividendExtractRecords || []);
});
const routeSummary = computed(() => item.value.route_summary || {});
const statusLabel = computed(() => ({ RUNNING: '处理中', SUCCESS: '已完成', FAIL: '失败' }[state.task?.status] || state.task?.status || '待处理'));
const statusClass = computed(() => ({ RUNNING: 'running', SUCCESS: 'success', FAIL: 'fail' }[state.task?.status] || 'pending'));
const resultLabel = computed(() => records.value.length ? `${records.value.length} 条记录` : (state.task?.status === 'SUCCESS' ? '无结构化记录' : '等待任务完成'));

async function request(path, options = {}) {
  const response = await fetch(`${API_PREFIX}${path}`, { headers: { 'content-type': 'application/json', ...(options.headers || {}) }, ...options });
  const payload = response.headers.get('content-type')?.includes('json') ? await response.json() : await response.text();
  if (!response.ok || (payload && payload.code && payload.code !== 200)) throw new Error(payload?.message || `请求失败 (${response.status})`);
  return payload?.data ?? payload;
}

function notify(message, type = '') { state.notice = message; state.noticeType = type; }
function esc(value) { return String(value ?? '').replace(/[&<>'"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[c])); }
function trimId(value) { return String(value || '').slice(0, 12); }

function saveRecent(task) {
  const list = recentTasks.value.filter((entry) => entry.id !== task.id);
  list.unshift({ id: task.id, status: task.status, url: item.value.file_url || '' });
  recentTasks.value = list.slice(0, 8);
  localStorage.setItem('taxroom.tasks', JSON.stringify(recentTasks.value));
}

function hydrateReview() {
  review.needHumanReview = Boolean(item.value.need_human_review || parsedResult.value.globalParam?.needHumanReview);
  review.reviewer = parsedResult.value.globalParam?.reviewer || '';
  review.comment = parsedResult.value.globalParam?.reviewComment || '';
  review.records = records.value.length ? JSON.stringify(records.value, null, 2) : '';
}

async function loadTask(taskId, quiet = false) {
  if (!taskId) return;
  state.taskId = taskId;
  localStorage.setItem('taxroom.taskId', taskId);
  if (!quiet) state.loading = true;
  try {
    state.task = await request(`/tasks/${encodeURIComponent(taskId)}`);
    saveRecent(state.task);
    hydrateReview();
    if (state.task.status === 'RUNNING') startPolling(); else stopPolling();
  } catch (error) { notify(error.message, 'error'); }
  finally { state.loading = false; }
}

function startPolling() { stopPolling(); pollingTimer = window.setInterval(() => loadTask(state.taskId, true), 5000); }
function stopPolling() { if (pollingTimer) window.clearInterval(pollingTimer); pollingTimer = undefined; }

async function createTask() {
  state.loading = true; notify('正在创建任务…');
  try {
    const result = await request('/tasks', { method: 'POST', body: JSON.stringify({ items: [{ fileUrl: form.fileUrl.trim(), documentType: form.documentType || null, documentId: form.documentId.trim() || null }] }) });
    notify(`任务 ${result.taskId} 已提交`, 'success');
    await loadTask(result.taskId);
  } catch (error) { notify(error.message, 'error'); }
  finally { state.loading = false; }
}

async function saveReview() {
  let recordsPayload = [];
  if (review.records.trim()) {
    try { recordsPayload = JSON.parse(review.records); if (!Array.isArray(recordsPayload)) throw new Error('records 必须是数组'); }
    catch (error) { notify(`JSON 无法保存：${error.message}`, 'error'); return; }
  }
  state.loading = true;
  try {
    await request(`/tasks/items/${encodeURIComponent(item.value.id)}/review`, { method: 'PUT', body: JSON.stringify({ needHumanReview: review.needHumanReview, records: recordsPayload, reviewer: review.reviewer.trim(), comment: review.comment.trim(), reviewReasons: review.comment.trim() ? [review.comment.trim()] : [] }) });
    notify('复核结论已保存', 'success');
    await loadTask(state.taskId, true);
  } catch (error) { notify(error.message, 'error'); }
  finally { state.loading = false; }
}

async function exportTask() {
  try {
    const result = await request(`/exports/records/${encodeURIComponent(state.taskId)}`);
    const url = result.url?.startsWith('/files') ? `${API_PREFIX}${result.url}` : result.url;
    window.open(url, '_blank', 'noopener');
  } catch (error) { notify(error.message, 'error'); }
}

async function checkBackend() {
  try { await request('/areas'); state.backendOnline = true; } catch { state.backendOnline = false; }
}

onMounted(() => { checkBackend(); if (state.taskId) loadTask(state.taskId); });
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
        <span class="workspace-name">材料解析</span>
        <span class="workspace-separator">/</span>
        <span class="workspace-mode">Agent workspace</span>
      </div>
      <div class="topbar-meta">
        <span class="live-dot" :class="{ success: state.backendOnline }"></span>
        <span>{{ state.backendOnline ? '服务正常' : '服务未连接' }}</span>
      </div>
    </header>

    <main class="agent-layout">
      <aside class="session-rail">
        <div class="rail-heading">
          <div><span class="section-kicker">SESSIONS</span><h2>任务记录</h2></div>
          <button class="icon-button" title="刷新连接" @click="checkBackend">↻</button>
        </div>
        <div class="recent-list">
          <div v-if="!recentTasks.length" class="rail-empty">
            <span class="empty-glyph">+</span>
            <strong>暂无任务</strong>
            <small>首份材料将在这里留下记录</small>
          </div>
          <button v-for="entry in recentTasks" :key="entry.id" class="recent-item" :class="{ active: entry.id === state.taskId }" @click="loadTask(entry.id)">
            <span class="recent-status" :class="({ RUNNING: 'running', SUCCESS: 'success', FAIL: 'fail' })[entry.status] || 'pending'"></span>
            <span class="recent-copy"><strong>{{ trimId(entry.id) }}</strong><small>{{ entry.url || '未记录材料地址' }}</small></span>
            <span class="recent-arrow">›</span>
          </button>
        </div>
        <div class="rail-footer">
          <span class="connection-label">CONNECTION</span>
          <div><span class="live-dot" :class="{ success: state.backendOnline }"></span>{{ state.backendOnline ? 'Spring Boot 已连接' : '等待 localhost:8080' }}</div>
        </div>
      </aside>

      <section class="conversation-panel">
        <header class="conversation-header">
          <div class="agent-identity">
            <span class="agent-avatar">T</span>
            <div><h1>税务材料助手</h1><p>文档识别与结构化工作流</p></div>
          </div>
          <span class="agent-state"><i></i>在线</span>
        </header>

        <div class="conversation-stream">
          <div class="date-divider"><span>当前会话</span></div>
          <article class="message agent-message">
            <span class="message-avatar">T</span>
            <div class="message-body">
              <div class="message-meta"><strong>Taxroom Agent</strong><span>刚刚</span></div>
              <p>把需要整理的银行流水或券商对账单地址发给我。我会先识别材料类型，再进行字段抽取和质量检查。</p>
              <div class="capability-row"><span>PDF</span><span>Excel</span><span>股息红利</span><span>银行流水</span></div>
            </div>
          </article>

          <article v-if="state.task" class="message agent-message task-message">
            <span class="message-avatar">T</span>
            <div class="message-body">
              <div class="message-meta"><strong>解析进度</strong><span>{{ statusLabel }}</span></div>
              <p>任务 <code>{{ trimId(state.task.id) }}</code> 已进入处理流程，右侧工作区会同步展示路由判断和结构化结果。</p>
              <div class="inline-progress">
                <span class="progress-node done">材料接收</span><i></i>
                <span class="progress-node" :class="{ done: state.task.status !== 'RUNNING' }">结构化抽取</span><i></i>
                <span class="progress-node" :class="{ done: state.task.status === 'SUCCESS' }">质量检查</span>
              </div>
            </div>
          </article>

          <article v-if="state.notice" class="message system-message" :class="state.noticeType">
            <span class="system-rule"></span><p>{{ state.notice }}</p>
          </article>
        </div>

        <form class="composer" @submit.prevent="createTask">
          <div class="composer-main">
            <label class="visually-hidden" for="file-url">材料地址</label>
            <input id="file-url" v-model="form.fileUrl" type="url" placeholder="输入 PDF 或 Excel 材料地址…" required />
            <button class="send-button" type="submit" :disabled="state.loading" title="提交材料">{{ state.loading ? '···' : '↑' }}</button>
          </div>
          <div class="composer-options">
            <label><span>材料类型</span><select id="document-type" v-model="form.documentType"><option value="">自动判断</option><option value="DIVIDEND">股息 / 红利</option><option value="BANK_STATEMENT">银行流水</option><option value="BROKER_STATEMENT">券商对账单</option></select></label>
            <label class="template-option"><span>指定模板</span><input id="document-id" v-model="form.documentId" type="text" placeholder="可选" /></label>
            <span class="composer-hint">输入地址后提交</span>
          </div>
        </form>
      </section>

      <section class="workspace-panel">
        <header class="workspace-header">
          <div><span class="section-kicker">ARTIFACTS</span><h2>{{ state.task ? '解析结果' : '任务工作区' }}</h2></div>
          <div v-if="state.task" class="task-actions">
            <button class="icon-text-button" title="刷新任务" @click="loadTask(state.taskId)">↻ <span>刷新</span></button>
            <button class="export-button" :disabled="state.task.status !== 'SUCCESS'" @click="exportTask">↓ <span>导出 Excel</span></button>
          </div>
        </header>

        <div v-if="!state.task" class="workspace-empty">
          <div class="document-placeholder"><span class="document-corner"></span><b>01</b><i></i><i></i><i></i><small>TAX DOCUMENT</small></div>
          <h3>等待材料</h3>
          <p>解析结果、证据和复核项会出现在这个工作区。</p>
          <div class="empty-process"><span><b>01</b> 材料识别</span><span><b>02</b> 模板路由</span><span><b>03</b> 结构化抽取</span><span><b>04</b> 质量校验</span></div>
        </div>

        <div v-else class="workspace-content">
          <section class="task-summary">
            <div class="task-title"><span class="live-dot" :class="statusClass"></span><div><small>TASK ID</small><strong>{{ state.task.id }}</strong></div></div>
            <div class="summary-metrics"><div><small>状态</small><span class="status-label" :class="statusClass">{{ statusLabel }}</span></div><div><small>路由模板</small><strong>{{ routeSummary.variant || item.route_variant || '自动路由' }}</strong></div><div><small>置信度</small><strong>{{ routeSummary.confidence != null ? Math.round(Number(routeSummary.confidence) * 100) + '%' : '—' }}</strong></div></div>
          </section>

          <section class="artifact-section">
            <div class="artifact-heading"><div><span class="section-number">01</span><h3>结构化记录</h3></div><span class="record-count">{{ resultLabel }}</span></div>
            <div class="results-content">
              <div v-if="!records.length" class="empty-result"><span class="empty-icon">∅</span><strong>{{ state.task.status === 'RUNNING' ? '正在等待解析结果' : '没有可展示的记录' }}</strong><small>{{ parsedResult.warnings?.[0] || item.review_reasons || '任务完成后，结构化记录会显示在这里。' }}</small></div>
              <div v-else class="table-wrap"><table><thead><tr><th>日期</th><th>付款方</th><th>币种</th><th>净额</th><th>预扣税</th><th>毛额</th><th>置信度</th></tr></thead><tbody><tr v-for="(record, index) in records" :key="record.record_id || index"><td>{{ record.dividendDate || '—' }}</td><td class="primary-cell">{{ record.payer || '—' }}</td><td>{{ record.currency || '—' }}</td><td>{{ record.netAmount ?? '—' }}</td><td>{{ record.withholdingTax ?? '—' }}</td><td>{{ record.grossAmount ?? '—' }}</td><td>{{ record.confidence != null ? Math.round(Number(record.confidence) * 100) + '%' : '—' }}</td></tr></tbody></table></div>
            </div>
            <div v-if="records.length" class="result-foot"><span>{{ item.need_human_review ? '需要人工确认' : '自动质量检查未发现阻断问题' }}</span><span>{{ parsedResult.warnings?.length ? parsedResult.warnings.join(' / ') : '证据行已随结果保留' }}</span></div>
          </section>

          <section class="artifact-section review-section">
            <div class="artifact-heading"><div><span class="section-number">02</span><h3>人工复核</h3></div><span class="record-count">REVIEW</span></div>
            <form class="review-form" @submit.prevent="saveReview">
              <label class="toggle-row"><input v-model="review.needHumanReview" type="checkbox" /><span class="toggle-ui"></span><span>标记为需要人工复核</span></label>
              <div class="review-fields"><label><span>复核人</span><input id="reviewer" v-model="review.reviewer" type="text" placeholder="姓名或工号" /></label><label><span>复核备注</span><textarea id="review-comment" v-model="review.comment" rows="2" placeholder="说明路由、金额或证据行的问题"></textarea></label></div>
              <label class="record-editor"><span>修正后的 records JSON <small>可选</small></span><textarea id="review-records" v-model="review.records" class="code-input" rows="7" spellcheck="false" placeholder="提交完整 records 数组即可覆盖 AI 结果"></textarea></label>
              <div class="review-footer"><button class="save-button" type="submit" :disabled="state.loading">保存复核结论 <b>✓</b></button></div>
            </form>
          </section>
        </div>
      </section>
    </main>
  </div>
</template>
