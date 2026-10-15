---
name: JDDBMS 数据迁移
description: 现有管理界面的迁移任务与向导扩展，样稿已确认，进入正式基础功能开发
colors:
  primary: "#006fff"
  primary-hover: "#2052dd"
  surface: "#ffffff"
  background: "#f1f3f9"
  text: "#303133"
  secondary: "#68728c"
  border: "#dfe6f0"
typography:
  body:
    fontFamily: "Helvetica Neue, Helvetica, PingFang SC, Hiragino Sans GB, Microsoft YaHei, Arial, sans-serif"
    fontSize: 12px
    lineHeight: 1.6
  heading:
    fontFamily: "Helvetica Neue, Helvetica, PingFang SC, Hiragino Sans GB, Microsoft YaHei, Arial, sans-serif"
    fontSize: 16px
    fontWeight: 600
    lineHeight: 1.5
  code:
    fontFamily: "Consolas, monospace"
    fontSize: 12px
    lineHeight: 20px
spacing:
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
rounded:
  sm: 4px
components:
  primary-button:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.surface}"
    typography: "{typography.body}"
    rounded: "{rounded.sm}"
  primary-button-hover:
    backgroundColor: "{colors.primary-hover}"
    textColor: "{colors.surface}"
  task-table:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    typography: "{typography.body}"
  table-heading:
    backgroundColor: "{colors.background}"
    textColor: "{colors.secondary}"
---

## Overview

Extension：增加数据迁移，不重设计个人中心和工作区。面向 DBA 与实施人员的紧凑、可靠操作界面。五档：视觉变化 2、动效 1、信息密度 8、素材依赖 1、品牌一致性 10；采用熟悉的表格和步骤导航，不增加品牌图片、装饰图形或渐变。

页面角色为配置、执行监控与审阅；主要观看距离是桌面显示器，气质克制。任务列表提供可扫描的状态，向导单步呈现相关配置，细节放在对象配置抽屉或高级选项，不把全部参数堆在首页。

## Colors

复用蓝色主动作、白色内容、浅灰表头、现有文本与边框。每步仅一个主要动作；风险/错误状态同时提供文字，不依赖颜色辨识。不中途改变全局主题。

## Typography

工具正文、表格、按钮与标签 12px，与左树和工作区统一；局部标题 16px。沿用项目字体，SQL 使用现有 Monaco 编辑器与 12px 等宽字。

## Layout

入口置于个人中心所在管理导航。任务页为标题与新建按钮、筛选条、任务表和分页。向导占满右侧内容区：上方步骤条，中间单步配置，下方固定操作条；不改变左侧全局导航。

源与目标连接并排、明确区分方向，不通过交换按钮静默改变已配置对象。对象选择区使用搜索、分页和筛选后选择语义。映射采用源/目标逐列对照表。预检查用分组问题列表与并排 DDL 预览。运行页为总体阶段、对象进度、错误及日志。

间距采用 4/8/16/24px。窄屏连接区纵排，表格内部横向滚动，底部主要操作始终可达。草稿、校验结果和运行状态不因页签切换或刷新丢失。

续开发：对象配置窗口分为单对象策略、字段映射、AND 筛选和业务依赖；任务页只显示实际策略与条件数量，行速率与批次并列。迁移专用弹窗采用 4vh 外边距、正文最大高度 `92vh - 120px` 及内部滚动，底部保存/取消独立于正文，1280×720 下仍可点击。正文和表单 12px，标题 16px；不改全局 Element UI 或其他业务弹窗。

失败恢复扩展：遇错策略放在映射页批次设置下方，并在执行审阅时重复展示；默认停止。结果表右侧固定“恢复操作”列，失败普通表提供“检查并重跑该表”，成功对象显示“无需重跑”，其他对象提示人工核对。检查通过仍需确认具体对象，不以颜色或通用重试图标代替风险说明。任务配置内容比较忽略 JSON 字段顺序，但任何实际策略/映射变化均使预检查失效。

2026-09-29 布局整改（v0 已确认，正式页面已实现）：Redesign · Preserve，仅调整迁移向导。保留步骤条、操作条、12px 正文、原有业务字段和提交行为；不改左侧导航、路由或工作区缓存。页面使用固定高度 Flex 布局和 `min-height: 0`，表格正文、执行设置及弹窗内容区独立滚动，不让页面整体滚动。对象选择增加“全选当前筛选结果”（当前模式、跨分页、仅可迁移对象），保留已选并遵守 100 个对象上限；区分全选与本页选择。映射策略分为“对象映射 / 执行设置”：前者保留默认目标模式、范围、同名策略及源→目标对照表，详细配置继续使用既有窗口；后者按复制与事务、并发与限速、目标存储与异常处理分组。风险文案仍在映射页可见，不藏入高级选项。最高风险为滚动容器改造，需确保底部操作始终可达、表格列可横向滚动。全选分页读取完成后才合并选择；超限、读取失败或上下文变化时不写入部分结果。执行设置不增加配置字段，页签状态不写入迁移草稿。验收过程和部署边界见《迁移向导界面整改记录-20260929.md》。

## Elevation & Depth

以细边框和浅灰分区划分层级，不用重阴影。配置抽屉与二次确认沿用 Element UI 的现有层级；背景操作在提交中禁用。

## Shapes

按钮和输入沿用 4px 小圆角。表格、步骤条、标签不增加大胶囊或大圆角卡片。

## Components

复用 Element UI 表单、步骤条、表格、分页、抽屉、消息提示和既有图标。任务状态包括草稿、预检查、就绪、排队、运行、正在停止、已取消、部分失败、失败、待核对、中断及完成；具体后端状态机在实现阶段与数据库模型一起定义。

向导按钮：上一步、保存草稿、下一步；最终步骤为“开始迁移”。按钮应展示禁用原因、加载反馈与成功/失败结果。对象列表支持勾选/半选，不让搜索过滤意外扩大迁移范围。

任务详情提供总览、对象、错误、日志、校验和报告。进度使用真实已提交统计，未知总量采用不定进度。任何部分失败或校验未通过不能呈现全部完成。

覆盖空数据源、空对象、加载、权限不足、预检查阻断、运行、取消等待、恢复待核对等状态。轮询仅更新任务区域，不刷新全页；键盘操作、焦点和状态文案沿用现有交互。

## Do's and Don'ts

- 保留蓝白配色、12px 正文、现有路由和工作区缓存行为。
- 仅参考 DTS 的业务流程，不复刻其第三方代码、品牌和桌面界面。
- 不使用虚假统计、模拟进度或永远可点击但没有真实能力的按钮。
- 危险策略展示具体目标与损失范围，不能隐藏在高级设置里后直接执行。
- 未确认本方案前不进入完整页面开发；确认后先提供 v0，再进行功能实现。
