<template>
  <main class="migration-page">
    <header class="page-heading">
      <div><h1>数据迁移</h1><p>DM / Oracle 全量离线迁移 · 独立后台任务，不改变工作区连接</p></div>
      <div><el-button v-if="editing" size="small" :disabled="busy || selectingAll" @click="backToList">返回任务列表</el-button><el-button v-else type="primary" size="small" icon="el-icon-plus" :disabled="busy" @click="newTask">新建迁移</el-button></div>
    </header>

    <section v-if="!editing" class="panel task-panel">
      <div class="toolbar"><el-input v-model="taskSearch" size="small" clearable placeholder="搜索迁移名称" @keyup.enter="loadList(true)" @clear="loadList(true)" /><el-button size="small" :loading="loading" @click="loadList(true)">刷新列表</el-button><span class="muted">仅显示自己的任务</span></div>
      <div class="table-region"><el-table :data="tasks" height="100%" size="small" empty-text="尚无迁移任务，点击右上角新建迁移" v-loading="loading">
        <el-table-column label="迁移名称" min-width="180"><template #default="s"><el-button type="text" @click="openTask(s.row.id)">{{ s.row.config.name }}</el-button></template></el-table-column>
        <el-table-column label="方向" prop="direction" width="125" />
        <el-table-column label="源端 → 目标端" min-width="200"><template #default="s">{{ sourceName(s.row.config.sourceId, s.row.sourceName) }} → {{ sourceName(s.row.config.targetId, s.row.targetName) }}</template></el-table-column>
        <el-table-column label="状态" width="120"><template #default="s"><el-tag size="small" :type="statusType(s.row.status)">{{ statusLabel(s.row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="已确认提交行数" width="140" prop="committedRows" />
        <el-table-column label="创建时间" min-width="150"><template #default="s">{{ time(s.row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="190"><template #default="s"><el-button type="text" @click="openTask(s.row.id)">查看</el-button><el-button type="text" @click="copyTask(s.row.id)">复制配置</el-button><el-button type="text" class="delete-action" :disabled="activeStatuses.includes(s.row.status) || busy" @click="deleteTask(s.row)">删除</el-button></template></el-table-column>
      </el-table></div>
      <el-pagination class="pagination" v-model:current-page="taskPage" :page-size="20" :total="taskTotal" layout="total, prev, pager, next" @current-change="loadList()" />
    </section>

    <template v-else>
      <section class="panel step-panel"><el-steps :active="step" finish-status="success" :align-center="true"><el-step v-for="name in steps" :key="name" :title="name" /></el-steps></section>
      <section class="panel editor-panel" v-loading="busy">
        <div class="editor-body" :class="{ 'table-step': step === 1, 'mapping-step': step === 2 }">
        <template v-if="step === 0">
          <h2>连接配置</h2><p class="muted">选择已保存的数据源，只有点击连接后才读取该端信息。此处不会修改连接配置或保存口令。</p>
          <el-form label-position="top" size="small" :disabled="!editable">
            <el-form-item label="迁移名称"><el-input v-model="config.name" maxlength="80" placeholder="填写便于识别的迁移名称" class="name-input" /></el-form-item>
            <div class="endpoint-grid">
              <div v-for="side in ['source', 'target']" :key="side" class="endpoint">
                <h3>{{ side === 'source' ? '源端 · 只读' : '目标端 · 写入' }}</h3>
                <el-form-item :label="side === 'source' ? '源连接' : '目标连接'"><el-select v-model="config[side + 'Id']" filterable placeholder="选择 DM / Oracle 数据源" @change="endpointChanged(side)"><el-option v-for="source in sources" :key="source.id" :value="source.id" :label="source.alias + ' · ' + source.type" /></el-select></el-form-item>
                <el-button size="small" :disabled="!config[side + 'Id']" :loading="connecting[side]" @click="connectEndpoint(side)">连接并读取模式</el-button>
                <div v-if="endpoints[side]" class="connection-summary"><p>{{ endpoints[side].name }} · {{ endpoints[side].type }}</p><p>数据库：{{ endpoints[side].version }}</p><p>驱动：{{ endpoints[side].driver }}</p></div>
              </div>
            </div>
          </el-form>
        </template>

        <template v-if="step === 1">
          <h2>选择对象 <span class="heading-note">已选 {{ config.tables.length }} 个对象</span></h2>
          <p class="muted">切换模式、搜索和翻页均保留选择。普通表自动映射；序列、视图、同义词和程序对象必须在下一步逐个审阅目标定义。</p>
          <div class="toolbar"><el-select v-model="config.sourceSchema" size="small" filterable placeholder="源业务模式" :disabled="!editable || selectingAll" @change="sourceSchemaChanged"><el-option v-for="schema in schemas('source')" :key="schema.name" :value="schema.name" :label="schema.name" /></el-select><el-input v-model="objectSearch" size="small" clearable placeholder="搜索对象名称" :disabled="selectingAll" @keyup.enter="loadObjects(true)" @clear="loadObjects(true)" /><el-button size="small" :disabled="!config.sourceSchema || selectingAll" @click="loadObjects(true)">查询</el-button><el-button size="small" :loading="selectingAll" :disabled="!editable || objectLoading || !objectTotal" @click="selectAllObjects">全选筛选结果</el-button><el-button size="small" :disabled="!editable || selectingAll || objectLoading || !objects.length" @click="selectVisible">选择本页</el-button><el-button size="small" :disabled="!editable || selectingAll || !config.tables.length" @click="clearSelection">清空选择</el-button></div>
          <div class="table-region"><el-table :data="objects" height="100%" size="small" v-loading="objectLoading" empty-text="选择源模式后查询对象">
            <el-table-column width="48"><template #default="s"><el-checkbox :model-value="selected(s.row.name, s.row.type)" :disabled="!editable || selectingAll || !supportedObject(s.row.type)" :aria-label="'选择 ' + s.row.name" @change="toggleObject(s.row, $event)" /></template></el-table-column>
            <el-table-column label="对象名称" prop="name" min-width="220" /><el-table-column label="类型" prop="type" width="180" /><el-table-column label="执行范围"><template #default="s">{{ s.row.type === 'TABLE' ? '普通表，预检查确认支持范围' : supportedObject(s.row.type) ? '人工确认目标 DDL / 依赖 / 编译校验' : '专项适配，当前不可执行' }}</template></el-table-column>
          </el-table></div>
          <div class="object-pagination"><span class="muted">全选仅包含当前模式与搜索条件下的可迁移对象，覆盖所有分页。</span><el-pagination :disabled="selectingAll" v-model:current-page="objectPage" :page-size="50" :total="objectTotal" layout="total, prev, pager, next" @current-change="loadObjects()" /></div>
        </template>

        <template v-if="step === 2">
          <h2>映射策略</h2>
          <el-form label-position="top" size="small" :disabled="!editable" class="mapping-form">
            <el-tabs v-model="mappingTab" class="mapping-tabs">
              <el-tab-pane label="对象映射" name="objects">
            <div class="default-rules"><el-form-item label="默认目标业务模式"><el-select v-model="config.targetSchema" filterable placeholder="选择已有目标模式" @change="targetSchemaChanged"><el-option v-for="schema in schemas('target')" :key="schema.name" :value="schema.name" :label="schema.name" /></el-select></el-form-item><el-form-item label="默认迁移范围"><el-select v-model="config.scope" @change="config.conflict = 'STOP'"><el-option value="ALL" label="结构 + 数据" /><el-option value="STRUCTURE" label="仅结构" /><el-option value="DATA" label="仅数据 · 预建兼容表" /></el-select></el-form-item><el-form-item label="同名对象 / 数据处理"><el-select v-model="config.conflict"><el-option value="STOP" :label="config.scope === 'DATA' ? '空目标写入 · 推荐' : '同名阻断 · 推荐'" /><el-option v-if="config.scope !== 'DATA'" value="SKIP" label="跳过 · 不计为全部验收" /><el-option v-if="config.scope === 'DATA'" value="APPEND" label="追加 · 高风险，合并内容校验" /><el-option v-if="config.scope === 'DATA'" value="CLEAR" label="清空后写入 · 高风险" /><el-option v-if="config.scope !== 'DATA'" value="REBUILD" label="删除重建 · 高风险，不级联" /></el-select></el-form-item></div>
            <p class="mapping-note muted">未设置单对象策略时继承默认规则；字段、筛选及 DDL 通过“对象配置”编辑。批次、并发等参数在“执行设置”。</p>
            <div class="table-region"><el-table :data="pagedMappings" height="100%" size="small" empty-text="请先选择源对象">
              <el-table-column label="源模式 / 对象" min-width="210"><template #default="s">{{ s.row.sourceSchema || config.sourceSchema }}.{{ s.row.source }}<p class="muted">{{ s.row.type || 'TABLE' }}</p></template></el-table-column>
              <el-table-column label="目标模式" min-width="180"><template #default="s"><el-select v-model="s.row.targetSchema" size="small" clearable filterable placeholder="使用默认目标模式" @change="s.row.ddlConfirmed = false"><el-option v-for="schema in schemas('target')" :key="schema.name" :value="schema.name" :label="schema.name" /></el-select></template></el-table-column>
              <el-table-column label="目标对象" min-width="170"><template #default="s"><el-input v-model="s.row.target" size="small" maxlength="128" @input="s.row.ddlConfirmed = false" /></template></el-table-column>
              <el-table-column label="对象策略 / 筛选" min-width="170"><template #default="s">{{ scopeLabel(effectiveScope(s.row)) }}<p>{{ conflictLabel(effectiveConflict(s.row), effectiveScope(s.row)) }} · {{ (s.row.filters || []).length }} 个条件</p></template></el-table-column>
              <el-table-column label="对象配置" width="140" fixed="right"><template #default="s"><el-button type="text" :disabled="!editable" @click="editMapping(s.row)">对象配置</el-button><p v-if="s.row.type && s.row.type !== 'TABLE'" :class="s.row.ddlConfirmed ? 'muted' : 'danger'">{{ s.row.ddlConfirmed ? '已人工确认' : '目标 DDL 待确认' }}</p><p v-else class="muted">字段 / 策略 / 筛选</p></template></el-table-column>
              <el-table-column label="操作" width="65" fixed="right"><template #default="s"><el-button type="text" :disabled="!editable" @click="removeMapping(s.row)">移除</el-button></template></el-table-column>
            </el-table></div>
            <el-pagination v-if="config.tables.length > 50" class="mapping-pagination" v-model:current-page="mappingPage" :page-size="50" :total="config.tables.length" layout="total, prev, pager, next" />
            <p class="mapping-safety" :class="highRisk ? 'danger' : 'muted'">默认不改既有数据。追加/清空仅用于兼容预建表；删除重建不级联，不自动处理其他表的引用。高风险策略须逐表审阅和备份后确认。DATE 映射为 TIMESTAMP(0)。</p>
              </el-tab-pane>
              <el-tab-pane label="执行设置" name="execution">
                <div class="settings-scroll">
                  <section class="setting-group"><h3>复制与事务</h3><div class="setting-grid">
                    <el-form-item label="读取批次（行）"><el-input-number v-model="config.fetchSize" :min="1" :max="1000" :step="50" controls-position="right" /><p class="muted">普通数据按批读取；LOB 实际逐行读取。</p></el-form-item>
                    <el-form-item label="事务提交批次（行）"><el-input-number v-model="config.commitSize" :min="1" :max="1000" :step="100" controls-position="right" /><p class="muted">决定事务提交粒度，范围 1～1000 行。</p></el-form-item>
                  </div></section>
                  <section class="setting-group"><h3>并发与限速</h3><div class="setting-grid">
                    <el-form-item label="表级并发（1～4）"><el-input-number v-model="config.parallelism" :min="1" :max="4" :precision="0" controls-position="right" /><p class="muted">依赖按序，索引 / 外键 / 程序对象随后执行；LOB 表始终单独串行，避免内存与连接失控。</p></el-form-item>
                    <el-form-item label="复制行速率（行/秒，0 不限速）"><el-input-number v-model="config.rowsPerSecond" :min="0" :max="100000" :precision="0" controls-position="right" /><p class="muted">所有工作线程共享任务限速；提交批次不超过一秒速率。不限制 LOB 字节。</p></el-form-item>
                  </div></section>
                  <section class="setting-group"><h3>目标存储与异常处理</h3><div class="setting-grid">
                    <el-form-item label="默认目标表空间（不移动既有表）"><el-select v-model="config.targetTablespace" clearable filterable placeholder="目标用户默认表空间"><el-option v-for="space in tablespaces" :key="space.name" :label="space.name" :value="space.name" /></el-select><p class="muted">仅选择已存在的可见永久表空间，不创建表空间、不修改配额；实际容量及权限在执行时核对。</p></el-form-item>
                    <el-form-item label="执行遇错策略"><el-select v-model="config.errorPolicy"><el-option value="STOP" label="遇错停止 · 默认" /><el-option value="CONTINUE_INDEPENDENT" label="继续独立对象 · 部分完成单独验收" /></el-select><p class="muted">仅确认的 SQL 数据、约束或语法错误可继续，失败依赖会阻断。超时、断连、回滚失败及提交/DDL 结果不确定仍停止；不忽略坏行。</p></el-form-item>
                  </div></section>
                </div>
                <p class="mapping-safety muted">设置属于同一份草稿；切换页签不清空配置，也不触发数据库查询。修改参数后需要重新预检查。</p>
              </el-tab-pane>
            </el-tabs>
          </el-form>
        </template>

        <template v-if="step === 3">
          <div class="section-heading"><h2>预检查</h2><el-button type="primary" size="small" :disabled="!editable" :loading="busy" @click="runPrecheck">{{ assessment ? '重新检查' : '执行预检查' }}</el-button></div>
          <p class="muted">此阶段只读取元数据，不执行试写。修改配置后结果失效；开始前重新检查，超过 10 分钟必须重查。</p>
          <el-alert v-if="task && task.message" :title="task.message" :type="ready ? 'success' : 'warning'" :closable="false" />
          <template v-if="assessment">
            <div class="summary-strip"><span>{{ assessment.direction }}</span><span>源：{{ assessment.sourceVersion }}</span><span>目标：{{ assessment.targetVersion }}</span><span>{{ time(assessment.checkedAt) }}</span></div>
            <ul class="warning-list"><li v-for="warning in assessment.warnings" :key="warning">{{ warning }}</li></ul>
            <el-table :data="pagedAssessment" size="small"><el-table-column label="源对象 → 目标对象" min-width="230"><template #default="s">{{ s.row.sourceSchema || config.sourceSchema }}.{{ s.row.source }} → {{ s.row.targetSchema || config.targetSchema }}.{{ s.row.target }}<p class="muted">{{ s.row.type || 'TABLE' }}</p></template></el-table-column><el-table-column label="检查结果" min-width="300"><template #default="s"><template v-if="s.row.blockers.length"><p v-for="blocker in s.row.blockers" :key="blocker" class="danger">{{ blocker }}</p></template><span v-else>{{ s.row.skipped ? '跳过：目标已存在，不修改' : '通过：执行前仍会重新检查' }}</span></template></el-table-column><el-table-column label="详情" width="130"><template #default="s"><el-button type="text" @click="preview = s.row">DDL / 类型映射</el-button></template></el-table-column></el-table>
            <el-pagination v-if="assessment.tables.length > 50" class="mapping-pagination" v-model:current-page="assessmentPage" :page-size="50" :total="assessment.tables.length" layout="total, prev, pager, next" />
          </template>
          <el-empty v-else description="尚无有效预检查结果" :image-size="70" />
        </template>

        <template v-if="step === 4">
          <h2>审阅并执行</h2>
          <dl class="review"><dt>任务</dt><dd>{{ config.name }}</dd><dt>连接</dt><dd>{{ task.sourceName }} → {{ task.targetName }}</dd><dt>默认模式</dt><dd>{{ config.sourceSchema }} → {{ config.targetSchema }}（各对象以映射清单为准）</dd><dt>范围</dt><dd>{{ scopeLabel(config.scope) }} · {{ config.tables.length }} 个对象 · {{ conflictLabel(config.conflict) }}</dd></dl>
          <p>遇错策略：{{ config.errorPolicy === 'CONTINUE_INDEPENDENT' ? '继续独立对象；失败依赖阻断，部分完成单独验收' : '遇错停止' }}</p>
          <p>表级并发：{{ config.parallelism }} · 默认目标表空间：{{ config.targetTablespace || '目标用户默认' }}</p>
          <p v-if="task.parentTaskId" class="muted">关联原任务 {{ task.parentTaskId }}。这是新目标的完整重新迁移，不会自动清理原目标。</p>
          <el-alert title="目标会产生真实 DDL / 数据写入。取消或失败不会撤销已提交数据、已创建表和已执行 DDL；结果不确定时必须人工核对，不能直接重跑。" type="warning" :closable="false" />
          <el-table :data="config.tables" size="small"><el-table-column label="目标对象" min-width="200"><template #default="s">{{ s.row.targetSchema || config.targetSchema }}.{{ s.row.target }}</template></el-table-column><el-table-column label="实际执行策略" min-width="190"><template #default="s">{{ scopeLabel(effectiveScope(s.row)) }} · {{ conflictLabel(effectiveConflict(s.row), effectiveScope(s.row)) }}</template></el-table-column><el-table-column label="数据范围" min-width="140"><template #default="s">{{ (s.row.filters || []).length ? s.row.filters.length + ' 个 AND 条件（预览查看）' : '未配置筛选' }}</template></el-table-column><el-table-column width="80"><template #default="s"><el-button type="text" @click="previewMapping(s.row)">预览</el-button></template></el-table-column></el-table>
          <template v-if="highRisk"><el-alert title="以下目标表将被追加、清空或删除重建；DDL 不承诺事务回滚。单表覆盖策略也须独立确认。" type="error" :closable="false" /><ul><li v-for="table in riskyTables" :key="objectKey(table.targetSchema || config.targetSchema, table.target)">{{ table.targetSchema || config.targetSchema }}.{{ table.target }} · {{ conflictLabel(effectiveConflict(table), effectiveScope(table)) }}</li></ul></template>
        </template>

        <template v-if="step === 5 && task">
          <div class="section-heading"><h2>执行与验收 <el-tag size="small" :type="statusType(task.status)">{{ statusLabel(task.status) }}</el-tag></h2><div><el-button v-if="running" size="small" type="danger" plain @click="cancelTask">取消任务</el-button><el-button v-if="resumable" size="small" :disabled="busy" @click="checkResume">恢复检查</el-button><el-button v-if="resumable && task.resumeFingerprint && !task.resumeObject" size="small" type="primary" :disabled="busy" @click="resumeTask">确认并恢复</el-button><el-button size="small" @click="refreshTask">刷新状态</el-button><el-button size="small" @click="downloadReport">下载报告</el-button></div></div>
          <el-alert :title="task.message || '后台执行中'" :type="statusType(task.status) || 'info'" :closable="false" />
          <div class="metrics"><div><span>已确认提交</span><strong>{{ task.committedRows }} <small>行</small></strong></div><div><span>LOB 已读取</span><strong>{{ bytes(task.streamedBytes) }}</strong><small>CLOB 按 UTF-16 字节计，不代表网络吞吐</small></div><div><span>当前阶段</span><strong>{{ stageLabel(task.stage) }}</strong></div><div><span>开始 / 结束时间</span><p>{{ time(task.startedAt) }}<br>{{ time(task.finishedAt) }}</p></div></div>
          <p class="muted">不使用估算百分比；完整内容摘要保留重复行且忽略行顺序。恢复只适用于有稳定主键、源端未变且归属已确认的表任务；已有行须逐行一致，不覆盖、不删除。执行次数：{{ task.attempts || 1 }}。</p>
          <p class="muted">稳定主键表可检查并续迁。无主键、无旧基线、仅结构或程序对象可“重新迁移到新目标”：生成关联草稿，包含必要依赖，重新审阅 / 预检查；原目标不删除。追加失败不会盲目再次追加。</p>
          <div v-if="resumable" class="toolbar"><el-select v-model="retrySelection" size="small" clearable placeholder="所有未完成对象"><el-option v-for="row in task.objects.filter(item => !['COMPLETED', 'SKIPPED'].includes(item.stage))" :key="row.source" :value="row.source" :label="row.source" /></el-select><el-button size="small" :disabled="busy" @click="retryToNewTarget(task.objects.find(row => row.source === retrySelection))">重新迁移到新目标</el-button></div>
          <el-table :data="pagedResults" size="small"><el-table-column label="源对象 → 目标对象" min-width="180"><template #default="s">{{ s.row.source }} → {{ s.row.target }}</template></el-table-column><el-table-column label="阶段" width="130"><template #default="s">{{ stageLabel(s.row.stage) }}</template></el-table-column><el-table-column label="已提交" prop="committedRows" width="90" /><el-table-column label="恢复复用行" width="100"><template #default="s">{{ s.row.reusedRows || 0 }}</template></el-table-column><el-table-column label="源 / 目标校验行数（追加含原目标）" width="160"><template #default="s">{{ s.row.sourceRows }} / {{ s.row.targetRows }}</template></el-table-column><el-table-column label="校验结果" min-width="180"><template #default="s">{{ verificationLabel(s.row.verification) }}<p v-if="s.row.message" class="danger">{{ s.row.message }}</p></template></el-table-column><el-table-column label="恢复操作" width="135" fixed="right"><template #default="s"><el-button v-if="retryable(s.row)" type="text" :disabled="busy" @click="retryObject(s.row)">检查并重跑该表</el-button><span v-else class="muted">{{ s.row.stage === 'COMPLETED' ? '无需重跑' : '人工核对' }}</span></template></el-table-column></el-table>
          <el-pagination v-if="task.objects.length > 50" class="mapping-pagination" v-model:current-page="resultPage" :page-size="50" :total="task.objects.length" layout="total, prev, pager, next" />
          <h3>任务记录</h3><div class="log-panel"><p v-for="(log, index) in task.logs" :key="index">{{ log }}</p><p v-if="!task.logs.length" class="muted">任务结束时记录结果摘要；未保存业务字段值或口令。</p></div>
        </template>

        </div>
      </section>
      <footer v-if="step < 5" class="panel wizard-footer" :class="{ 'review-footer': step === 4 }">
        <div v-if="step === 4" class="execution-confirmations">
          <el-checkbox v-model="offlineConfirmed">我已冻结源端数据和结构，停止目标端业务写入，并核对迁移范围及类型映射</el-checkbox>
          <el-checkbox v-if="highRisk" v-model="targetChangeConfirmed">我已逐表核对高风险目标改动，完成数据、索引、约束、触发器及授权备份</el-checkbox>
        </div>
        <div class="footer-row">
          <el-button size="small" :disabled="step === 0 || busy || selectingAll" @click="step--">上一步</el-button>
          <span v-if="step < 4">配置草稿保存在服务端，离开页面不会取消已启动任务</span>
          <span v-else class="execution-hint">{{ !ready ? '预检查已失效，请返回上一步重新检查' : !offlineConfirmed ? '请先勾选执行确认' : highRisk && !targetChangeConfirmed ? '请先确认高风险目标改动' : '将写入目标数据库，请确认后启动' }}</span>
          <div class="footer-actions">
            <el-button v-if="step < 4" size="small" :disabled="!editable || busy || selectingAll" @click="saveDraft">保存草稿</el-button>
            <el-button v-if="step < 4" size="small" type="primary" :disabled="busy || selectingAll || (step === 3 && !ready)" @click="nextStep">下一步</el-button>
            <el-button v-if="step === 4" class="start-migration-button" type="primary" size="default" :loading="busy" :disabled="!ready || !offlineConfirmed || (highRisk && !targetChangeConfirmed)" @click="startTask">开始后台迁移</el-button>
          </div>
        </div>
      </footer>
    </template>

    <el-dialog title="对象配置 · 策略 / 字段 / 筛选 / 定义" custom-class="migration-config-dialog" :model-value="!!mappingEditor" width="85%" @close="mappingEditor = null" append-to-body>
      <template v-if="mappingEditor">
        <p>{{ mappingEditor.sourceSchema || config.sourceSchema }}.{{ mappingEditor.source }} → {{ mappingEditor.targetSchema || config.targetSchema }}.{{ mappingEditor.target }}</p>
        <el-form size="small" label-position="top" class="mapping-settings"><el-form-item label="单对象迁移范围"><el-select v-model="mappingEditor.scope" clearable placeholder="继承任务默认" @change="mappingEditor.conflict = ''"><el-option v-if="(mappingEditor.type || 'TABLE') === 'TABLE'" value="ALL" label="结构 + 数据" /><el-option value="STRUCTURE" label="仅结构" /><el-option v-if="(mappingEditor.type || 'TABLE') === 'TABLE'" value="DATA" label="仅数据" /></el-select></el-form-item><el-form-item label="单对象目标策略"><el-select v-model="mappingEditor.conflict" clearable placeholder="继承任务默认"><el-option value="STOP" :label="effectiveScope(mappingEditor) === 'DATA' ? '空目标写入' : '同名阻断'" /><el-option v-if="effectiveScope(mappingEditor) !== 'DATA'" value="SKIP" label="同名跳过" /><el-option v-if="(mappingEditor.type || 'TABLE') === 'TABLE' && effectiveScope(mappingEditor) === 'DATA'" value="APPEND" label="追加 · 高风险" /><el-option v-if="(mappingEditor.type || 'TABLE') === 'TABLE' && effectiveScope(mappingEditor) === 'DATA'" value="CLEAR" label="清空后写入 · 高风险" /><el-option v-if="(mappingEditor.type || 'TABLE') === 'TABLE' && effectiveScope(mappingEditor) !== 'DATA'" value="REBUILD" label="删除重建 · 高风险" /></el-select></el-form-item><p>留空继承任务默认。不同范围的策略不兼容时请显式选择，预检查会阻断无效组合。</p></el-form>
        <el-alert title="字段默认保留类型；可显式指定 CAST 转换并确认语义，用户定义对象可投影一个标量属性，不会自动序列化或静默截断。非表定义必须使用 CREATE 类型 &quot;目标模式&quot;.&quot;目标对象&quot;，禁止 OR REPLACE；包规范 / 包体用独立行 / 分隔。" type="info" :closable="false" />
        <template v-if="(mappingEditor.type || 'TABLE') === 'TABLE'">
          <el-form size="small" label-position="top"><el-form-item label="该表目标表空间"><el-select v-model="mappingEditor.targetTablespace" clearable filterable placeholder="继承任务默认"><el-option v-for="space in tablespaces" :key="space.name" :value="space.name" :label="space.name" /></el-select></el-form-item></el-form>
          <h3>显式类型转换 · 留空自动映射</h3>
          <p class="muted">支持 NUMBER(p,s)、VARCHAR(n)、NVARCHAR(n)、RAW(n)、TIMESTAMP(0～6)。达梦的 TEXT 与 NCLOB 均显示为 TEXT，默认迁为 CLOB；需要 NCLOB 时请在对应字段选择并确认。校验转换后的完整内容；精度变化 / 属性投影须主动确认。转换字段不可同时筛选；转换主键使用新目标重新迁移。</p>
          <el-table :data="mappingFields" size="small"><el-table-column label="源字段" prop="source" min-width="120" /><el-table-column label="目标类型" min-width="190"><template #default="s"><el-select v-if="s.row.sourceType === 'TEXT'" v-model="s.row.targetType" size="small" clearable placeholder="默认 CLOB" style="width: 100%" @change="s.row.conversionConfirmed = false"><el-option value="CLOB" label="CLOB" /><el-option value="NCLOB" label="NCLOB" /></el-select><el-input v-else v-model="s.row.targetType" size="small" placeholder="例如 NUMBER(20,4)" @input="s.row.conversionConfirmed = false" /></template></el-table-column><el-table-column label="对象标量属性（可选）" min-width="180"><template #default="s"><el-input v-model="s.row.sourceAttribute" size="small" placeholder="例如 ADDRESS.ZIP" @input="s.row.conversionConfirmed = false" /></template></el-table-column><el-table-column label="业务语义确认" width="150"><template #default="s"><el-checkbox v-model="s.row.conversionConfirmed" :disabled="!s.row.targetType">已审阅转换</el-checkbox></template></el-table-column></el-table>
        </template>
        <div v-if="(mappingEditor.type || 'TABLE') !== 'TABLE'" class="ddl-grid"><div><h3>源定义 · 只读</h3><el-input type="textarea" :rows="16" readonly :model-value="mappingSourceDdl" /></div><div><h3>转换后的目标定义</h3><el-input v-model="mappingEditor.manualDdl" type="textarea" :rows="16" :maxlength="98304" @input="mappingEditor.ddlConfirmed = false" /><el-checkbox v-model="mappingEditor.ddlConfirmed">我已审阅目标语法、序列起点、引用对象及触发器影响</el-checkbox></div></div>
        <template v-else><h3>字段映射</h3><el-table :data="mappingFields" size="small" max-height="280"><el-table-column label="源字段" prop="source" /><el-table-column label="源类型" prop="sourceType" /><el-table-column label="目标字段"><template #default="s"><el-input v-model="s.row.target" size="small" maxlength="128" /></template></el-table-column></el-table><template><h3>源数据筛选 · AND 条件</h3><p>不输入 SQL；空列表读取全表。仅结构范围请移除已有筛选。最多 20 个条件，筛选同步用于内容校验及恢复。外键所需父数据必须保留。筛选值会随配置和报告保存，请妥善保管。</p><el-table :data="mappingEditor.filters" size="small"><el-table-column label="源字段" min-width="170"><template #default="s"><el-select v-model="s.row.column" size="small" filterable @change="s.row.operator = 'EQ'; s.row.value = ''"><el-option v-for="field in mappingFields" :key="field.source" :value="field.source" :label="field.source + ' · ' + field.sourceType" /></el-select></template></el-table-column><el-table-column label="条件" min-width="140"><template #default="s"><el-select v-model="s.row.operator" size="small"><el-option v-for="op in filterOperators(s.row.column)" :key="op.value" :value="op.value" :label="op.label" /></el-select></template></el-table-column><el-table-column label="值（时间：YYYY-MM-DD HH:mm:ss，最多 6 位小数）" min-width="260"><template #default="s"><el-input v-model="s.row.value" size="small" :disabled="['IS_NULL', 'IS_NOT_NULL'].includes(s.row.operator)" maxlength="4000" placeholder="参数绑定，不执行 SQL" /></template></el-table-column><el-table-column width="65"><template #default="s"><el-button type="text" @click="mappingEditor.filters.splice(s.$index, 1)">移除</el-button></template></el-table-column></el-table><el-button size="small" :disabled="effectiveScope(mappingEditor) === 'STRUCTURE' || mappingEditor.filters.length >= 20" @click="addFilter">新增条件</el-button></template></template>
        <el-form size="small" label-position="top"><el-form-item label="补充业务依赖（目录无法识别的动态 SQL 依赖；必须同时选中这些源对象）"><el-select v-model="mappingEditor.dependencies" multiple filterable style="width: 100%"><el-option v-for="item in dependencyOptions" :key="item.key" :value="item.key" :label="item.label" /></el-select></el-form-item></el-form>
      </template>
      <template #footer><span ><el-button size="small" @click="mappingEditor = null">取消</el-button><el-button size="small" type="primary" @click="saveMapping">保存映射</el-button></span></template>
    </el-dialog>
    <el-dialog title="DDL 与字段映射 · 只读预览" custom-class="migration-config-dialog" :model-value="!!preview" width="85%" @close="preview = null" append-to-body>
      <template v-if="preview"><div class="ddl-grid"><div><h3>源对象 DDL</h3><el-input type="textarea" :rows="12" readonly :model-value="preview.sourceDdl || '未读取'" /></div><div><h3>{{ effectiveScope(preview) === 'DATA' ? '既有目标表 DDL（不执行）' : '计划创建的目标 DDL' }}</h3><el-input type="textarea" :rows="12" readonly :model-value="previewDdl(preview)" /></div></div><template v-if="preview.previousTargetDdl"><h3>删除重建前的目标 DDL · 只读（不包含数据或授权备份）</h3><el-input type="textarea" :rows="6" readonly :model-value="preview.previousTargetDdl" /></template><el-table :data="preview.columns" size="small"><el-table-column label="源字段" prop="name" /><el-table-column label="目标字段"><template #default="s">{{ s.row.targetName || s.row.name }}</template></el-table-column><el-table-column label="源类型" prop="sourceType" /><el-table-column label="目标类型" prop="targetType" /><el-table-column label="允许空值"><template #default="s">{{ s.row.nullable ? '是' : '否' }}</template></el-table-column></el-table></template>
      <template v-if="preview && preview.filters && preview.filters.length"><h3>源数据筛选 · AND · 只读</h3><el-table :data="preview.filters" size="small"><el-table-column label="源字段" prop="column" /><el-table-column label="操作符" prop="operator" /><el-table-column label="绑定值（不拼接 SQL）" prop="value" /></el-table></template>
    </el-dialog>
  </main>
