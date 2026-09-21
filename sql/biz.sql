
-- ----------------------------
-- === 保障机构表
-- ----------------------------
create table SYS_biz_bzjg
(
    id     varchar(38) not null constraint PRIMARY_biz_bzjg primary key,
    parent_id   varchar(38) default '0',
    ancestors   varchar(50) default '':: varchar,
    name   varchar(30) default '':: varchar,
    order_num   int default 0,
    leader      varchar(20),
    phone       varchar(11),
    email       varchar(50),
    status      char(1)     default '0'::bpchar,
    del_flag    char(1)     default '0'::bpchar,
    create_by   varchar(64) default '':: varchar,
    create_time datetime,
    update_by   varchar(64) default '':: varchar,
    update_time datetime
);
comment on table SYS_biz__bzjg is '保障机构表';
comment on column SYS_biz__bzjg.dept_id is 'id';
comment on column SYS_biz__bzjg.parent_id is '父id';
comment on column SYS_biz__bzjg.ancestors is '祖级列表';
comment on column SYS_biz__bzjg.dept_name is '机构名称';
comment on column SYS_biz__bzjg.order_num is '显示顺序';
comment on column SYS_biz__bzjg.leader is '负责人';
comment on column SYS_biz__bzjg.phone is '联系电话';
comment on column SYS_biz__bzjg.email is '邮箱';
comment on column SYS_biz__bzjg.status is '部门状态（0正常 1停用）';
comment on column SYS_biz__bzjg.del_flag is '删除标志（0代表存在 2代表删除）';
comment on column SYS_biz__bzjg.create_by is '创建者';
comment on column SYS_biz__bzjg.create_time is '创建时间';
comment on column SYS_biz__bzjg.update_by is '更新者';
comment on column SYS_biz__bzjg.update_time is '更新时间';