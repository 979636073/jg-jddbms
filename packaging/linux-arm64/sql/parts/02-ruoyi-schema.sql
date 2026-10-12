CREATE TABLE SYS_DEPT (
  dept_id BIGINT not null IDENTITY(1,1),
  parent_id BIGINT default 0,
  ancestors varchar(50) default '',
  dept_name varchar(30) default '',
  order_num INT default 0,
  leader varchar(20),
  phone varchar(11),
  email varchar(50),
  status char(1) default '0',
  del_flag char(1) default '0',
  create_by varchar(64) default '',
  create_time TIMESTAMP,
  update_by varchar(64) default '',
  update_time TIMESTAMP,
  primary key (dept_id)
);

CREATE TABLE SYS_USER (
  user_id BIGINT not null IDENTITY(1,1),
  dept_id BIGINT,
  user_name varchar(30) not null,
  nick_name varchar(30) not null,
  user_type varchar(2) default '00',
  email varchar(50) default '',
  phonenumber varchar(11) default '',
  sex char(1) default '0',
  avatar varchar(100) default '',
  password varchar(100) default '',
  status char(1) default '0',
  del_flag char(1) default '0',
  login_ip varchar(128) default '',
  login_date TIMESTAMP,
  create_by varchar(64) default '',
  create_time TIMESTAMP,
  update_by varchar(64) default '',
  update_time TIMESTAMP,
  remark varchar(500),
  primary key (user_id)
);

CREATE TABLE SYS_POST (
  post_id BIGINT not null IDENTITY(1,1),
  post_code varchar(64) not null,
  post_name varchar(50) not null,
  post_sort INT not null,
  status char(1) not null,
  create_by varchar(64) default '',
  create_time TIMESTAMP,
  update_by varchar(64) default '',
  update_time TIMESTAMP,
  remark varchar(500),
  primary key (post_id)
);

CREATE TABLE SYS_ROLE (
  role_id BIGINT not null IDENTITY(1,1),
  role_name varchar(30) not null,
  role_key varchar(100) not null,
  role_sort INT not null,
  data_scope char(1) default '1',
  menu_check_strictly INT default 1,
  dept_check_strictly INT default 1,
  status char(1) not null,
  del_flag char(1) default '0',
  create_by varchar(64) default '',
  create_time TIMESTAMP,
  update_by varchar(64) default '',
  update_time TIMESTAMP,
  remark varchar(500),
  primary key (role_id)
);

CREATE TABLE SYS_MENU (
  menu_id BIGINT not null IDENTITY(1,1),
  menu_name varchar(50) not null,
  parent_id BIGINT default 0,
  order_num INT default 0,
  path varchar(200) default '',
  component varchar(255),
  query varchar(255),
  is_frame INT default 1,
  is_cache INT default 0,
  menu_type char(1) default '',
  visible char(1) default 0,
  status char(1) default 0,
  perms varchar(100),
  icon varchar(100) default '#',
  create_by varchar(64) default '',
  create_time TIMESTAMP,
  update_by varchar(64) default '',
  update_time TIMESTAMP,
  remark varchar(500) default '',
  primary key (menu_id)
);

CREATE TABLE SYS_USER_ROLE (
  user_id BIGINT not null,
  role_id BIGINT not null,
  primary key(user_id, role_id)
);

CREATE TABLE SYS_ROLE_MENU (
  role_id BIGINT not null,
  menu_id BIGINT not null,
  primary key(role_id, menu_id)
);

CREATE TABLE SYS_ROLE_DEPT (
  role_id BIGINT not null,
  dept_id BIGINT not null,
  primary key(role_id, dept_id)
);

CREATE TABLE SYS_USER_POST (
  user_id BIGINT not null,
  post_id BIGINT not null,
  primary key (user_id, post_id)
);

CREATE TABLE SYS_OPER_LOG (
  oper_id BIGINT not null IDENTITY(1,1),
  title varchar(50) default '',
  business_type INT default 0,
  method varchar(100) default '',
  request_method varchar(10) default '',
  operator_type INT default 0,
  oper_name varchar(50) default '',
  dept_name varchar(50) default '',
  oper_url varchar(255) default '',
  oper_ip varchar(128) default '',
  oper_location varchar(255) default '',
  oper_param varchar(2000) default '',
  json_result varchar(2000) default '',
  status INT default 0,
  error_msg varchar(2000) default '',
  oper_time TIMESTAMP,
  cost_time BIGINT default 0,
  primary key (oper_id)
);

