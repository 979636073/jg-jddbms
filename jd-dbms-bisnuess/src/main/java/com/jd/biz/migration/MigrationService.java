package com.jd.biz.migration;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.repository.entity.TaskDO;
import com.jd.biz.domain.repository.mapper.TaskMapper;
import com.jd.common.tools.base.excption.BusinessException;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import javax.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import static com.jd.biz.migration.MigrationModel.*;

@Service
public class MigrationService {
    static final String TYPE = "DATA_MIGRATION";
    static final List<String> ACTIVE = Arrays.asList("MIG_RUNNING", "MIG_CANCEL_REQUESTED");
    static final List<String> RESUMABLE = Arrays.asList("MIG_FAILED", "MIG_CANCELLED", "MIG_INTERRUPTED", "MIG_NEEDS_REVIEW", "MIG_PARTIAL");
    private final TaskMapper mapper;
    private final MigrationConnections connections;
    private final MigrationMetadata metadata;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "data-migration"); thread.setDaemon(true); return thread;
    });
    private volatile MigrationTransfer.Control active;

    public MigrationService(TaskMapper mapper, MigrationConnections connections, MigrationMetadata metadata) {
        this.mapper = mapper; this.connections = connections; this.metadata = metadata;
    }

    private LambdaQueryWrapper<TaskDO> owned(Long user) {
        if (user == null) throw new BusinessException("请先登录");
        return new LambdaQueryWrapper<TaskDO>().eq(TaskDO::getTaskType, TYPE).eq(TaskDO::getUserId, user).eq(TaskDO::getDeleted, "N");
    }

    TaskDO row(Long id, Long user) {
        TaskDO row = mapper.selectOne(owned(user).eq(TaskDO::getId, id));
        if (row == null) throw new BusinessException("迁移任务不存在或无权访问");
        return row;
    }

    static Task snapshot(TaskDO row) {
        Task task = JSON.parseObject(row.getContent(), Task.class);
        task.id = row.getId(); task.status = row.getTaskStatus();
        if ("MIG_CANCEL_REQUESTED".equals(task.status)) task.message = "取消请求已受理，等待数据库响应及批次安全边界；已提交数据和 DDL 不会撤销";
        return task;
    }

    public Task get(Long id, Long user) { return snapshot(row(id, user)); }

    public Map<String, Object> page(Long user, int page, int size, String search) {
        if (page < 1 || size < 1 || size > 100) throw new BusinessException("任务分页范围无效");
        LambdaQueryWrapper<TaskDO> query = owned(user);
        if (search != null && !search.trim().isEmpty()) query.like(TaskDO::getTaskName, search.trim());
        Page<TaskDO> result = mapper.selectPage(new Page<>(page, size), query.orderByDesc(TaskDO::getId));
        List<Task> rows = new ArrayList<>();
        for (TaskDO item : result.getRecords()) {
            Task task = snapshot(item);
            // List responses do not carry large DDL previews or logs.
            task.assessment = null; task.objects.clear(); task.logs.clear(); rows.add(task);
        }
        Map<String, Object> output = new LinkedHashMap<>(); output.put("data", rows); output.put("total", result.getTotal()); return output;
    }

    public synchronized Task save(Long id, Long user, Config config) {
        MigrationDialect.validate(config, false);
        if (JSON.toJSONBytes(config).length > 128 * 1024) throw new BusinessException("配置超出大小限制");
        owned(user);
        TaskDO row = id == null ? new TaskDO() : row(id, user);
        if (id != null && !Arrays.asList("MIG_DRAFT", "MIG_READY", "MIG_BLOCKED").contains(row.getTaskStatus())) {
            throw new BusinessException("执行过的任务不可改写，请复制配置新建任务");
        }
        DataSource source = config.sourceId == null ? null : connections.authorized(config.sourceId);
        DataSource target = config.targetId == null ? null : connections.authorized(config.targetId);
        Task task = new Task(); task.id = id; task.config = config; task.status = "MIG_DRAFT";
        if (id != null) task.parentTaskId = snapshot(row).parentTaskId;
        task.sourceName = source == null ? null : source.getAlias(); task.targetName = target == null ? null : target.getAlias();
        task.direction = source == null || target == null ? null : source.getType() + " → " + target.getType();
        task.createdAt = id == null ? System.currentTimeMillis() : snapshot(row).createdAt;
        row.setTaskType(TYPE); row.setDeleted("N"); row.setUserId(user); row.setTaskName(config.name);
        row.setDataSourceId(config.sourceId); row.setGmtCreate(new Date(task.createdAt));
        fill(row, task);
        if (id == null) mapper.insert(row); else mapper.updateById(row);
        task.id = row.getId(); return task;
    }

    public synchronized Task retryDraft(Long id, Long user, String sourceObject) {
        Task previous = get(id, user);
        if (!RESUMABLE.contains(previous.status) || previous.assessment == null || previous.objects.size() != previous.assessment.tables.size()) throw new BusinessException("该任务没有完整的失败对象清单");
        Set<String> selected = new HashSet<>();
        for (ObjectResult result : previous.objects) if ((sourceObject == null || sourceObject.equals(result.source)) && !Arrays.asList("COMPLETED", "SKIPPED").contains(result.stage)) selected.add(result.source);
        if (selected.isEmpty()) throw new BusinessException("请选择未完成对象，成功对象无需重新迁移");
        boolean changed;
        do {
            changed = false;
            for (TablePlan plan : previous.assessment.tables) if (selected.contains(MigrationDialect.qualified(plan.sourceSchema, plan.source))) {
                for (String dependency : plan.dependencies) if (selected.add(dependency)) changed = true;
                for (DdlOperation operation : plan.postDdl) if (operation.foreignKey) for (TablePlan candidate : previous.assessment.tables)
                    if (candidate.targetSchema.equals(operation.referenceSchema) && candidate.target.equals(operation.referenceTable)
                            && selected.add(MigrationDialect.qualified(candidate.sourceSchema, candidate.source))) changed = true;
            }
        } while (changed);
        Config config = JSON.parseObject(JSON.toJSONBytes(previous.config), Config.class);
        config.name = previous.config.name.substring(0, Math.min(60, previous.config.name.length())) + " · 重新迁移";
        config.scope = "ALL"; config.conflict = "STOP";
        String suffix = "_R" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        config.tables.removeIf(mapping -> !selected.contains(MigrationDialect.qualified(MigrationDialect.sourceSchema(config, mapping), mapping.source)));
        for (Mapping mapping : config.tables) {
            String prefix = mapping.target;
            while (prefix.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 21) prefix = prefix.substring(0, prefix.offsetByCodePoints(prefix.length(), -1));
            mapping.target = prefix + suffix;
            mapping.scope = "TABLE".equals(mapping.type) ? "ALL" : "STRUCTURE"; mapping.conflict = "STOP";
            // Program references must be rewritten and reviewed by the user, never blindly string-replaced.
            if (!"TABLE".equals(mapping.type)) { mapping.manualDdl = null; mapping.ddlConfirmed = false; }
        }
        Task task = save(null, user, config); task.parentTaskId = id;
        task.message = "关联原任务 " + id + "，重新迁移所选失败对象及必要依赖到新目标；原目标保留。请复核范围、目标名称及程序引用，重新预检查后执行。";
        persist(task); return task;
    }

    public synchronized Task precheck(Long id, Long user) {
        TaskDO row = row(id, user); Task task = snapshot(row);
        if (!Arrays.asList("MIG_DRAFT", "MIG_READY", "MIG_BLOCKED").contains(task.status)) throw new BusinessException("该任务不能重新预检查");
        MigrationDialect.validate(task.config, true);
        DataSource source = connections.authorized(task.config.sourceId), target = connections.authorized(task.config.targetId);
        try (Connection read = connections.open(source); Connection write = connections.open(target)) {
            task.assessment = metadata.assess(read, write, task.config, source.getType(), target.getType());
            task.status = task.assessment.isReady() ? "MIG_READY" : "MIG_BLOCKED";
            task.stage = "PRECHECK"; task.message = task.assessment.isReady() ? "预检查通过，执行前仍会重新核对" : "存在阻断项，未写入目标";
        } catch (Exception e) {
            task.assessment = null; task.status = "MIG_BLOCKED"; task.message = MigrationConnections.safeError(e);
        }
        persist(task); return task;
    }

    static void validateStart(Task task, StartRequest request) {
        if (request == null || !request.offlineConfirmed) throw new BusinessException("请确认已冻结源端数据与结构，并停止目标对象的业务写入");
        if (!"MIG_READY".equals(task.status) || task.assessment == null || !task.assessment.isReady()
                || !Objects.equals(task.assessment.fingerprint, request.fingerprint)) throw new BusinessException("配置或预检查结果已变化，请重新预检查");
        if (System.currentTimeMillis() - task.assessment.checkedAt > 10 * 60 * 1000L) throw new BusinessException("预检查超过 10 分钟，请重新检查");
        if (MigrationDialect.highRisk(task.config) && !request.targetChangeConfirmed) throw new BusinessException("请逐表确认目标高风险改动和备份；未确认不能执行追加、清空或删除重建");
    }

    public synchronized Task start(Long id, Long user, StartRequest request) {
        Task task = get(id, user); validateStart(task, request);
        if (active != null) throw new BusinessException("已有迁移任务执行中；首版仅允许一个后台迁移任务");
        DataSource source = connections.authorized(task.config.sourceId), target = connections.authorized(task.config.targetId);
        task.status = "MIG_RUNNING"; task.stage = "RECHECK"; task.startedAt = System.currentTimeMillis();
        task.message = "后台执行已启动；离开页面不会停止任务";
        for (TablePlan table : task.assessment.tables) {
            ObjectResult result = new ObjectResult(); result.source = MigrationDialect.qualified(table.sourceSchema == null ? task.config.sourceSchema : table.sourceSchema, table.source);
            result.target = MigrationDialect.qualified(table.targetSchema == null ? task.config.targetSchema : table.targetSchema, table.target); task.objects.add(result);
        }
        TaskDO update = new TaskDO(); fill(update, task);
        int changed = mapper.update(update, new LambdaUpdateWrapper<TaskDO>().eq(TaskDO::getId, id)
                .eq(TaskDO::getUserId, user).eq(TaskDO::getTaskType, TYPE).eq(TaskDO::getTaskStatus, "MIG_READY"));
        if (changed != 1) throw new BusinessException("任务已启动或状态已变化，不能重复执行");
        MigrationTransfer.Control control = new MigrationTransfer.Control(task, () -> persist(task)); active = control;
        try { executor.execute(() -> execute(control, source, target, false)); }
        catch (RejectedExecutionException e) { active = null; task.status = "MIG_FAILED"; task.message = "执行器已停止，未开始写入"; persist(task); }
        return get(id, user);
    }

    public synchronized Task reviewResume(Long id, Long user) {
        return reviewResume(id, user, null);
    }

    public synchronized Task reviewObject(Long id, Long user, String sourceObject) {
        if (sourceObject == null) throw new BusinessException("请选择重跑对象");
        return reviewResume(id, user, sourceObject);
    }

    private Task reviewResume(Long id, Long user, String sourceObject) {
        Task task = get(id, user);
        if (!RESUMABLE.contains(task.status) || task.assessment == null) throw new BusinessException("该任务不支持恢复检查");
        if (sourceObject != null) retryIndex(task, sourceObject);
        task.resumeObject = sourceObject;
        DataSource source = connections.authorized(task.config.sourceId), target = connections.authorized(task.config.targetId);
        try (Connection read = connections.open(source); Connection write = connections.open(target)) {
            checkResume(read, write, task, target.getType());
            task.resumeFingerprint = MigrationDialect.hash(JSON.toJSONString(task.config) + JSON.toJSONString(task.assessment) + task.attempts + sourceObject);
            task.resumeCheckedAt = System.currentTimeMillis(); task.message = (sourceObject == null ? "恢复" : "单对象重跑 " + sourceObject) + "检查通过：稳定主键、源端基线及既有目标内容一致；只补写缺失行，不覆盖或删除";
        } catch (Exception error) {
            task.resumeFingerprint = null; task.resumeCheckedAt = 0; task.message = "恢复检查阻断：" + MigrationConnections.safeError(error);
        }
        persist(task); return task;
    }

    public synchronized Task resume(Long id, Long user, StartRequest request) {
        Task task = get(id, user);
        if (request == null || !request.offlineConfirmed || !request.reviewConfirmed || !RESUMABLE.contains(task.status)
                || task.resumeFingerprint == null || !task.resumeFingerprint.equals(request.fingerprint)
                || !Objects.equals(task.resumeObject, request.sourceObject)
                || System.currentTimeMillis() - task.resumeCheckedAt > 10 * 60 * 1000L) throw new BusinessException("请重新执行恢复检查，并确认冻结数据、已复核既有目标及不确定提交");
        if (active != null) throw new BusinessException("已有迁移任务执行中");
        DataSource source = connections.authorized(task.config.sourceId), target = connections.authorized(task.config.targetId);
        String previous = task.status; task.attempts++; task.resumeFingerprint = null;
        task.logs.add("第 " + task.attempts + " 次执行：" + (task.resumeObject == null ? "受限恢复全任务" : "只重跑 " + task.resumeObject) + "；原状态 " + previous);
        task.inFlightCommits = 0;
        task.status = "MIG_RUNNING"; task.stage = "RESUME_RECHECK"; task.finishedAt = 0; task.message = "受限恢复已启动；执行前再次校验，不覆盖既有目标内容";
        TaskDO update = new TaskDO(); fill(update, task);
        if (mapper.update(update, new LambdaUpdateWrapper<TaskDO>().eq(TaskDO::getId, id).eq(TaskDO::getUserId, user)
                .eq(TaskDO::getTaskType, TYPE).eq(TaskDO::getTaskStatus, previous)) != 1) throw new BusinessException("任务状态已变化，不能重复恢复");
        MigrationTransfer.Control control = new MigrationTransfer.Control(task, () -> persist(task)); active = control;
        try { executor.execute(() -> execute(control, source, target, true)); }
        catch (RejectedExecutionException error) { active = null; task.status = previous; task.message = "执行器已停止，未恢复写入"; persist(task); }
        return get(id, user);
    }

    private void checkResume(Connection read, Connection write, Task task, String targetType) throws Exception {
        if (task.objects.size() != task.assessment.tables.size()) throw new BusinessException("该任务不是可恢复的数据迁移");
        MigrationTransfer transfer = new MigrationTransfer(); MigrationTransfer.Control control = new MigrationTransfer.Control(task, () -> { });
        for (int i = 0; i < task.assessment.tables.size(); i++) {
            if (task.resumeObject != null && i != retryIndex(task, task.resumeObject)) continue;
            TablePlan table = task.assessment.tables.get(i); ObjectResult result = task.objects.get(i);
            Config scoped = MigrationDialect.scoped(task.config, table);
            if ("APPEND".equals(scoped.conflict) || "STRUCTURE".equals(scoped.scope)) throw new BusinessException("仅结构或追加对象不支持自动恢复，请人工核对");
            if (!"TABLE".equals(table.type) || table.skipped) throw new BusinessException("含跳过或程序对象的任务暂不允许自动恢复，请人工处理或复制配置");
            if (!"DATA".equals(scoped.scope) && !result.created) throw new BusinessException("未持久确认目标创建归属，不能自动接管同名对象");
            metadata.verifyCreated(write, task.config, table, targetType);
            TablePlan fresh = new TablePlan(); fresh.source = table.source; fresh.target = table.target; fresh.sourceSchema = table.sourceSchema;
            fresh.targetSchema = table.targetSchema; fresh.columns = table.columns;
            if (!"DATA".equals(scoped.scope)) {
                String sourceType = task.assessment.direction.startsWith("DM") ? "DM" : "ORACLE";
                MigrationConstraints.assess(read, task.config, fresh, sourceType);
                if (!JSON.toJSONString(fresh.postDdl).equals(JSON.toJSONString(table.postDdl))) throw new BusinessException("源端索引或约束变化，禁止恢复");
            }
            for (DdlOperation operation : table.postDdl) {
                if (result.appliedDdl.contains(operation.name)) MigrationConstraints.verify(write, table, operation);
                else if (MigrationConstraints.exists(write, table, operation)) throw new BusinessException("目标 DDL 归属未持久确认，必须人工核对，禁止自动恢复");
            }
            transfer.reviewResume(read, write, task.config, table, result, control);
        }
    }

    private static int retryIndex(Task task, String sourceObject) {
        if (task.objects.size() != task.assessment.tables.size()) throw new BusinessException("对象执行记录不完整，禁止重跑");
        for (int i = 0; i < task.objects.size(); i++) if (sourceObject.equals(task.objects.get(i).source)) {
            ObjectResult result = task.objects.get(i); TablePlan table = task.assessment.tables.get(i);
            if (!"TABLE".equals(table.type) || table.skipped || "COMPLETED".equals(result.stage) || "SKIPPED".equals(result.stage)) throw new BusinessException("仅未完成的普通表支持单对象受限重跑");
            if (dependencyBlocked(task, table, true)) throw new BusinessException("依赖对象未完成，请先处理依赖，不能单独重跑");
            return i;
        }
        throw new BusinessException("重跑对象不属于该任务");
    }

    private static boolean selected(Task task, int index, boolean resume) {
        return !resume || task.resumeObject == null || task.resumeObject.equals(task.objects.get(index).source);
    }

    static boolean dependencyBlocked(Task task, TablePlan table, boolean foreignKeys) {
        for (int i = 0; i < task.assessment.tables.size(); i++) {
            TablePlan dependency = task.assessment.tables.get(i); ObjectResult result = task.objects.get(i);
            boolean linked = table.dependencies.contains(result.source);
            if (foreignKeys) linked |= table.postDdl.stream().anyMatch(ddl -> ddl.foreignKey
                    && Objects.equals(ddl.referenceSchema, dependency.targetSchema) && Objects.equals(ddl.referenceTable, dependency.target));
            if (linked && dependency != table && !"COMPLETED".equals(result.stage)) return true;
        }
        return false;
    }

    private static void blocked(MigrationTransfer.Control control, ObjectResult result) {
        result.stage = "DEPENDENCY_BLOCKED"; result.message = "依赖对象未完成；不继续执行。目标可能保留此前已提交的数据和 DDL"; control.checkpoint();
    }

    static boolean certainSqlFailure(Exception error) {
        if (!(error instanceof SQLException)) return false;
        SQLException sql = (SQLException) error;
        for (SQLException item = sql; item != null; item = item.getNextException()) {
            String state = item.getSQLState();
            if (state == null || !(state.startsWith("22") || state.startsWith("23") || state.startsWith("42"))) return false;
        }
        return true;
    }

    private static void objectFailure(MigrationTransfer.Control control, Connection write, ObjectResult result, Exception error) throws Exception {
        if (!"CONTINUE_INDEPENDENT".equals(control.task.config.errorPolicy) || control.committing || control.checkpointFailed
                || !certainSqlFailure(error)) throw error;
        control.check();
        try { write.rollback(); } catch (SQLException rollback) { control.committing = true; throw rollback; }
        result.stage = "FAILED"; result.message = MigrationConnections.safeError(error);
        synchronized (control.task) { control.task.logs.add(result.source + " 失败，未提交批次已回滚；保留已提交内容，继续独立对象：" + result.message); }
        control.checkpoint();
    }

    private static void ddl(MigrationTransfer.Control control, Connection write, String sql, Runnable recorded) throws Exception {
        control.check(); String stage = control.task.stage;
        control.beginCommit("DDL_COMMITTING");
        try (Statement statement = write.createStatement()) {
            statement.setQueryTimeout(300); statement.execute(sql); recorded.run(); control.checkpoint();
            control.endCommit(stage);
        } catch (Exception error) {
            if (!control.checkpointFailed && certainSqlFailure(error)) control.endCommit(stage);
            throw error;
        }
    }

    public Task cancel(Long id, Long user) {
        Task task = get(id, user);
        MigrationTransfer.Control running = active;
        if (!ACTIVE.contains(task.status) || running == null || !id.equals(running.task.id)) throw new BusinessException("该任务当前不能取消");
        running.cancelled.set(true);
        mapper.update(null, new LambdaUpdateWrapper<TaskDO>().eq(TaskDO::getId, id).eq(TaskDO::getUserId, user)
                .eq(TaskDO::getTaskType, TYPE).in(TaskDO::getTaskStatus, ACTIVE).set(TaskDO::getTaskStatus, "MIG_CANCEL_REQUESTED"));
        return get(id, user);
    }

    private void execute(MigrationTransfer.Control control, DataSource source, DataSource target, boolean resume) {
        Task task = control.task; ObjectResult current = null;
        try (Connection read = connections.open(source); Connection write = connections.open(target)) {
            control.check(); write.setAutoCommit(false);
            if (resume) checkResume(read, write, task, target.getType());
            else {
                Assessment recheck = metadata.assess(read, write, task.config, source.getType(), target.getType());
                if (!recheck.isReady() || !task.assessment.fingerprint.equals(recheck.fingerprint)) throw new BusinessException("执行前发现结构、版本、目标状态或预检查变化，未开始写入，请新建或重新预检查");
            }
            if (task.config.parallelism == 1) {
                for (int i = 0; i < task.assessment.tables.size(); i++) if (selected(task, i, resume)) {
                    current = task.objects.get(i); executeTable(control, read, write, target.getType(), i, resume);
                }
            } else executeTables(control, source, target, resume);
            for (boolean foreignKeys : new boolean[] { false, true }) {
                for (int i = 0; i < task.assessment.tables.size(); i++) {
                    if (!selected(task, i, resume)) continue;
                    TablePlan table = task.assessment.tables.get(i); current = task.objects.get(i);
                    final ObjectResult result = current;
                    if (table.skipped || !"TABLE".equals(table.type) || !"COMPLETED".equals(current.stage)) continue;
                    if (dependencyBlocked(task, table, foreignKeys)) { blocked(control, current); continue; }
                    try {
                        for (DdlOperation operation : table.postDdl) {
                            if (operation.foreignKey != foreignKeys) continue;
                            control.check(); task.stage = "DEPENDENT_DDL"; current.stage = "DEPENDENT_DDL"; control.checkpoint();
                            if (!current.appliedDdl.contains(operation.name)) {
                                ddl(control, write, operation.sql, () -> result.appliedDdl.add(operation.name));
                            }
                            MigrationConstraints.verify(write, table, operation);
                        }
                        current.stage = "COMPLETED"; control.checkpoint();
                    } catch (Exception error) { objectFailure(control, write, current, error); }
                }
            }
            for (int i = 0; i < task.assessment.tables.size(); i++) {
                if (!selected(task, i, resume)) continue;
                TablePlan table = task.assessment.tables.get(i); current = task.objects.get(i);
                final ObjectResult result = current;
                if (table.skipped || "TABLE".equals(table.type)) continue;
                if (dependencyBlocked(task, table, true)) { blocked(control, current); continue; }
                try {
                    control.check(); task.stage = "PROGRAM_DDL"; current.stage = "CREATING"; control.checkpoint();
                    ddl(control, write, table.targetDdl, () -> result.created = true);
                    for (String after : table.afterDdl) ddl(control, write, after, () -> { });
                    MigrationObjects.verify(write, table); current.stage = "COMPLETED"; current.verification = "OBJECT_VALID_PASS"; control.checkpoint();
                } catch (Exception error) { objectFailure(control, write, current, error); }
            }
            control.check();
            // A later constraint failure also invalidates previously copied dependents; committed data remains visible in the report.
            boolean changed;
            do {
                changed = false;
                for (int i = 0; i < task.objects.size(); i++) if ("COMPLETED".equals(task.objects.get(i).stage)
                        && dependencyBlocked(task, task.assessment.tables.get(i), true)) { blocked(control, task.objects.get(i)); changed = true; }
            } while (changed);
            task.status = task.objects.stream().allMatch(o -> "COMPLETED".equals(o.stage)) ? "MIG_COMPLETED" : "MIG_PARTIAL";
            task.stage = "FINISHED"; task.message = "MIG_PARTIAL".equals(task.status) ? "部分对象未完成：请查看失败、依赖阻断及跳过清单；不得视为全部验收" : "所选对象已完成对应的结构或全量内容摘要校验";
        } catch (Exception e) {
            boolean uncertain = control.committing || control.checkpointFailed || task.inFlightCommits > 0;
            task.status = uncertain ? "MIG_NEEDS_REVIEW" : e instanceof MigrationTransfer.Cancelled ? "MIG_CANCELLED" : "MIG_FAILED";
            task.message = uncertain ? "提交、DDL 或持久记录结果不确定，需要人工核对目标；禁止自动重试" : e instanceof MigrationTransfer.Cancelled
                    ? "已取消；未提交批次回滚，已提交数据和已执行 DDL 保留" : MigrationConnections.safeError(e);
            if (current != null) { current.stage = task.status; current.message = task.message; }
            task.stage = "STOPPED";
        } finally {
            task.finishedAt = System.currentTimeMillis();
            task.resumeObject = null; task.resumeFingerprint = null; task.resumeCheckedAt = 0;
            task.logs.add(task.message + "；已确认提交 " + task.committedRows + " 行。目标可能保留已创建表，不自动删除。");
            try { persist(task); } finally { active = null; }
        }
    }

    private void executeTable(MigrationTransfer.Control control, Connection read, Connection write, String targetType, int index, boolean resume) throws Exception {
        Task task = control.task; TablePlan table = task.assessment.tables.get(index); ObjectResult result = task.objects.get(index);
        control.check(); result.message = null;
        if (table.skipped) { result.stage = "SKIPPED"; result.message = "既有目标对象未修改，也未校验"; control.checkpoint(); return; }
        if (!"TABLE".equals(table.type)) return;
        if (dependencyBlocked(task, table, false)) { blocked(control, result); return; }
        try {
            MigrationTransfer transfer = new MigrationTransfer(); Config scoped = MigrationDialect.scoped(task.config, table);
            if (!resume && !"STRUCTURE".equals(scoped.scope)) transfer.baseline(read, task.config, table, result, control);
            if (!resume && !"DATA".equals(scoped.scope)) {
                result.stage = "CREATING"; task.stage = "STRUCTURE"; control.checkpoint();
                if (table.rebuild) ddl(control, write, "DROP TABLE " + MigrationDialect.qualified(table.targetSchema, table.target), () -> result.appliedDdl.add("DROP_TABLE"));
                ddl(control, write, table.targetDdl, () -> result.created = true);
                for (String comment : table.afterDdl) ddl(control, write, comment, () -> { });
                metadata.verifyCreated(write, task.config, table, targetType);
            }
            if ("STRUCTURE".equals(scoped.scope)) { result.stage = "COMPLETED"; result.verification = "LOGICAL_STRUCTURE_PASS"; }
            else {
                if (!resume && "CLEAR".equals(scoped.conflict)) ddl(control, write, "TRUNCATE TABLE " + MigrationDialect.qualified(scoped.targetSchema, table.target), () -> result.appliedDdl.add("TRUNCATE_TABLE"));
                if (!resume && "APPEND".equals(scoped.conflict)) {
                    result.targetBaseline = MigrationTransfer.signature(transfer.digest(write, scoped.targetSchema, table.target, MigrationDialect.targetColumns(table), control)); control.checkpoint();
                } else if (!resume) MigrationMetadata.assertEmpty(write, scoped.targetSchema, table.target);
                MigrationMetadata.assertNoTriggers(write, scoped.targetSchema, table.target); result.reusedRows = 0;
                transfer.copy(read, write, task.config, table, result, control, resume);
                transfer.verify(read, write, task.config, table, result, control);
            }
            control.checkpoint();
        } catch (Exception error) { objectFailure(control, write, result, error); }
    }

    private void executeTables(MigrationTransfer.Control control, DataSource source, DataSource target, boolean resume) throws Exception {
        Task task = control.task;
        List<Integer> pending = new ArrayList<>();
        for (int i = 0; i < task.assessment.tables.size(); i++) if (selected(task, i, resume) && "TABLE".equals(task.assessment.tables.get(i).type)) pending.add(i);
        ExecutorService workers = Executors.newFixedThreadPool(task.config.parallelism);
        try {
            while (!pending.isEmpty()) {
                control.check(); List<Integer> wave = new ArrayList<>();
                for (int index : pending) {
                    TablePlan table = task.assessment.tables.get(index);
                    // Defer selected manual dependencies until their worker has finished, not merely until creation.
                    boolean waiting = pending.stream().anyMatch(other -> other != index && table.dependencies.contains(task.objects.get(other).source));
                    if (!waiting && wave.size() < task.config.parallelism) wave.add(index);
                }
                if (wave.isEmpty()) throw new BusinessException("表依赖无法排序，未继续写入");
                List<Future<Exception>> futures = new ArrayList<>();
                for (int index : wave) futures.add(workers.submit(() -> {
                    MigrationTransfer.Control worker = control.worker(); TablePlan table = task.assessment.tables.get(index);
                    boolean lob = table.columns.stream().anyMatch(c -> Arrays.asList("BLOB", "CLOB", "NCLOB").contains(c.kind)), acquired = false;
                    try {
                        if (lob) { while (!(acquired = control.lobSlot.tryAcquire(100, TimeUnit.MILLISECONDS))) worker.check(); }
                        worker.check();
                        try (Connection read = connections.open(source); Connection write = connections.open(target)) {
                            write.setAutoCommit(false); executeTable(worker, read, write, target.getType(), index, resume);
                        }
                        return null;
                    } catch (Exception error) {
                        control.stopped.set(true);
                        if (worker.committing) control.committing = true;
                        if (worker.checkpointFailed) control.checkpointFailed = true;
                        ObjectResult result = task.objects.get(index); result.stage = worker.committing || worker.checkpointFailed ? "MIG_NEEDS_REVIEW" : error instanceof MigrationTransfer.Cancelled ? "MIG_CANCELLED" : "FAILED";
                        result.message = MigrationConnections.safeError(error); worker.checkpoint(); return error;
                    } finally { if (acquired) control.lobSlot.release(); }
                }));
                Exception failure = null;
                // Join every started writer before releasing the task lock or returning a terminal status.
                for (Future<Exception> future : futures) {
                    Exception error;
                    try { error = future.get(); } catch (ExecutionException e) { error = e.getCause() instanceof Exception ? (Exception) e.getCause() : e; }
                    if (error != null && (failure == null || failure instanceof MigrationTransfer.Cancelled)) failure = error;
                }
                if (failure != null) throw failure;
                pending.removeAll(wave);
            }
        } finally { workers.shutdown(); }
    }

    private void persist(Task task) {
        MigrationTransfer.Control running = active;
        if (running != null && running.task == task && running.cancelled.get() && ACTIVE.contains(task.status)) task.status = "MIG_CANCEL_REQUESTED";
        TaskDO row = new TaskDO(); row.setId(task.id); fill(row, task);
        if (mapper.updateById(row) != 1) throw new IllegalStateException("Migration checkpoint unavailable");
    }

    private static void fill(TaskDO row, Task task) {
        byte[] content = JSON.toJSONBytes(task);
        if (content.length > 8 * 1024 * 1024) throw new BusinessException("迁移元数据超过 8MB 限制，请减少所选对象");
        row.setContent(content); row.setTaskStatus(task.status); row.setTaskProgress(task.stage); row.setGmtModified(new Date());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        for (TaskDO row : mapper.selectList(new LambdaQueryWrapper<TaskDO>().eq(TaskDO::getTaskType, TYPE).in(TaskDO::getTaskStatus, ACTIVE))) {
            Task task = snapshot(row);
            task.status = task.inFlightCommits > 0 || Arrays.asList("COMMITTING", "DDL_COMMITTING").contains(task.stage) ? "MIG_NEEDS_REVIEW" : "MIG_INTERRUPTED";
            task.message = "服务中断；已提交批次和 DDL 需人工核对，不自动重跑或续传"; task.finishedAt = System.currentTimeMillis(); persist(task);
        }
    }

    @PreDestroy public void shutdown() {
        MigrationTransfer.Control running = active; if (running != null) running.cancelled.set(true); executor.shutdown();
    }
}
