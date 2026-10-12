CREATE TABLE SYS_LOGINLOG (
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