CREATE TABLE SYS_DICT_TYPE (
  dict_id BIGINT not null IDENTITY(1,1),
  dict_name varchar(100) default '',
  dict_type varchar(100) default '',
  status char(1) default '0',
  create_by varchar(64) default '',
  create_time TIMESTAMP,
  update_by varchar(64) default '',
  update_time TIMESTAMP,
  remark varchar(500),
  primary key (dict_id),
  unique (dict_type)
);

CREATE TABLE SYS_DICT_DATA (
  dict_code BIGINT not null IDENTITY(1,1),
  dict_sort INT default 0,
  dict_label varchar(100) default '',
  dict_value varchar(100) default '',
  dict_type varchar(100) default '',
  css_class varchar(100),
  list_class varchar(100),
  is_default char(1) default 'N',
  status char(1) default '0',
  create_by varchar(64) default '',
  create_time TIMESTAMP,
  update_by varchar(64) default '',
  update_time TIMESTAMP,
  remark varchar(500),
  primary key (dict_code)
);

CREATE TABLE SYS_CONFIG (
  config_id INT not null IDENTITY(1,1),
  config_name varchar(100) default '',
  config_key varchar(100) default '',
  config_value varchar(500) default '',
  config_type char(1) default 'N',
  create_by varchar(64) default '',
  create_time TIMESTAMP,
  update_by varchar(64) default '',
  update_time TIMESTAMP,
  remark varchar(500),
  primary key (config_id)
);

CREATE TABLE SYS_LOGININFOR (
  info_id BIGINT not null IDENTITY(1,1),
  user_name varchar(50) default '',
  ipaddr varchar(128) default '',
  login_location varchar(255) default '',
  browser varchar(50) default '',
  os varchar(50) default '',
  status char(1) default '0',
  msg varchar(255) default '',
  login_time TIMESTAMP,
  primary key (info_id)
);

CREATE TABLE SYS_JOB (
  job_id BIGINT not null IDENTITY(1,1),
  job_name varchar(64) default '',
  job_group varchar(64) default 'DEFAULT',
  invoke_target varchar(500) not null,
  cron_expression varchar(255) default '',
  misfire_policy varchar(20) default '3',
  concurrent char(1) default '1',
  status char(1) default '0',
  create_by varchar(64) default '',
  create_time TIMESTAMP,
  update_by varchar(64) default '',
  update_time TIMESTAMP,
  remark varchar(500) default '',
  primary key (job_id, job_name, job_group)
);

CREATE TABLE SYS_JOB_LOG (
  job_log_id BIGINT not null IDENTITY(1,1),
  job_name varchar(64) not null,
  job_group varchar(64) not null,
  invoke_target varchar(500) not null,
  job_message varchar(500),
  status char(1) default '0',
  exception_info varchar(2000) default '',
  create_time TIMESTAMP,
  primary key (job_log_id)
);

CREATE TABLE SYS_NOTICE (
  notice_id INT not null IDENTITY(1,1),
  notice_title varchar(50) not null,
  notice_type char(1) not null,
  notice_content BLOB,
  status char(1) default '0',
  create_by varchar(64) default '',
  create_time TIMESTAMP,
  update_by varchar(64) default '',
  update_time TIMESTAMP,
  remark varchar(255),
  primary key (notice_id)
);

CREATE TABLE GEN_TABLE (
  table_id BIGINT not null IDENTITY(1,1),
  table_name varchar(200) default '',
  table_comment varchar(500) default '',
  sub_table_name varchar(64),
  sub_table_fk_name varchar(64),
  class_name varchar(100) default '',
  tpl_category varchar(200) default 'crud',
  tpl_web_type varchar(30) default '',
  package_name varchar(100),
  module_name varchar(30),
  business_name varchar(30),
  function_name varchar(50),
  function_author varchar(50),
  gen_type char(1) default '0',
  gen_path varchar(200) default '/',
  options varchar(1000),
  create_by varchar(64) default '',
  create_time TIMESTAMP,
  update_by varchar(64) default '',
  update_time TIMESTAMP,
  remark varchar(500),
  primary key (table_id)
);

CREATE TABLE GEN_TABLE_COLUMN (
  column_id BIGINT not null IDENTITY(1,1),
  table_id BIGINT,
  column_name varchar(200),
  column_comment varchar(500),
  column_type varchar(100),
  java_type varchar(500),
  java_field varchar(200),
  is_pk char(1),
  is_increment char(1),
  is_required char(1),
  is_insert char(1),
  is_edit char(1),
  is_list char(1),
  is_query char(1),
  query_type varchar(200) default 'EQ',
  html_type varchar(200),
  dict_type varchar(200) default '',
  sort int,
  create_by varchar(64) default '',
  create_time TIMESTAMP,
  update_by varchar(64) default '',
  update_time TIMESTAMP,
  primary key (column_id)
);