</template>

<script>
import migration from '@/api/main/migration'
import connectionServer from '@/api/main/connection'
import { saveAs } from 'file-saver'

const activeStatuses = ['MIG_RUNNING', 'MIG_CANCEL_REQUESTED']
const maxObjects = 10000
const editableStatuses = ['MIG_DRAFT', 'MIG_READY', 'MIG_BLOCKED']
const blankConfig = () => ({ name: '', sourceId: null, targetId: null, sourceSchema: '', targetSchema: '', scope: 'ALL', conflict: 'STOP', fetchSize: 100, commitSize: 500, rowsPerSecond: 0, parallelism: 1, targetTablespace: '', errorPolicy: 'STOP', tables: [] })
const configSignature = config => JSON.stringify(Object.assign(blankConfig(), config), (_, value) => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return value
  return Object.keys(value).sort().reduce((sorted, key) => { sorted[key] = value[key]; return sorted }, {})
})

export default {
  name: 'DataMigration',
  data() {
    return { steps: ['连接配置', '选择对象', '映射策略', '预检查', '审阅执行', '结果验收'],
      editing: false, step: 0, mappingTab: 'objects', config: blankConfig(), task: null, checkedConfig: '',
      retrySelection: '',
      sources: [], endpoints: { source: null, target: null }, connecting: { source: false, target: false },
      activeStatuses, tasks: [], taskSearch: '', taskPage: 1, taskTotal: 0, loading: false, busy: false,
      mappingPage: 1, assessmentPage: 1, resultPage: 1,
      objects: [], objectSearch: '', objectPage: 1, objectTotal: 0, objectLoading: false, selectingAll: false, selectAllRequest: 0,
      offlineConfirmed: false, targetChangeConfirmed: false, preview: null, mappingEditor: null, mappingSourceDdl: '', mappingFields: [], mappingIndex: -1, pollTimer: null, generation: 0, visible: true,
      listRequest: 0, taskRequest: 0, objectRequest: 0, connectionRequest: { source: 0, target: 0 } }
  },
  computed: {
    tablespaces() { return this.endpoints.target && this.endpoints.target.tablespaces || [] },
    editable() { return !this.task || editableStatuses.includes(this.task.status) },
    assessment() { return this.task && this.task.assessment },
    ready() { return !!(this.task && this.task.status === 'MIG_READY' && this.assessment && this.assessment.ready && this.checkedConfig === configSignature(this.config)) },
    running() { return !!(this.task && activeStatuses.includes(this.task.status)) },
    riskyTables() { return this.config.tables.filter(table => ['APPEND', 'CLEAR', 'REBUILD'].includes(this.effectiveConflict(table))) },
    highRisk() { return this.riskyTables.length > 0 },
    resumable() { return !!(this.task && ['MIG_FAILED', 'MIG_CANCELLED', 'MIG_INTERRUPTED', 'MIG_NEEDS_REVIEW', 'MIG_PARTIAL'].includes(this.task.status)) },
    pagedMappings() { return this.config.tables.slice((this.mappingPage - 1) * 50, this.mappingPage * 50) },
    pagedAssessment() { return this.assessment ? this.assessment.tables.slice((this.assessmentPage - 1) * 50, this.assessmentPage * 50) : [] },
    pagedResults() { return this.task ? this.task.objects.slice((this.resultPage - 1) * 50, this.resultPage * 50) : [] },
    dependencyOptions() { return this.config.tables.filter(item => !this.mappingEditor || item !== this.config.tables[this.mappingIndex]).map(item => ({ key: this.objectKey(item.sourceSchema || this.config.sourceSchema, item.source), label: (item.sourceSchema || this.config.sourceSchema) + '.' + item.source })) }
  },
  watch: {
    config: { deep: true, handler() {
      if (this.checkedConfig && this.checkedConfig !== configSignature(this.config)) {
        if (this.task) this.task.assessment = null
        this.checkedConfig = ''
        this.offlineConfirmed = false
        this.targetChangeConfirmed = false
      }
    } }
  },
  async created() {
    try {
      const response = await connectionServer.getList({ pageNo: 1, pageSize: 1000 })
      if (!response.success) throw new Error(response.errorMessage || '数据源列表读取失败')
      const rows = Array.isArray(response.data) ? response.data : (response.data && response.data.data) || []
      this.sources = rows.filter(row => ['DM', 'ORACLE'].includes(row.type)).map(row => {
        const alias = String(row.alias || '').trim()
        return { id: row.id, alias: alias && !alias.startsWith('@') ? alias : row.user || row.userName || alias || `数据源 ${row.id}`, type: row.type }
      })
      const id = this.$route.query.taskId
      if (id) await this.openTask(id)
      else await this.loadList()
    } catch (error) { this.showError(error) }
  },
  beforeUnmount() { this.visible = false; this.selectingAll = false; this.stopPolling(); this.generation++ },
  deactivated() { this.visible = false; this.selectingAll = false; this.stopPolling(); this.generation++ },
  activated() { this.visible = true; this.schedulePolling() },
  methods: {
    showError(error) { if (error !== 'cancel' && error !== 'close') this.$message.error(error.message || '迁移操作失败') },
    stopPolling() { clearTimeout(this.pollTimer); this.pollTimer = null },
    schedulePolling() {
      this.stopPolling()
      if (!this.visible || !(this.editing ? this.running : this.tasks.some(task => activeStatuses.includes(task.status)))) return
      this.pollTimer = setTimeout(() => { if (this.editing) this.refreshTask(); else this.loadList() }, 2000)
    },
    async loadList(reset = false) {
      if (reset) this.taskPage = 1
      const generation = this.generation, request = ++this.listRequest
      if (!this.tasks.length) this.loading = true
      try {
        const result = await migration.list({ page: this.taskPage, size: 20, search: this.taskSearch })
        if (generation !== this.generation || request !== this.listRequest || !this.visible) return
        this.tasks = result.data; this.taskTotal = result.total; this.schedulePolling()
      } catch (error) { this.showError(error) } finally { this.loading = false }
    },
    newTask() {
      this.stopPolling(); this.generation++; this.editing = true; this.step = 0; this.task = null
      this.mappingTab = 'objects'; this.mappingPage = this.assessmentPage = this.resultPage = 1; this.selectingAll = false
      this.config = blankConfig(); this.checkedConfig = ''; this.retrySelection = ''; this.endpoints = { source: null, target: null }
      this.objects = []; this.objectTotal = 0; this.objectSearch = ''; this.offlineConfirmed = false; this.targetChangeConfirmed = false; this.preview = this.mappingEditor = null
      this.$router.replace({ path: this.$route.path, query: {} })
    },
    async backToList() {
      if (this.editable && this.config.name && (!this.task || configSignature(this.config) !== configSignature(this.task.config))) {
        try { await this.$confirm('当前配置尚未保存，返回后丢弃这些修改？', '未保存配置', { type: 'warning' }) } catch (_) { return }
      }
      this.stopPolling(); this.generation++; this.editing = false; this.selectingAll = false
      this.$router.replace({ path: this.$route.path, query: {} }); await this.loadList()
    },
    applyTask(task, loadConfig = false) {
      this.task = task
      if (loadConfig) { this.config = Object.assign(blankConfig(), JSON.parse(JSON.stringify(task.config))); this.retrySelection = '' }
      this.checkedConfig = task.assessment ? configSignature(task.config) : ''
    },
    async openTask(id) {
      this.stopPolling(); const generation = ++this.generation; this.busy = true; this.selectingAll = false; this.mappingTab = 'objects'; this.mappingPage = this.assessmentPage = this.resultPage = 1
      try {
        const task = await migration.get(id)
        if (generation !== this.generation) return
        this.applyTask(task, true); this.editing = true; this.offlineConfirmed = false; this.targetChangeConfirmed = false; this.preview = this.mappingEditor = null
        this.step = editableStatuses.includes(task.status) ? (task.assessment ? 3 : 0) : 5
        this.endpoints = { source: null, target: null }; this.objects = []; this.objectSearch = ''; this.objectTotal = 0
        this.$router.replace({ path: this.$route.path, query: { taskId: String(id) } }); this.schedulePolling()
      } catch (error) { this.showError(error) } finally { this.busy = false }
    },
    async copyTask(id) {
      try { const task = await migration.get(id); this.newTask(); this.config = Object.assign(blankConfig(), JSON.parse(JSON.stringify(task.config))); this.config.name = (this.config.name + ' - 副本').slice(0, 80) } catch (error) { this.showError(error) }
    },
    async deleteTask(row) {
      if (activeStatuses.includes(row.status) || this.busy) return
      try {
        await this.$confirm(`确认删除迁移任务“${row.config.name}”？仅从任务列表移除记录，不会删除源端或目标端数据。`, '删除迁移任务', { type: 'warning', confirmButtonText: '删除任务', cancelButtonText: '取消' })
        this.busy = true
        await migration.delete(row.id)
        if (this.taskPage > 1 && this.tasks.length === 1) this.taskPage--
        await this.loadList()
        this.$message.success('迁移任务已删除')
      } catch (error) { this.showError(error) } finally { this.busy = false }
    },
    endpointChanged(side) {
      this.connectionRequest[side]++; this.connecting[side] = false; this.endpoints[side] = null; this.config[side + 'Schema'] = ''
      if (side === 'source') { this.objectRequest++; this.objectLoading = false; this.config.tables = []; this.mappingPage = 1; this.objects = []; this.objectTotal = 0 }
      else this.config.tables.forEach(table => { table.targetSchema = ''; table.ddlConfirmed = false })
    },
    async connectEndpoint(side) {
      const id = this.config[side + 'Id'], generation = this.generation, request = ++this.connectionRequest[side]; this.connecting[side] = true
      try { const info = await migration.connection(id); if (generation === this.generation && request === this.connectionRequest[side] && id === this.config[side + 'Id']) this.endpoints[side] = Object.assign({}, info, { name: this.sourceName(id, info.name) }) }
      catch (error) { this.showError(error) } finally { if (request === this.connectionRequest[side]) this.connecting[side] = false }
    },
    schemas(side) { return this.endpoints[side] ? this.endpoints[side].schemas : [] },
    sourceName(id, fallback) { return (this.sources.find(source => String(source.id) === String(id)) || {}).alias || fallback || '未选择' },
    targetSchemaChanged() { this.config.tables.filter(table => !table.targetSchema).forEach(table => { table.ddlConfirmed = false }) },
    sourceSchemaChanged() { this.objectRequest++; this.objectLoading = false; this.objects = []; this.objectTotal = 0; this.loadObjects(true) },
    async loadObjects(reset = false) {
      if (!this.config.sourceId || !this.config.sourceSchema) return
      if (reset) this.objectPage = 1
      const generation = this.generation, request = ++this.objectRequest, id = this.config.sourceId, schema = this.config.sourceSchema, search = this.objectSearch, page = this.objectPage
      this.objectLoading = true
      try {
        const result = await migration.objects({ id, schema, search, page, size: 50 })
        if (generation !== this.generation || request !== this.objectRequest || schema !== this.config.sourceSchema || page !== this.objectPage || search !== this.objectSearch) return
        this.objects = result.data; this.objectTotal = result.total
      } catch (error) { this.showError(error) } finally { if (request === this.objectRequest) this.objectLoading = false }
    },
    objectKey(schema, name) { return '"' + schema.replace(/"/g, '""') + '"."' + name.replace(/"/g, '""') + '"' },
    selectionKey(schema, name, type) { return JSON.stringify([schema, name, type]) },
    newMapping(row, schema = this.config.sourceSchema) { return { source: row.name, target: row.name, sourceSchema: schema, targetSchema: '', type: row.type, scope: '', conflict: '', filters: [], columns: [], dependencies: [], manualDdl: '', ddlConfirmed: false } },
    supportedObject(type) { return ['TABLE', 'VIEW', 'SEQUENCE', 'SYNONYM', 'PROCEDURE', 'FUNCTION', 'PACKAGE', 'TRIGGER'].includes(type) && (this.config.scope !== 'DATA' || type === 'TABLE') },
    selected(name, type = 'TABLE') { return this.config.tables.some(table => table.source === name && (table.sourceSchema || this.config.sourceSchema) === this.config.sourceSchema && (table.type || 'TABLE') === type) },
    toggleObject(row, selected) {
      if (!this.supportedObject(row.type) || !this.editable) return
      if (!selected) this.config.tables = this.config.tables.filter(table => !(table.source === row.name && (table.sourceSchema || this.config.sourceSchema) === this.config.sourceSchema && (table.type || 'TABLE') === row.type))
      else if (!this.selected(row.name, row.type)) {
        if (this.config.tables.length >= maxObjects) { this.$message.warning(`单个任务最多选择 ${maxObjects} 个对象`); return }
        this.config.tables.push(this.newMapping(row))
      }
    },
    selectVisible() {
      if (!this.editable || this.selectingAll) return
      const rows = this.objects.filter(row => this.supportedObject(row.type) && !this.selected(row.name, row.type))
      if (this.config.tables.length + rows.length > maxObjects) { this.$message.warning(`单个任务最多选择 ${maxObjects} 个对象，请缩小选择范围`); return }
      this.config.tables.push(...rows.map(row => this.newMapping(row)))
    },
    async selectAllObjects() {
      if (!this.editable || this.selectingAll || !this.config.sourceId || !this.config.sourceSchema) return
      const generation = this.generation, request = ++this.selectAllRequest, id = this.config.sourceId, schema = this.config.sourceSchema, search = this.objectSearch, scope = this.config.scope
      const current = () => this.visible && this.editable && generation === this.generation && request === this.selectAllRequest && id === this.config.sourceId && schema === this.config.sourceSchema && search === this.objectSearch && scope === this.config.scope
      this.selectingAll = true
      try {
        const additions = new Map(); let found = false
        const existing = new Set(this.config.tables.map(table => this.selectionKey(table.sourceSchema || schema, table.source, table.type || 'TABLE')))
        for (let page = 1, total = Infinity; (page - 1) * 200 < total; page++) {
          const result = await migration.objects({ id, schema, search, page, size: 200 })
          if (!current()) return
          total = result.total
          result.data.filter(row => this.supportedObject(row.type)).forEach(row => {
            found = true
            const key = this.selectionKey(schema, row.name, row.type)
            if (!existing.has(key)) additions.set(key, row)
          })
          if (this.config.tables.length + additions.size > maxObjects) { this.$message.warning(`全选结果超过单个任务 ${maxObjects} 个对象的上限，请缩小搜索范围；已有选择未改变`); return }
        }
        if (!found) { this.$message.warning('当前筛选结果没有可迁移对象'); return }
        this.config.tables.push(...Array.from(additions.values(), row => this.newMapping(row, schema)))
        this.$message.success(`全选完成，任务共选择 ${this.config.tables.length} 个对象`)
      } catch (error) { if (current()) this.showError(error) } finally { if (request === this.selectAllRequest) this.selectingAll = false }
    },
    removeMapping(row) {
      const index = this.config.tables.indexOf(row)
      if (index < 0) return
      this.config.tables.splice(index, 1)
      if ((this.mappingPage - 1) * 50 >= this.config.tables.length) this.mappingPage = Math.max(1, this.mappingPage - 1)
    },
    clearSelection() { this.config.tables = []; this.mappingPage = 1 },
    async editMapping(row) {
      if (!this.editable) return
      const generation = this.generation; this.busy = true
      try {
        const definition = await migration.definition({ id: this.config.sourceId, schema: row.sourceSchema || this.config.sourceSchema, name: row.source, type: row.type || 'TABLE' })
        if (generation !== this.generation || !this.config.tables.includes(row)) return
        this.mappingIndex = this.config.tables.indexOf(row); this.mappingSourceDdl = definition.ddl
        this.mappingEditor = Object.assign({ dependencies: [], columns: [], filters: [], scope: '', conflict: '', targetTablespace: '', manualDdl: '', ddlConfirmed: false }, JSON.parse(JSON.stringify(row)))
        this.mappingFields = definition.columns.map(column => Object.assign({ source: column.name, sourceType: column.sourceType, kind: column.kind, target: column.name, targetType: '', sourceAttribute: '', conversionConfirmed: false }, (row.columns || []).find(field => field.source === column.name)))
      } catch (error) { this.showError(error) } finally { this.busy = false }
    },
    saveMapping() {
      if (!this.mappingEditor || !this.editable) return
      if ((this.mappingEditor.type || 'TABLE') === 'TABLE') {
        if (this.effectiveScope(this.mappingEditor) === 'STRUCTURE' && this.mappingEditor.filters.length) { this.$message.warning('仅结构不能保留数据筛选，请移除条件'); return }
        if (this.mappingEditor.filters.some(filter => !this.mappingFields.some(field => field.source === filter.column) || !this.filterOperators(filter.column).some(op => op.value === filter.operator) || (!['IS_NULL', 'IS_NOT_NULL'].includes(filter.operator) && !filter.value))) { this.$message.warning('筛选条件字段、操作符或值不完整'); return }
        const names = this.mappingFields.map(field => field.target.trim())
        if (names.some(name => !name) || new Set(names).size !== names.length) { this.$message.warning('目标字段不能为空或重复'); return }
        if (this.mappingFields.some(field => field.targetType && !field.conversionConfirmed || field.sourceAttribute && !field.targetType)) { this.$message.warning('请填写转换类型并确认业务语义；属性投影也必须指定类型'); return }
        this.mappingEditor.columns = this.mappingFields.map(field => ({ source: field.source, target: field.target.trim(), targetType: field.targetType, sourceAttribute: field.sourceAttribute, conversionConfirmed: field.conversionConfirmed }))
      }
      this.config.tables.splice(this.mappingIndex, 1, this.mappingEditor); this.mappingEditor = null
    },
    effectiveScope(table) { return table.scope || this.config.scope },
    effectiveConflict(table) { return table.conflict || this.config.conflict },
    previewMapping(table) { this.preview = this.assessment.tables.find(plan => plan.source === table.source && (plan.sourceSchema || this.config.sourceSchema) === (table.sourceSchema || this.config.sourceSchema)) },
    addFilter() { this.mappingEditor.filters.push({ column: '', operator: 'EQ', value: '' }) },
    filterOperators(name) {
      const field = this.mappingFields.find(field => field.source === name)
      const operators = [{ value: 'IS_NULL', label: '为空' }, { value: 'IS_NOT_NULL', label: '不为空' }]
      if (field && ['NUMBER', 'TEXT', 'TIMESTAMP'].includes(field.kind)) operators.unshift(...[{ value: 'EQ', label: '等于' }, { value: 'NE', label: '不等于' }, { value: 'GT', label: '大于' }, { value: 'GE', label: '大于等于' }, { value: 'LT', label: '小于' }, { value: 'LE', label: '小于等于' }], ...(field.kind === 'TEXT' ? [{ value: 'LIKE', label: 'LIKE（% / _）' }] : []))
      return operators
    },
    async persistDraft() {
      const task = await migration.save(this.task && this.task.id, this.config); this.applyTask(task, true)
      this.$router.replace({ path: this.$route.path, query: { taskId: String(task.id) } }); return task
    },
    async saveDraft() { this.busy = true; try { await this.persistDraft(); this.$message.success('草稿已保存') } catch (error) { this.showError(error) } finally { this.busy = false } },
    async nextStep() {
      if (this.step === 0 && (!this.endpoints.source || !this.endpoints.target)) { this.$message.warning('请分别连接源端和目标端'); return }
      if (this.step === 1 && (!this.config.sourceSchema || !this.config.tables.length)) { this.$message.warning('请选择源模式与表'); return }
      if (this.step === 2 && (!this.config.targetSchema || this.config.tables.some(table => !table.target.trim()))) { this.$message.warning('请填写目标模式与表映射'); return }
      if (this.step === 3) { if (this.ready) this.step++; return }
      this.busy = true; try { await this.persistDraft(); this.step++ } catch (error) { this.showError(error) } finally { this.busy = false }
    },
    async runPrecheck() {
      this.busy = true
      try { const saved = await this.persistDraft(); const task = await migration.precheck(saved.id); this.applyTask(task, true); this.assessmentPage = 1; this.offlineConfirmed = false; this.targetChangeConfirmed = false }
      catch (error) { this.showError(error) } finally { this.busy = false }
    },
    async startTask() {
      if (!this.ready || !this.offlineConfirmed || (this.highRisk && !this.targetChangeConfirmed)) return
      this.busy = true
      try { this.applyTask(await migration.start(this.task.id, { fingerprint: this.assessment.fingerprint, offlineConfirmed: true, targetChangeConfirmed: this.targetChangeConfirmed })); this.resultPage = 1; this.step = 5; this.schedulePolling() }
      catch (error) { this.showError(error) } finally { this.busy = false }
    },
    async refreshTask() {
      if (!this.task) return
      const id = this.task.id, generation = this.generation, request = ++this.taskRequest
      try { const task = await migration.get(id); if (generation !== this.generation || request !== this.taskRequest || !this.visible || !this.editing || id !== this.task.id) return; this.applyTask(task); this.schedulePolling() }
      catch (error) { this.stopPolling(); this.showError(error) }
    },
    async cancelTask() {
      try { await this.$confirm('仅回滚未提交批次，已提交数据与 DDL 保留。确认取消？', '取消迁移', { type: 'warning' }); this.applyTask(await migration.cancel(this.task.id)); this.schedulePolling() }
      catch (error) { this.showError(error) }
    },
    async checkResume() {
      this.busy = true; try { this.applyTask(await migration.resumeCheck(this.task.id)) } catch (error) { this.showError(error) } finally { this.busy = false }
    },
    retryable(row) {
      const plan = this.assessment && this.assessment.tables.find(plan => this.objectKey(plan.sourceSchema || this.config.sourceSchema, plan.source) === row.source)
      return this.resumable && plan && plan.type === 'TABLE' && !plan.skipped && !['COMPLETED', 'SKIPPED'].includes(row.stage)
    },
    async retryObject(row) {
      const id = this.task.id, generation = this.generation, sourceObject = row.source
      this.busy = true
      try {
        const task = await migration.objectRetryCheck(id, { sourceObject })
        if (generation !== this.generation || !this.task || id !== this.task.id) return
        this.applyTask(task)
        if (!task.resumeFingerprint) return
        await this.$confirm(`只重跑 ${sourceObject}，不执行其他对象。请确认源端冻结、目标停止业务写入，并已核对该表归属及不确定提交。只补写缺失主键行，不覆盖或删除；成功对象保持不变。`, '单对象受限重跑', { type: 'warning', confirmButtonText: '确认并重跑该表' })
        if (generation !== this.generation || !this.task || id !== this.task.id) return
        const resumed = await migration.resume(id, { sourceObject, fingerprint: task.resumeFingerprint, offlineConfirmed: true, reviewConfirmed: true })
        if (generation === this.generation && this.task && id === this.task.id) { this.applyTask(resumed); this.schedulePolling() }
      } catch (error) { this.showError(error) } finally { this.busy = false }
    },
    async retryToNewTarget(row) {
      const id = this.task.id, generation = this.generation
      try {
        await this.$confirm('为未完成对象及必要依赖创建关联草稿，使用新的目标名称。原目标和成功记录保留；程序定义需重新编辑引用。不会立即写入目标，须完整审阅并预检查后执行。', '重新迁移到新目标', { type: 'warning', confirmButtonText: '创建关联草稿' })
        if (generation !== this.generation || !this.task || id !== this.task.id) return
        this.busy = true
        const draft = await migration.retryDraft(id, { sourceObject: row && row.source })
        if (generation !== this.generation || !this.task || id !== this.task.id) return
        this.stopPolling(); this.generation++; this.applyTask(draft, true); this.step = 0; this.endpoints = { source: null, target: null }
        this.$router.replace({ path: this.$route.path, query: { taskId: String(draft.id) } })
        this.$message.success('关联草稿已创建，请重新连接、核对新目标及程序定义')
      } catch (error) { this.showError(error) } finally { this.busy = false }
    },
    async resumeTask() {
      if (this.task.resumeObject) { this.$message.warning('当前为单对象检查，请使用该表的重跑操作，或重新执行全任务恢复检查'); return }
      try {
        await this.$confirm('确认源端数据与结构仍冻结、目标业务停止写入，并已复核不确定提交及目标对象归属？恢复仅补写缺失主键行，不覆盖或删除；已有内容不一致将阻断。', '受限恢复确认', { type: 'warning', confirmButtonText: '确认并恢复' })
        this.busy = true; this.applyTask(await migration.resume(this.task.id, { fingerprint: this.task.resumeFingerprint, offlineConfirmed: true, reviewConfirmed: true })); this.schedulePolling()
      } catch (error) { this.showError(error) } finally { this.busy = false }
    },
    async downloadReport() {
      try { const report = await migration.report(this.task.id); saveAs(new Blob([JSON.stringify(report, null, 2)], { type: 'application/json;charset=utf-8' }), `migration-${report.id}-report.json`) }
      catch (error) { this.showError(error) }
    },
    statusLabel(status) { return ({ MIG_DRAFT: '草稿', MIG_READY: '预检查通过', MIG_BLOCKED: '预检查阻断', MIG_RUNNING: '执行中', MIG_CANCEL_REQUESTED: '取消中', MIG_COMPLETED: '校验完成', MIG_PARTIAL: '部分完成', MIG_CANCELLED: '已取消', MIG_FAILED: '失败', MIG_NEEDS_REVIEW: '提交待核对', MIG_INTERRUPTED: '已中断' })[status] || status },
    statusType(status) { return status === 'MIG_COMPLETED' || status === 'MIG_READY' ? 'success' : ['MIG_FAILED', 'MIG_NEEDS_REVIEW', 'MIG_BLOCKED'].includes(status) ? 'error' : ['MIG_PARTIAL', 'MIG_CANCEL_REQUESTED', 'MIG_INTERRUPTED'].includes(status) ? 'warning' : 'info' },
    previewDdl(table) { return [table.targetDdl].concat(this.effectiveScope(table) === 'DATA' ? [] : (table.afterDdl || []).concat((table.postDdl || []).map(operation => operation.sql))).filter(Boolean).join(';\n\n') },
    conflictLabel(value, scope = this.config.scope) { return ({ STOP: scope === 'DATA' ? '空目标写入' : '同名阻断', SKIP: '同名跳过', APPEND: '追加既有数据', CLEAR: '清空后写入', REBUILD: '删除重建（不级联）' })[value] || value },
    stageLabel(stage) { return ({ CONFIGURATION: '配置', PRECHECK: '预检查', RECHECK: '执行前复核', RESUME_RECHECK: '恢复前复核', SOURCE_BASELINE: '记录源端基线', DEPENDENT_DDL: '索引 / 约束', PROGRAM_DDL: '依赖 / 程序', STRUCTURE: '创建结构', DATA: '迁移数据', THROTTLED: '限速等待（可取消）', VERIFICATION: '全量校验', FINISHED: '结束', STOPPED: '已停止', WAITING: '等待', SKIPPED: '已跳过', CREATING: '创建对象', COPYING: '读取 / 写入', COMMITTING: '正在提交', DDL_COMMITTING: 'DDL 执行待确认', DEPENDENCY_BLOCKED: '依赖阻断', VERIFYING: '全量校验', COMPLETED: '已完成', FAILED: '失败' })[stage] || this.statusLabel(stage) },
    verificationLabel(value) { return ({ NOT_CHECKED: '尚未校验', OBJECT_VALID_PASS: '目标对象编译 / 可读性通过（人工转换）', LOGICAL_STRUCTURE_PASS: '逻辑结构校验通过', FULL_CONTENT_HASH_PASS: '全量内容摘要与行数通过', APPENDED_CONTENT_HASH_PASS: '既有目标 + 源数据的合并摘要通过', SOURCE_CHANGED: '源端基线变化，未通过', CONTENT_MISMATCH: '内容不一致，未通过' })[value] || value },
    scopeLabel(value) { return ({ ALL: '表结构 + 数据', STRUCTURE: '仅表结构', DATA: '仅数据' })[value] },
    time(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—' },
    bytes(value) { return value >= 1048576 ? (value / 1048576).toFixed(2) + ' MiB' : value >= 1024 ? (value / 1024).toFixed(1) + ' KiB' : (value || 0) + ' B' }
  }
}
</script>

<style lang="scss" scoped>
.migration-page {
  --migration-primary: #006fff; --migration-border: #dfe6f0; --migration-muted: #68728c;
  box-sizing: border-box; display: flex; flex-direction: column; gap: 12px;
  padding: 16px; background: #f1f3f9; color: #303133; font-size: 12px; height: calc(100vh - 84px); overflow: hidden;
  h1, h2 { font-size: 16px; font-weight: 600; margin: 0 0 8px; } h3 { font-size: 12px; margin: 16px 0 12px; }
  p { line-height: 1.65; margin: 6px 0; text-wrap: pretty; } .muted, .heading-note { color: var(--migration-muted); font-size: 12px; font-weight: normal; }
  .page-heading, .section-heading { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-shrink: 0; }
  .section-heading { margin-bottom: 16px; } .page-heading h1 { margin-bottom: 4px; } .page-heading p { color: var(--migration-muted); margin: 0; }
  .panel { border: 1px solid var(--migration-border); border-radius: 4px; background: #fff; padding: 16px; flex-shrink: 0; }
  .editor-panel, .task-panel { flex: 1; min-height: 0; display: flex; flex-direction: column; overflow: hidden; }
  .editor-body { flex: 1; min-height: 0; overflow: auto; }
  .table-step, .mapping-step { display: flex; flex-direction: column; gap: 8px; overflow: hidden; }
  .table-step > h2, .mapping-step > h2, .table-step > p { flex-shrink: 0; margin: 0; }
  .table-region { flex: 1; min-height: 0; overflow: hidden; }
  .step-panel { padding: 12px 16px; } .toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; flex-shrink: 0; }
  .table-step .toolbar { margin-bottom: 0; }
  .toolbar > .el-input { width: 220px; } .toolbar > .el-select { width: 240px; } .pagination { text-align: right; margin-top: 16px; }
  .mapping-pagination { flex-shrink: 0; text-align: right; padding: 8px 0; }
  .delete-action:not(.is-disabled) { color: #d94242; }
  .object-pagination { display: flex; align-items: center; justify-content: space-between; gap: 8px; flex-shrink: 0; }
  .endpoint-grid, .ddl-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; } .endpoint { border: 1px solid var(--migration-border); border-radius: 4px; padding: 16px; } .endpoint h3 { margin-top: 0; }
  .name-input { max-width: 560px; } .endpoint .el-select { width: 100%; } .connection-summary { color: var(--migration-muted); overflow-wrap: anywhere; margin-top: 16px; }
  .mapping-form, .mapping-tabs { display: flex; flex-direction: column; flex: 1; min-height: 0; }
  .mapping-tabs ::v-deep .el-tabs__header { margin: 0 0 8px; flex-shrink: 0; }
  .mapping-tabs ::v-deep .el-tabs__content { flex: 1; min-height: 0; }
  .mapping-tabs ::v-deep .el-tab-pane { height: 100%; min-height: 0; display: flex; flex-direction: column; gap: 8px; }
  .mapping-tabs ::v-deep .el-tabs__item { height: 36px; line-height: 36px; font-size: 12px; }
  .mapping-tabs ::v-deep .el-tabs__item.is-active { color: var(--migration-primary); }
  .mapping-tabs ::v-deep .el-tabs__active-bar { background: var(--migration-primary); }
  .mapping-tabs ::v-deep .el-tabs__nav-wrap::after { height: 1px; background: var(--migration-border); }
  .mapping-tabs ::v-deep .el-table td { padding: 4px 0; }
  .mapping-tabs ::v-deep .el-table .cell { line-height: 20px; }
  .mapping-tabs ::v-deep .el-table p { margin: 0; line-height: 20px; }
  .mapping-tabs ::v-deep .el-table .el-button--text { padding: 0; }
  .default-rules { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; flex-shrink: 0; }
  .default-rules ::v-deep .el-form-item, .setting-grid ::v-deep .el-form-item { margin-bottom: 0; }
  .default-rules ::v-deep .el-form-item__label, .setting-grid ::v-deep .el-form-item__label { line-height: 20px; padding-bottom: 4px; }
  .default-rules .el-select, .setting-grid .el-select, .setting-grid .el-input-number { width: 100%; }
  .mapping-note, .mapping-safety { flex-shrink: 0; margin: 0; }
  .mapping-safety { border-top: 1px solid var(--migration-border); padding-top: 8px; }
  .settings-scroll { flex: 1; min-height: 0; overflow-y: auto; padding-right: 8px; }
  .setting-group { padding-bottom: 16px; margin-bottom: 16px; border-bottom: 1px solid var(--migration-border); }
  .setting-group:last-child { border: 0; padding-bottom: 0; margin-bottom: 0; }
  .setting-group h3 { margin: 0 0 12px; }
  .setting-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
  .summary-strip { display: flex; flex-wrap: wrap; gap: 16px; padding: 16px 0; } .warning-list { padding-left: 20px; line-height: 1.9; color: var(--migration-muted); }
  .danger { color: #d94242; } .review { display: grid; grid-template-columns: 90px 1fr; gap: 16px; margin: 24px 0; } .review dt { color: var(--migration-muted); } .review dd { margin: 0; overflow-wrap: anywhere; }
  .metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 24px; margin: 24px 0; }
  .metrics > div { padding-right: 16px; border-right: 1px solid var(--migration-border); } .metrics > div:last-child { border: 0; } .metrics span, .metrics small { color: var(--migration-muted); display: block; font-size: 12px; }
  .metrics strong { display: block; font-size: 16px; margin: 8px 0; font-weight: 600; } .metrics strong small { display: inline; font-weight: normal; }
  .log-panel { background: #f8fafc; padding: 12px; border: 1px solid var(--migration-border); overflow-wrap: anywhere; }
  .wizard-footer { padding: 12px 16px; }
  .footer-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
  .footer-row > span { color: var(--migration-muted); } .footer-actions { display: flex; align-items: center; gap: 8px; }
  .footer-actions ::v-deep .el-button + .el-button { margin-left: 0; }
  .execution-confirmations { display: flex; flex-wrap: wrap; gap: 8px 24px; padding-bottom: 10px; margin-bottom: 10px; border-bottom: 1px solid var(--migration-border); }
  .execution-confirmations ::v-deep .el-checkbox { margin-right: 0; max-width: 100%; }
  .start-migration-button { min-width: 174px; font-weight: 600; box-shadow: 0 2px 8px rgba(0, 111, 255, .25); }
  .review-footer ::v-deep .start-migration-button { font-size: 14px; }
  .review-footer ::v-deep .start-migration-button.is-disabled, .review-footer ::v-deep .start-migration-button.is-disabled:hover { color: #fff; background: #89b7f0; border-color: #89b7f0; box-shadow: none; }
  ::v-deep .el-step__title { font-size: 12px; line-height: 24px; } ::v-deep .el-form-item__label { font-size: 12px; padding-bottom: 4px; }
  ::v-deep .el-step__icon { width: 22px; height: 22px; } ::v-deep .el-step.is-horizontal .el-step__line { top: 10px; }
  ::v-deep .el-table, ::v-deep .el-input__inner, ::v-deep .el-button, ::v-deep .el-checkbox__label, ::v-deep .el-alert__title { font-size: 12px; }
  ::v-deep .el-button--primary { background: var(--migration-primary); border-color: var(--migration-primary); } ::v-deep .el-button--primary:hover { background: #2052dd; border-color: #2052dd; }
  ::v-deep .el-checkbox__label { white-space: normal; line-height: 1.7; } ::v-deep .el-table p { margin: 4px 0; }
  ::v-deep .el-tag--error { color: #d94242; background: #fef0f0; border-color: #fbc4c4; }
  ::v-deep .el-textarea__inner { font: 12px/1.65 Consolas, monospace; }
  @media (max-width: 980px) { padding: 12px; gap: 8px; .metrics { grid-template-columns: 1fr 1fr; } .footer-row > span, .object-pagination > span { display: none; } }
  @media (max-width: 680px) { .endpoint-grid, .ddl-grid, .default-rules, .setting-grid, .metrics { grid-template-columns: 1fr; } .default-rules { max-height: 150px; overflow-y: auto; } .section-heading, .page-heading { align-items: flex-start; flex-wrap: wrap; } .execution-confirmations { max-height: 96px; overflow-y: auto; } }
}
.ddl-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }
.mapping-settings { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; font-size: 12px; } .mapping-settings .el-select { width: 100%; } .mapping-settings p { grid-column: 1 / -1; margin-top: 0; }
.ddl-grid ::v-deep .el-textarea__inner { font: 12px/1.65 Consolas, monospace; }
@media (max-width: 680px) { .ddl-grid { grid-template-columns: 1fr; } }
</style>

<style lang="scss">
.migration-config-dialog {
  margin: 4vh auto !important;
  .el-dialog__body { max-height: calc(92vh - 120px); overflow-y: auto; padding: 16px; font-size: 12px; }
  .el-dialog__title { font-size: 16px; }
  .el-dialog__footer { padding: 12px 16px 16px; }
  h3 { font-size: 12px; margin: 16px 0 8px; } p { line-height: 1.65; margin: 6px 0; }
  .el-form-item__label, .el-input__inner, .el-checkbox__label, .el-table, .el-alert__title { font-size: 12px; }
  @media (max-width: 680px) { .mapping-settings { grid-template-columns: 1fr; } }
}
</style>
