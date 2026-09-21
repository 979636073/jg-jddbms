const OperationColumn = {
    ShiftOut: 'shiftOut', // 移出数据源
    Refresh: 'refresh', // 刷新各级菜单
    CreateTable: 'createTable', //创建表
    CreateConsole: 'createConsole', // 新建console
    DeleteTable: 'deleteTable', // 删除表
    OpenTable: 'openTable', // 删除表
    ViewDDL: 'viewDDL', // 查看ddl
    EditSource: 'editSource', // 编辑数据源
    Pin: 'pin', // 置顶
    EditTable: 'editTable', // 编辑表
    EditTableData: 'editTableData', // 编辑表数据
    CopyName: 'copyName', // 复制名称
    EditView: 'editView', // 编辑视图
    OpenView: 'openView', // 打开视图
    OpenFunction: 'openFunction', // 打开函数
    OpenProcedure: 'openProcedure', // 打开存储过程
    OpenTrigger: 'openTrigger', // 打开触发器
    CreateSchema: 'createSchema', // 新建schema
    CreateDatabase: 'createDatabase', // 新建database
    ViewAllTable: 'viewAllTable', // 查看所有的表
  }

const DatabaseTypeCode = {
  MYSQL: 'MYSQL',
  ORACLE: 'ORACLE',
  DB2: 'DB2',
  MONGODB: 'MONGODB',
  REDIS: 'REDIS',
  H2: 'H2',
  POSTGRESQL: 'POSTGRESQL',
  SQLSERVER: 'SQLSERVER',
  SQLITE: 'SQLITE',
  MARIADB: 'MARIADB',
  CLICKHOUSE: 'CLICKHOUSE',
  DM: "DM",
  OCEANBASE: "OCEANBASE",
  PRESTO: "PRESTO",
  HIVE: "HIVE",
  KINGBASE: "KINGBASE",
  OSCAR: "OSCAR",
  GBASE: 'GBASE'

}
export const database = [
  {
    name: "MYSQL",
    img: "",
    code: DatabaseTypeCode.MYSQL,
    icon: "\uec6d",
  },
  // {
  //   name: "H2",
  //   img: "",
  //   code: DatabaseTypeCode.H2,
  //   icon: "\ue61c",
  // },
  {
    name: "Oracle",
    img: "",
    code: DatabaseTypeCode.ORACLE,
    icon: "\uec48",
  },
  // {
  //   name: "PostgreSql",
  //   img: "",
  //   code: DatabaseTypeCode.POSTGRESQL,
  //   icon: "\uec5d",
  // },
  {
    name: "SQLServer",
    img: "",
    code: DatabaseTypeCode.SQLSERVER,
    icon: "\ue664",
  },
  // {
  //   name: "SQLite",
  //   img: "",
  //   code: DatabaseTypeCode.SQLITE,
  //   icon: "\ue65a",
  // },
  // {
  //   name: "Mariadb",
  //   img: "",
  //   code: DatabaseTypeCode.MARIADB,
  //   icon: "\ue6f5",
  // },
  // {
  //   name: "ClickHouse",
  //   img: "",
  //   code: DatabaseTypeCode.CLICKHOUSE,
  //   icon: "\ue8f4",
  // },
  {
    name: "DM",
    img: "",
    code: DatabaseTypeCode.DM,
    icon: "\ue655",
  },
  // {
  //   name: "Presto",
  //   img: "",
  //   code: DatabaseTypeCode.PRESTO,
  //   icon: "\ue60b",
  // },
  // {
  //   name: "DB2",
  //   img: "",
  //   code: DatabaseTypeCode.DB2,
  //   icon: "\ue60a",
  // },
  // {
  //   name: "OceanBase",
  //   img: "",
  //   code: DatabaseTypeCode.OCEANBASE,
  //   icon: "\ue982",
  // },
  // {
  //   name: "Hive",
  //   img: "",
  //   code: DatabaseTypeCode.HIVE,
  //   icon: "\ue60e",
  // },
  {
    name: "KingBase",
    img: "",
    code: DatabaseTypeCode.KINGBASE,
    icon: "\ue6a0",
  },
  // {
  //   name: "MongoDB",
  //   img: "",
  //   code: DatabaseTypeCode.MONGODB,
  //   icon: "\uec21",
  // },
  {
    name: 'OsCar(神通)',
    img: "",
    code: DatabaseTypeCode.OSCAR,
    icon: "\ue6a7",
  },
  {
    name: 'GBase(南大通用)',
    img: "",
    code: DatabaseTypeCode.GBASE,
    icon: "\ue616",
  }
];

const sshConfig = {
  items: [
    {
      defaultValue: false,
      inputType: 'select',
      labelNameCN: "使用SSH",
      labelNameEN: "USE SSH",
      name: "use",
      required: false,
      selects: [
        {
          label: "false",
          value: false,
        },
        {
          label: "true",
          value: true,
        },
      ],
    },
    {
      defaultValue: "",
      inputType: 'input',
      labelNameCN: "SSH 主机",
      labelNameEN: "SSH Hostname",
      name: "hostName",
      required: false,
      styles: {
        width: "70%",
      },
    },
    {
      defaultValue: "22",
      inputType: 'input',
      labelNameCN: "SSH 端口",
      labelNameEN: "Port",
      name: "port",
      required: false,
      styles: {
        width: "30%",
        labelWidthEN: "40px",
        labelWidthCN: "70px",
        labelAlign: "right",
      },
    },
    {
      defaultValue: "",
      inputType: 'input',
      labelNameCN: "用户名",
      labelNameEN: "SSH UserName",
      name: "userName",
      required: false,
      styles: {
        width: "70%",
      },
    },
    {
      defaultValue: "",
      inputType: 'input',
      labelNameCN: "本地端口",
      labelNameEN: "LocalPort",
      name: "localPort",
      placeholder: "不必填",
      placeholderEN: "Need not fill in",
      required: false,
      styles: {
        width: "30%",
        labelWidthEN: "70px",
        labelWidthCN: "70px",
        labelAlign: "right",
      },
    },
    {
      defaultValue: "password",
      inputType: 'select',
      labelNameCN: "身份验证",
      labelNameEN: "Authentication",
      name: "authenticationType",
      required: true,
      selects: [
        {
          items: [
            {
              defaultValue: "",
              inputType: 'password',
              labelNameCN: "密码",
              labelNameEN: "Password",
              name: "password",
              required: true,
            },
          ],
          label: "password",
          value: "password",
        },
        {
          items: [
            {
              defaultValue: "",
              inputType: 'input',
              labelNameCN: "密钥文件",
              labelNameEN: "Private key file",
              name: "keyFile",
              required: true,
              placeholder: "/user/userName/.ssh/xxxx",
              placeholderEN: "/user/userName/.ssh/xxxx",
            },
            {
              defaultValue: "",
              inputType: 'password',
              labelNameCN: "密码短语",
              labelNameEN: "Passphrase",
              name: "passphrase",
              required: true,
            },
          ],
          label: "Private key",
          value: "keyFile",
        },
      ],
      styles: {
        width: "50%",
      },
    },
  ],
};

const envItem = {
  defaultValue: "",
  inputType: 'select',
  labelNameCN: "环境",
  labelNameEN: "Env",
  name: "environmentId",
  required: true,
  selects: [],
  styles: {
    width: "50%",
  },
};
export const dataSourceFormConfigs = [
  // MYSQL
  {
    baseInfo: {
      items: [
        {
          defaultValue: "@localhost",
          inputType: 'input',
          labelNameCN: "名称",
          labelNameEN: "Name",
          name: "alias",
          required: true,
        },
        envItem,
        {
          defaultValue: "localhost",
          inputType: 'input',
          labelNameCN: "主机",
          labelNameEN: "Host",
          name: "host",
          required: true,
          styles: {
            width: "70%",
          },
        },
        {
          defaultValue: "3306",
          inputType: 'input',
          labelNameCN: "端口",
          labelNameEN: "Port",
          name: "port",
          labelTextAlign: "right",
          required: true,
          styles: {
            width: "30%",
            labelWidthEN: "40px",
            labelWidthCN: "40px",
            labelAlign: "right",
          },
        },
        {
          defaultValue: '1',
          inputType: 'select',
          labelNameCN: "身份验证",
          labelNameEN: "Authentication",
          name: "authenticationType",
          required: true,
          styles: {
            width: "50%",
          },
          selects: [
            {
              items: [
                {
                  defaultValue: "root",
                  inputType: 'input',
                  labelNameCN: "用户名",
                  labelNameEN: "User",
                  name: "user",
                  required: true,
                },
                {
                  defaultValue: "",
                  inputType: 'password',
                  labelNameCN: "密码",
                  labelNameEN: "Password",
                  name: "password",
                  required: true,
                },
              ],
              label: "User&Password",
              value: '1',
            },
            {
              label: "NONE",
              value: '2',
              items: [],
            },
          ],
        },
        {
          defaultValue: "",
          inputType: 'input',
          labelNameCN: "数据库",
          labelNameEN: "Database",
          name: "database",
          required: false,
        },
        {
          defaultValue: "jdbc:mysql://localhost:3306",
          inputType: 'input',
          labelNameCN: "URL",
          labelNameEN: "URL",
          name: "url",
          required: true,
        },
      ],
      pattern: /jdbc:mysql:\/\/(.*):(\d+)(\/(\w+))?/,
      template: "jdbc:mysql://{host}:{port}/{database}",
    },
    ssh: sshConfig,
    extendInfo: [
      {
        key: "zeroDateTimeBehavior",
        value: "convertToNull",
      },
    ],
    type: 'MYSQL',
  },
  // POSTGRESQL
  {
    type: 'POSTGRESQL',
    baseInfo: {
      items: [
        {
          defaultValue: "@localhost",
          inputType: 'input',
          labelNameCN: "名称",
          labelNameEN: "Name",
          name: "alias",
          required: true,
        },
        envItem,
        {
          defaultValue: "localhost",
          inputType: 'input',
          labelNameCN: "主机",
          labelNameEN: "Host",
          name: "host",
          required: true,
          styles: {
            width: "70%",
          },
        },
        {
          defaultValue: "5432",
          inputType: 'input',
          labelNameCN: "端口",
          labelNameEN: "Port",
          name: "port",
          labelTextAlign: "right",
          required: true,
          styles: {
            width: "30%",
            labelWidthEN: "40px",
            labelWidthCN: "40px",
            labelAlign: "right",
          },
        },
        {
          defaultValue: '1',
          inputType: 'select',
          labelNameCN: "身份验证",
          labelNameEN: "Authentication",
          name: "authenticationType",
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: "root",
                  inputType: 'input',
                  labelNameCN: "用户名",
                  labelNameEN: "User",
                  name: "user",
                  required: true,
                },
                {
                  defaultValue: "",
                  inputType: 'password',
                  labelNameCN: "密码",
                  labelNameEN: "Password",
                  name: "password",
                  required: true,
                },
              ],
              label: "User&Password",
              value: '1',
            },
            {
              label: "NONE",
              value: '2',
              items: [],
            },
          ],
          styles: {
            width: "50%",
          },
        },
        {
          defaultValue: "postgres",
          inputType: 'input',
          labelNameCN: "数据库",
          labelNameEN: "Database",
          name: "database",
          required: false,
        },
        {
          defaultValue: "jdbc:postgresql://localhost:5432",
          inputType: 'input',
          labelNameCN: "URL",
          labelNameEN: "URL",
          name: "url",
          required: true,
        },
      ],
      pattern: /jdbc:postgresql:\/\/(.*):(\d+)(\/(\w+))?/,
      template: "jdbc:postgresql://{host}:{port}/{database}",
    },
    ssh: sshConfig,
  },
  // ORACLE
  {
    type: 'ORACLE',
    baseInfo: {
      items: [
        {
          defaultValue: "@localhost",
          inputType: 'input',
          labelNameCN: "名称",
          labelNameEN: "Name",
          name: "alias",
          required: true,
        },
        envItem,
        {
          defaultValue: "localhost",
          inputType: 'input',
          labelNameCN: "主机",
          labelNameEN: "Host",
          name: "host",
          required: true,
          styles: {
            width: "70%",
          },
        },
        {
          defaultValue: "1521",
          inputType: 'input',
          labelNameCN: "端口",
          labelNameEN: "Port",
          name: "port",
          labelTextAlign: "right",
          required: true,
          styles: {
            width: "30%",
            labelWidthEN: "40px",
            labelWidthCN: "40px",
            labelAlign: "right",
          },
        },
        {
          defaultValue: "sid",
          inputType: 'select',
          labelNameCN: "链接类型",
          labelNameEN: "Service type",
          name: "serviceType",
          required: true,
          selects: [
            {
              label: "SID",
              value: "sid",
              items: [
                {
                  defaultValue: "XE",
                  inputType: 'input',
                  labelNameCN: "SID",
                  labelNameEN: "SID",
                  name: "sid",
                  required: true,
                  styles: {
                    width: "70%",
                  },
                },
              ],
              onChange: (data) => {
                data.baseInfo.pattern = /jdbc:oracle:(.*):@(.*):(\d+):(.*)/;
                data.baseInfo.template =
                  "jdbc:oracle:{driver}:@{host}:{port}:{sid}";
                return data;
              },
            },
            {
              label: "Service",
              value: "service",
              items: [
                {
                  defaultValue: "XE",
                  inputType: 'input',
                  labelNameCN: "服务名",
                  labelNameEN: "Service name",
                  name: "serviceName",
                  required: true,
                  styles: {
                    width: "70%",
                  },
                },
              ],
              onChange: (data) => {
                data.baseInfo.pattern =
                  /jdbc:oracle:(.*):@\/\/(.*):(\d+)\/(.*)/;
                data.baseInfo.template =
                  "jdbc:oracle:{driver}:@//{host}:{port}/{serviceName}";
                return data;
              },
            },
          ],
          styles: {
            width: "50%",
          },
        },
        {
          defaultValue: "thin",
          inputType: 'select',
          labelNameCN: "驱动",
          labelNameEN: "Driver",
          name: "driver",
          required: true,
          labelTextAlign: "right",
          selects: [
            {
              value: "THIN",
              label: "thin",
            },
            {
              value: "OCI",
              label: "oci",
            },
            {
              value: "OCI8",
              label: "oci8",
            },
          ],
          styles: {
            width: "30%",
            labelWidthEN: "70px",
            labelWidthCN: "40px",
            labelAlign: "right",
          },
        },
        {
          defaultValue: '1',
          inputType: 'select',
          labelNameCN: "身份验证",
          labelNameEN: "Authentication",
          name: "authenticationType",
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: "root",
                  inputType: 'input',
                  labelNameCN: "用户名",
                  labelNameEN: "User",
                  name: "user",
                  required: true,
                },
                {
                  defaultValue: "",
                  inputType: 'password',
                  labelNameCN: "密码",
                  labelNameEN: "Password",
                  name: "password",
                  required: true,
                },
              ],
              label: "User&Password",
              value: '1',
            },
            {
              label: "NONE",
              value: '2',
              items: [],
            },
          ],
          styles: {
            width: "50%",
          },
        },
        {
          defaultValue: "jdbc:oracle:thin:@localhost:1521:XE",
          inputType: 'input',
          labelNameCN: "URL",
          labelNameEN: "URL",
          name: "url",
          required: true,
        },
      ],
      pattern: /jdbc:oracle:(.*):@(.*):(\d+):(.*)/,
      template: "jdbc:oracle:{driver}:@{host}:{port}:{sid}",
    },
    ssh: sshConfig,
  },
  // H2
  {
    type: 'H2',
    baseInfo: {
      items: [
        {
          defaultValue: "@localhost",
          inputType: 'input',
          labelNameCN: "名称",
          labelNameEN: "Name",
          name: "alias",
          required: true,
        },
        envItem,
        {
          defaultValue: "localhost",
          inputType: 'input',
          labelNameCN: "主机",
          labelNameEN: "Host",
          name: "host",
          required: true,
          styles: {
            width: "70%",
          },
        },
        {
          defaultValue: "9092",
          inputType: 'input',
          labelNameCN: "端口",
          labelNameEN: "Port",
          name: "port",
          labelTextAlign: "right",
          required: true,
          styles: {
            width: "30%",
            labelWidthEN: "40px",
            labelWidthCN: "40px",
            labelAlign: "right",
          },
        },
        {
          defaultValue: "TCP",
          inputType: 'select',
          labelNameCN: "链接类型",
          labelNameEN: "Service type",
          name: "serviceType",
          required: true,
          selects: [
            {
              label: '线上',
              value: "TCP",
              items: [],
              onChange: (data) => {
                data.baseInfo.pattern = /jdbc:h2:tcp:\/\/(.*):(\d+)(\/(\w+))?/;
                data.baseInfo.template =
                  "jdbc:h2:tcp://{host}:{port}/{database}";
                return data;
              },
            },
            {
              label: '本地',
              value: "LocalFile",
              items: [
                {
                  defaultValue: "",
                  inputType: 'input',
                  labelNameCN: "File",
                  labelNameEN: "File",
                  name: "file",
                  required: true,
                },
              ],
              onChange: (data) => {
                data.baseInfo.pattern = /jdbc:h2:(.*)?/;
                data.baseInfo.template = "jdbc:h2:{file}";
                return data;
              },
            },
          ],
          styles: {
            width: "70%",
          },
        },
        {
          defaultValue: '1',
          inputType: 'select',
          labelNameCN: "身份验证",
          labelNameEN: "Authentication",
          name: "authenticationType",
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: "root",
                  inputType: 'input',
                  labelNameCN: "用户名",
                  labelNameEN: "User",
                  name: "user",
                  required: true,
                },
                {
                  defaultValue: "",
                  inputType: 'password',
                  labelNameCN: "密码",
                  labelNameEN: "Password",
                  name: "password",
                  required: true,
                },
              ],

              label: "User&Password",
              value: '1',
            },
            {
              label: "NONE",
              value: '2',
              items: [],
            },
          ],
          styles: {
            width: "50%",
          },
        },
        {
          defaultValue: "",
          inputType: 'input',
          labelNameCN: "数据库",
          labelNameEN: "Database",
          name: "database",
          required: false,
        },
        {
          defaultValue: "jdbc:h2:tcp://localhost:9092",
          inputType: 'input',
          labelNameCN: "URL",
          labelNameEN: "URL",
          name: "url",
          required: true,
        },
      ],
      pattern: /jdbc:h2:tcp:\/\/(.*):(\d+)(\/(\w+))?/,
      template: "jdbc:h2:tcp://{host}:{port}/{database}",
    },
    ssh: sshConfig,
  },
  // SQLSERVER encrypt=true;trustServerCertificate=true;integratedSecurity=false;Trusted_Connection=yes
  {
    type: 'SQLSERVER',
    extendInfo: [
      {
        key: "encrypt",
        value: "false",
      },
      {
        key: "trustServerCertificate",
        value: "true",
      },
      {
        key: "integratedSecurity",
        value: "false",
      },
      {
        key: "Trusted_Connection",
        value: "yes",
      },
    ],
    baseInfo: {
      items: [
        {
          defaultValue: "@localhost",
          inputType: 'input',
          labelNameCN: "名称",
          labelNameEN: "Name",
          name: "alias",
          required: true,
        },
        envItem,
        {
          defaultValue: "localhost",
          inputType: 'input',
          labelNameCN: "主机",
          labelNameEN: "Host",
          name: "host",
          required: true,
          styles: {
            width: "70%",
          },
        },
        {
          defaultValue: "1433",
          inputType: 'input',
          labelNameCN: "端口",
          labelNameEN: "Port",
          name: "port",
          labelTextAlign: "right",
          required: true,
          styles: {
            width: "30%",
            labelWidthEN: "40px",
            labelWidthCN: "40px",
            labelAlign: "right",
          },
        },
        {
          defaultValue: "",
          inputType: 'input',
          labelNameCN: "Instance",
          labelNameEN: "Instance",
          name: "instance",
          required: false,
        },
        {
          defaultValue: '1',
          inputType: 'select',
          labelNameCN: "身份验证",
          labelNameEN: "Authentication",
          name: "authenticationType",
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: "root",
                  inputType: 'input',
                  labelNameCN: "用户名",
                  labelNameEN: "User",
                  name: "user",
                  required: true,
                },
                {
                  defaultValue: "",
                  inputType: 'password',
                  labelNameCN: "密码",
                  labelNameEN: "Password",
                  name: "password",
                  required: true,
                },
              ],

              label: "User&Password",
              value: '1',
            },
            {
              label: "NONE",
              value: '2',
              items: [],
            },
          ],
          styles: {
            width: "50%",
          },
        },
        {
          defaultValue: "",
          inputType: 'input',
          labelNameCN: "数据库",
          labelNameEN: "Database",
          name: "database",
          required: false,
        },
        {
          defaultValue: "jdbc:sqlserver://localhost:1433",
          inputType: 'input',
          labelNameCN: "URL",
          labelNameEN: "URL",
          name: "url",
          required: true,
        },
      ],
      pattern: /jdbc:sqlserver:\/\/(.*):(\d+)(;database=(\w+))?/,
      template: "jdbc:sqlserver://{host}:{port};database={database}",
    },
    ssh: sshConfig,
  },
  // SQLITE
  {
    type: 'SQLITE',
    baseInfo: {
      items: [
        {
          defaultValue: "@localhost",
          inputType: 'input',
          labelNameCN: "名称",
          labelNameEN: "Name",
          name: "alias",
          required: true,
        },
        envItem,
        {
          defaultValue: "identifier.sqlite",
          inputType: 'input',
          labelNameCN: "File",
          labelNameEN: "File",
          name: "file",
          required: true,
        },
        {
          defaultValue: "jdbc:sqlite:identifier.sqlite",
          inputType: 'input',
          labelNameCN: "URL",
          labelNameEN: "URL",
          name: "url",
          required: true,
        },
      ],
      pattern: /jdbc:sqlite:(.*)?/,
      template: "jdbc:sqlite:{file}",
    },
    ssh: sshConfig,
  },
  // MARIADB
  {
    type: 'MARIADB',
    baseInfo: {
      items: [
        {
          defaultValue: "@localhost",
          inputType: 'input',
          labelNameCN: "名称",
          labelNameEN: "Name",
          name: "alias",
          required: true,
        },
        envItem,
        {
          defaultValue: "localhost",
          inputType: 'input',
          labelNameCN: "主机",
          labelNameEN: "Host",
          name: "host",
          required: true,
          styles: {
            width: "70%",
          },
        },
        {
          defaultValue: "3306",
          inputType: 'input',
          labelNameCN: "端口",
          labelNameEN: "Port",
          name: "port",
          labelTextAlign: "right",
          required: true,
          styles: {
            width: "30%",
            labelWidthEN: "40px",
            labelWidthCN: "40px",
            labelAlign: "right",
          },
        },
        {
          defaultValue: '1',
          inputType: 'select',
          labelNameCN: "身份验证",
          labelNameEN: "Authentication",
          name: "authenticationType",
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: "root",
                  inputType: 'input',
                  labelNameCN: "用户名",
                  labelNameEN: "User",
                  name: "user",
                  required: true,
                },
                {
                  defaultValue: "",
                  inputType: 'password',
                  labelNameCN: "密码",
                  labelNameEN: "Password",
                  name: "password",
                  required: true,
                },
              ],

              label: "User&Password",
              value: '1',
            },
            {
              label: "NONE",
              value: '2',
              items: [],
            },
          ],
          styles: {
            width: "50%",
          },
        },
        {
          defaultValue: "",
          inputType: 'input',
          labelNameCN: "数据库",
          labelNameEN: "Database",
          name: "database",
          required: false,
        },
        {
          defaultValue: "jdbc:mariadb://localhost:3306",
          inputType: 'input',
          labelNameCN: "URL",
          labelNameEN: "URL",
          name: "url",
          required: true,
        },
      ],
      pattern: /jdbc:mariadb:\/\/(.*):(\d+)(\/(\w+))?/,
      template: "jdbc:mariadb://{host}:{port}/{database}",
    },
    ssh: sshConfig,
  },
  // CLICKHOUSE
  {
    type: 'CLICKHOUSE',
    baseInfo: {
      items: [
        {
          defaultValue: "@localhost",
          inputType: 'input',
          labelNameCN: "名称",
          labelNameEN: "Name",
          name: "alias",
          required: true,
        },
        envItem,
        {
          defaultValue: "localhost",
          inputType: 'input',
          labelNameCN: "主机",
          labelNameEN: "Host",
          name: "host",
          required: true,
          styles: {
            width: "70%",
          },
        },
        {
          defaultValue: "8123",
          inputType: 'input',
          labelNameCN: "端口",
          labelNameEN: "Port",
          name: "port",
          labelTextAlign: "right",
          required: true,
          styles: {
            width: "30%",
            labelWidthEN: "40px",
            labelWidthCN: "40px",
            labelAlign: "right",
          },
        },
        {
          defaultValue: '1',
          inputType: 'select',
          labelNameCN: "身份验证",
          labelNameEN: "Authentication",
          name: "authenticationType",
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: "root",
                  inputType: 'input',
                  labelNameCN: "用户名",
                  labelNameEN: "User",
                  name: "user",
                  required: true,
                },
                {
                  defaultValue: "",
                  inputType: 'password',
                  labelNameCN: "密码",
                  labelNameEN: "Password",
                  name: "password",
                  required: true,
                },
              ],

              label: "User&Password",
              value: '1',
            },
            {
              label: "NONE",
              value: '2',
              items: [],
            },
          ],
          styles: {
            width: "50%",
          },
        },
        {
          defaultValue: "",
          inputType: 'input',
          labelNameCN: "数据库",
          labelNameEN: "Database",
          name: "database",
          required: false,
        },
        {
          defaultValue: "jdbc:clickhouse://localhost:8123",
          inputType: 'input',
          labelNameCN: "URL",
          labelNameEN: "URL",
          name: "url",
          required: true,
        },
      ],
      pattern: /jdbc:clickhouse:\/\/(.*):(\d+)(\/(\w+))?/,
      template: "jdbc:clickhouse://{host}:{port}/{database}",
      excludes: [
        OperationColumn.ViewDDL,
        OperationColumn.CreateTable,
        OperationColumn.EditTable,
      ],
      //排除掉导出ddl 和 创建表功能 支持的功能见 ./enum.ts => OperationColumn
    },
    ssh: sshConfig,
  },
  // DM
  {
    baseInfo: {
      items: [
        {
          defaultValue: "@localhost",
          inputType: 'input',
          labelNameCN: "名称",
          labelNameEN: "Name",
          name: "alias",
          required: true,
        },
        envItem,
        {
          defaultValue: "localhost",
          inputType: 'input',
          labelNameCN: "主机",
          labelNameEN: "Host",
          name: "host",
          required: true,
          styles: {
            width: "70%",
          },
        },
        {
          defaultValue: "5236",
          inputType: 'input',
          labelNameCN: "端口",
          labelNameEN: "Port",
          name: "port",
          labelTextAlign: "right",
          required: true,
          styles: {
            width: "30%",
            labelWidthEN: "40px",
            labelWidthCN: "40px",
            labelAlign: "right",
          },
        },
        {
          defaultValue: '1',
          inputType: 'select',
          labelNameCN: "身份验证",
          labelNameEN: "Authentication",
          name: "authenticationType",
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: "root",
                  inputType: 'input',
                  labelNameCN: "用户名",
                  labelNameEN: "User",
                  name: "user",
                  required: true,
                },
                {
                  defaultValue: "",
                  inputType: 'password',
                  labelNameCN: "密码",
                  labelNameEN: "Password",
                  name: "password",
                  required: true,
                },
              ],
              label: "User&Password",
              value: '1',
            },
            {
              label: "NONE",
              value: '2',
              items: [],
            },
          ],
          styles: {
            width: "50%",
          },
        },
        {
          defaultValue: "",
          inputType: 'input',
          labelNameCN: "数据库",
          labelNameEN: "Database",
          name: "database",
          required: false,
        },
        {
          defaultValue: "jdbc:dm://localhost:5236",
          inputType: 'input',
          labelNameCN: "URL",
          labelNameEN: "URL",
          name: "url",
          required: true,
        },
      ],
      pattern: /jdbc:dm:\/\/(.*):(\d+)(\/(\w+))?/,
      template: "jdbc:dm://{host}:{port}/{database}",
      // excludes: [OperationColumn.EditTable]
    },
    ssh: sshConfig,
    extendInfo: [
      {
        key: "zeroDateTimeBehavior",
        value: "convertToNull",
      },
    ],
    type: 'DM',
  },
  //DB2
  {
    baseInfo: {
      items: [
        {
          defaultValue: "@localhost",
          inputType: 'input',
          labelNameCN: "名称",
          labelNameEN: "Name",
          name: "alias",
          required: true,
          styles: {
            width: "100%",
          },
        },
        envItem,
        {
          defaultValue: "localhost",
          inputType: 'input',
          labelNameCN: "主机",
          labelNameEN: "Host",
          name: "host",
          required: true,
          styles: {
            width: "70%",
          },
        },
        {
          defaultValue: "50000",
          inputType: 'input',
          labelNameCN: "端口",
          labelNameEN: "Port",
          name: "port",
          labelTextAlign: "right",
          required: true,
          styles: {
            width: "30%",
            labelWidthEN: "40px",
            labelWidthCN: "40px",
            labelAlign: "right",
          },
        },
        {
          defaultValue: '1',
          inputType: 'select',
          labelNameCN: "身份验证",
          labelNameEN: "Authentication",
          name: "authenticationType",
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: "root",
                  inputType: 'input',
                  labelNameCN: "用户名",
                  labelNameEN: "User",
                  name: "user",
                  required: true,
                  styles: {
                    width: "100%",
                  },
                },
                {
                  defaultValue: "",
                  inputType: 'password',
                  labelNameCN: "密码",
                  labelNameEN: "Password",
                  name: "password",
                  required: true,
                  styles: {
                    width: "100%",
                  },
                },
              ],
              label: "User&Password",
              value: '1',
            },
            {
              label: "NONE",
              value: '2',
              items: [],
            },
          ],
          styles: {
            width: "50%",
          },
        },
        {
          defaultValue: "",
          inputType: 'input',
          labelNameCN: "数据库",
          labelNameEN: "Database",
          name: "database",
          required: false,
          styles: {
            width: "100%",
          },
        },
        {
          defaultValue: "jdbc:db2://localhost:50000",
          inputType: 'input',
          labelNameCN: "URL",
          labelNameEN: "URL",
          name: "url",
          required: true,
          styles: {
            width: "100%",
          },
        },
      ],
      pattern: /jdbc:db2:\/\/(.*):(\d+)(\/(\w+))?/,
      template: "jdbc:db2://{host}:{port}/{database}",
      // excludes: [OperationColumn.EditTable]
    },
    ssh: sshConfig,
    extendInfo: [],
    type: 'DB2',
  },
  //presto
  {
    baseInfo: {
      items: [
        {
          defaultValue: "@localhost",
          inputType: 'input',
          labelNameCN: "名称",
          labelNameEN: "Name",
          name: "alias",
          required: true,
          styles: {
            width: "100%",
          },
        },
        envItem,
        {
          defaultValue: "localhost",
          inputType: 'input',
          labelNameCN: "主机",
          labelNameEN: "Host",
          name: "host",
          required: true,
          styles: {
            width: "70%",
          },
        },
        {
          defaultValue: "8080",
          inputType: 'input',
          labelNameCN: "端口",
          labelNameEN: "Port",
          name: "port",
          labelTextAlign: "right",
          required: true,
          styles: {
            width: "30%",
            labelWidthEN: "40px",
            labelWidthCN: "40px",
            labelAlign: "right",
          },
        },
        {
          defaultValue: '1',
          inputType: 'select',
          labelNameCN: "身份验证",
          labelNameEN: "Authentication",
          name: "authenticationType",
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: "root",
                  inputType: 'input',
                  labelNameCN: "用户名",
                  labelNameEN: "User",
                  name: "user",
                  required: true,
                  styles: {
                    width: "100%",
                  },
                },
                {
                  defaultValue: "",
                  inputType: 'password',
                  labelNameCN: "密码",
                  labelNameEN: "Password",
                  name: "password",
                  required: true,
                  styles: {
                    width: "100%",
                  },
                },
              ],
              label: "User&Password",
              value: '1',
            },
            {
              label: "NONE",
              value: '2',
              items: [],
            },
          ],
          styles: {
            width: "50%",
          },
        },
        {
          defaultValue: "",
          inputType: 'input',
          labelNameCN: "数据库",
          labelNameEN: "Database",
          name: "database",
          required: false,
          styles: {
            width: "100%",
          },
        },
        {
          defaultValue: "jdbc:presto://localhost:8080",
          inputType: 'input',
          labelNameCN: "URL",
          labelNameEN: "URL",
          name: "url",
          required: true,
          styles: {
            width: "100%",
          },
        },
      ],
      pattern: /jdbc:presto:\/\/(.*):(\d+)(\/(\w+))?/,
      template: "jdbc:presto://{host}:{port}/{database}",
      // excludes: [OperationColumn.EditTable]
    },
    ssh: sshConfig,
    extendInfo: [],
    type: 'PRESTO',
  },
  //oceanbase
  {
    baseInfo: {
      items: [
        {
          defaultValue: "@localhost",
          inputType: 'input',
          labelNameCN: "名称",
          labelNameEN: "Name",
          name: "alias",
          required: true,
          styles: {
            width: "100%",
          },
        },
        envItem,
        {
          defaultValue: "localhost",
          inputType: 'input',
          labelNameCN: "主机",
          labelNameEN: "Host",
          name: "host",
          required: true,
          styles: {
            width: "70%",
          },
        },
        {
          defaultValue: "2883",
          inputType: 'input',
          labelNameCN: "端口",
          labelNameEN: "Port",
          name: "port",
          labelTextAlign: "right",
          required: true,
          styles: {
            width: "30%",
            labelWidthEN: "40px",
            labelWidthCN: "40px",
            labelAlign: "right",
          },
        },
        {
          defaultValue: '1',
          inputType: 'select',
          labelNameCN: "身份验证",
          labelNameEN: "Authentication",
          name: "authenticationType",
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: "root",
                  inputType: 'input',
                  labelNameCN: "用户名",
                  labelNameEN: "User",
                  name: "user",
                  required: true,
                  styles: {
                    width: "100%",
                  },
                },
                {
                  defaultValue: "",
                  inputType: 'password',
                  labelNameCN: "密码",
                  labelNameEN: "Password",
                  name: "password",
                  required: true,
                  styles: {
                    width: "100%",
                  },
                },
              ],
              label: "User&Password",
              value: '1',
            },
            {
              label: "NONE",
              value: '2',
              items: [],
            },
          ],
          styles: {
            width: "50%",
          },
        },
        {
          defaultValue: "",
          inputType: 'input',
          labelNameCN: "数据库",
          labelNameEN: "Database",
          name: "database",
          required: false,
          styles: {
            width: "100%",
          },
        },
        {
          defaultValue: "jdbc:oceanbase://localhost:2883",
          inputType: 'input',
          labelNameCN: "URL",
          labelNameEN: "URL",
          name: "url",
          required: true,
          styles: {
            width: "100%",
          },
        },
      ],
      pattern: /jdbc:oceanbase:\/\/(.*):(\d+)(\/(\w+))?/,
      template: "jdbc:oceanbase://{host}:{port}/{database}",
      // excludes: [OperationColumn.EditTable]
    },
    ssh: sshConfig,
    extendInfo: [],
    type: 'OCEANBASE',
  },
  //redis
  {
    baseInfo: {
      items: [
        {
          defaultValue: "@localhost",
          inputType: 'input',
          labelNameCN: "名称",
          labelNameEN: "Name",
          name: "alias",
          required: true,
          styles: {
            width: "100%",
          },
        },
        envItem,
        {
          defaultValue: "localhost",
          inputType: 'input',
          labelNameCN: "主机",
          labelNameEN: "Host",
          name: "host",
          required: true,
          styles: {
            width: "70%",
          },
        },
        {
          defaultValue: "6379",
          inputType: 'input',
          labelNameCN: "端口",
          labelNameEN: "Port",
          name: "port",
          labelTextAlign: "right",
          required: true,
          styles: {
            width: "30%",
            labelWidthEN: "40px",
            labelWidthCN: "40px",
            labelAlign: "right",
          },
        },
        {
          defaultValue: '1',
          inputType: 'select',
          labelNameCN: "身份验证",
          labelNameEN: "Authentication",
          name: "authenticationType",
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: "root",
                  inputType: 'input',
                  labelNameCN: "用户名",
                  labelNameEN: "User",
                  name: "user",
                  required: true,
                  styles: {
                    width: "100%",
                  },
                },
                {
                  defaultValue: "",
                  inputType: 'password',
                  labelNameCN: "密码",
                  labelNameEN: "Password",
                  name: "password",
                  required: true,
                  styles: {
                    width: "100%",
                  },
                },
              ],
              label: "User&Password",
              value: '1',
            },
            {
              label: "NONE",
              value: '2',
              items: [],
            },
          ],
          styles: {
            width: "50%",
          },
        },
        {
          defaultValue: "",
          inputType: 'input',
          labelNameCN: "数据库",
          labelNameEN: "Database",
          name: "database",
          required: false,
          styles: {
            width: "100%",
          },
        },
        {
          defaultValue: "jdbc:redis://localhost:6379",
          inputType: 'input',
          labelNameCN: "URL",
          labelNameEN: "URL",
          name: "url",
          required: true,
          styles: {
            width: "100%",
          },
        },
      ],
      pattern: /jdbc:redis:\/\/(.*):(\d+)(\/(\w+))?/,
      template: "jdbc:redis://{host}:{port}/{database}",
    },
    ssh: sshConfig,
    extendInfo: [],
    type: 'REDIS',
  },
  //hive
  {
    baseInfo: {
      items: [
        {
          defaultValue: "@localhost",
          inputType: 'input',
          labelNameCN: "名称",
          labelNameEN: "Name",
          name: "alias",
          required: true,
          styles: {
            width: "100%",
          },
        },
        envItem,
        {
          defaultValue: "localhost",
          inputType: 'input',
          labelNameCN: "主机",
          labelNameEN: "Host",
          name: "host",
          required: true,
          styles: {
            width: "70%",
          },
        },
        {
          defaultValue: "10000",
          inputType: 'input',
          labelNameCN: "端口",
          labelNameEN: "Port",
          name: "port",
          labelTextAlign: "right",
          required: true,
          styles: {
            width: "30%",
            labelWidthEN: "40px",
            labelWidthCN: "40px",
            labelAlign: "right",
          },
        },
        {
          defaultValue: '1',
          inputType: 'select',
          labelNameCN: "身份验证",
          labelNameEN: "Authentication",
          name: "authenticationType",
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: "root",
                  inputType: 'input',
                  labelNameCN: "用户名",
                  labelNameEN: "User",
                  name: "user",
                  required: true,
                  styles: {
                    width: "100%",
                  },
                },
                {
                  defaultValue: "",
                  inputType: 'password',
                  labelNameCN: "密码",
                  labelNameEN: "Password",
                  name: "password",
                  required: true,
                  styles: {
                    width: "100%",
                  },
                },
              ],
              label: "User&Password",
              value: '1',
            },
            {
              label: "NONE",
              value: '2',
              items: [],
            },
          ],
          styles: {
            width: "50%",
          },
        },
        {
          defaultValue: "",
          inputType: 'input',
          labelNameCN: "数据库",
          labelNameEN: "Database",
          name: "database",
          required: false,
          styles: {
            width: "100%",
          },
        },
        {
          defaultValue: "jdbc:hive2://localhost:10000",
          inputType: 'input',
          labelNameCN: "URL",
          labelNameEN: "URL",
          name: "url",
          required: true,
          styles: {
            width: "100%",
          },
        },
      ],
      pattern: /jdbc:hive2:\/\/(.*):(\d+)(\/(\w+))?/,
      template: "jdbc:hive2://{host}:{port}/{database}",
      // excludes: [OperationColumn.EditTable]
    },
    ssh: sshConfig,
    extendInfo: [],
    type: 'HIVE',
  },
  //KINGBASE
  {
    baseInfo: {
      items: [
        {
          defaultValue: "@localhost",
          inputType: 'input',
          labelNameCN: "名称",
          labelNameEN: "Name",
          name: "alias",
          required: true,
          styles: {
            width: "100%",
          },
        },
        envItem,
        {
          defaultValue: "localhost",
          inputType: 'input',
          labelNameCN: "主机",
          labelNameEN: "Host",
          name: "host",
          required: true,
          styles: {
            width: "70%",
          },
        },
        {
          defaultValue: "54321",
          inputType: 'input',
          labelNameCN: "端口",
          labelNameEN: "Port",
          name: "port",
          labelTextAlign: "right",
          required: true,
          styles: {
            width: "30%",
            labelWidthEN: "40px",
            labelWidthCN: "40px",
            labelAlign: "right",
          },
        },
        {
          defaultValue: '1',
          inputType: 'select',
          labelNameCN: "身份验证",
          labelNameEN: "Authentication",
          name: "authenticationType",
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: "root",
                  inputType: 'input',
                  labelNameCN: "用户名",
                  labelNameEN: "User",
                  name: "user",
                  required: true,
                  styles: {
                    width: "100%",
                  },
                },
                {
                  defaultValue: "",
                  inputType: 'password',
                  labelNameCN: "密码",
                  labelNameEN: "Password",
                  name: "password",
                  required: true,
                  styles: {
                    width: "100%",
                  },
                },
              ],
              label: "User&Password",
              value: '1',
            },
            {
              label: "NONE",
              value: '2',
              items: [],
            },
          ],
          styles: {
            width: "50%",
          },
        },
        {
          defaultValue: "",
          inputType: 'input',
          labelNameCN: "数据库",
          labelNameEN: "Database",
          name: "database",
          required: false,
          styles: {
            width: "100%",
          },
        },
        {
          defaultValue: "jdbc:kingbase8://127.0.0.1:54321",
          inputType: 'input',
          labelNameCN: "URL",
          labelNameEN: "URL",
          name: "url",
          required: true,
          styles: {
            width: "100%",
          },
        },
      ],
      pattern: /jdbc:kingbase8:\/\/(.*):(\d+)(\/(\w+))?/,
      template: "jdbc:kingbase8://{host}:{port}/{database}",
      // excludes: [OperationColumn.EditTable]
    },
    ssh: sshConfig,
    extendInfo: [],
    type: 'KINGBASE',
  },
  //MONGODB
  {
    baseInfo: {
      items: [
        {
          defaultValue: "@localhost",
          inputType: 'input',
          labelNameCN: "名称",
          labelNameEN: "Name",
          name: "alias",
          required: true,
          styles: {
            width: "100%",
          },
        },
        envItem,
        {
          defaultValue: "localhost",
          inputType: 'input',
          labelNameCN: "主机",
          labelNameEN: "Host",
          name: "host",
          required: true,
          styles: {
            width: "70%",
          },
        },
        {
          defaultValue: "27017",
          inputType: 'input',
          labelNameCN: "端口",
          labelNameEN: "Port",
          name: "port",
          labelTextAlign: "right",
          required: true,
          styles: {
            width: "30%",
            labelWidthEN: "40px",
            labelWidthCN: "40px",
            labelAlign: "right",
          },
        },
        {
          defaultValue: '1',
          inputType: 'select',
          labelNameCN: "身份验证",
          labelNameEN: "Authentication",
          name: "authenticationType",
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: "root",
                  inputType: 'input',
                  labelNameCN: "用户名",
                  labelNameEN: "User",
                  name: "user",
                  required: true,
                  styles: {
                    width: "100%",
                  },
                },
                {
                  defaultValue: "",
                  inputType: 'password',
                  labelNameCN: "密码",
                  labelNameEN: "Password",
                  name: "password",
                  required: true,
                  styles: {
                    width: "100%",
                  },
                },
              ],
              label: "User&Password",
              value: '1',
            },
            {
              label: "NONE",
              value: '2',
              items: [],
            },
          ],
          styles: {
            width: "50%",
          },
        },
        {
          defaultValue: "",
          inputType: 'input',
          labelNameCN: "数据库",
          labelNameEN: "Database",
          name: "database",
          required: false,
          styles: {
            width: "100%",
          },
        },
        {
          defaultValue: "mongodb://localhost:27017",
          inputType: 'input',
          labelNameCN: "URL",
          labelNameEN: "URL",
          name: "url",
          required: true,
          styles: {
            width: "100%",
          },
        },
      ],
      pattern: /mongodb:\/\/(.*):(\d+)(\/(\w+))?/,
      template: "mongodb://{host}:{port}/{database}",
      excludes: [
        OperationColumn.ViewDDL,
        OperationColumn.CreateTable,
        OperationColumn.EditTable,
      ],
    },
    ssh: sshConfig,
    extendInfo: [],
    type: 'MONGODB',
  },
  // GBASE
  {
    baseInfo: {
      items: [
        {
          defaultValue: "@localhost",
          inputType: 'input',
          labelNameCN: "名称",
          labelNameEN: "Name",
          name: "alias",
          required: true,
        },
        envItem,
        {
          defaultValue: "localhost",
          inputType: 'input',
          labelNameCN: "主机",
          labelNameEN: "Host",
          name: "host",
          required: true,
          styles: {
            width: "70%",
          },
        },
        {
          defaultValue: "3306",
          inputType: 'input',
          labelNameCN: "端口",
          labelNameEN: "Port",
          name: "port",
          labelTextAlign: "right",
          required: true,
          styles: {
            width: "30%",
            labelWidthEN: "40px",
            labelWidthCN: "40px",
            labelAlign: "right",
          },
        },
        {
          defaultValue: '1',
          inputType: 'select',
          labelNameCN: "身份验证",
          labelNameEN: "Authentication",
          name: "authenticationType",
          required: true,
          styles: {
            width: "50%",
          },
          selects: [
            {
              items: [
                {
                  defaultValue: "root",
                  inputType: 'input',
                  labelNameCN: "用户名",
                  labelNameEN: "User",
                  name: "user",
                  required: true,
                },
                {
                  defaultValue: "",
                  inputType: 'password',
                  labelNameCN: "密码",
                  labelNameEN: "Password",
                  name: "password",
                  required: true,
                },
              ],
              label: "User&Password",
              value: '1',
            },
            {
              label: "NONE",
              value: '2',
              items: [],
            },
          ],
        },
        {
          defaultValue: "",
          inputType: 'input',
          labelNameCN: "数据库",
          labelNameEN: "Database",
          name: "database",
          required: false,
        },
        {
          defaultValue: "jdbc:mysql://localhost:3306",
          inputType: 'input',
          labelNameCN: "URL",
          labelNameEN: "URL",
          name: "url",
          required: true,
        },
      ],
      pattern: /jdbc:mysql:\/\/(.*):(\d+)(\/(\w+))?/,
      template: "jdbc:mysql://{host}:{port}/{database}",
    },
    ssh: sshConfig,
    extendInfo: [
      {
        key: "zeroDateTimeBehavior",
        value: "convertToNull",
      },
    ],
    type: 'GBASE',
  },
  // OSCAR
  {
    type: 'OSCAR',
    baseInfo: {
      items: [
        {
          defaultValue: "@localhost",
          inputType: 'input',
          labelNameCN: "名称",
          labelNameEN: "Name",
          name: "alias",
          required: true,
        },
        envItem,
        {
          defaultValue: "localhost",
          inputType: 'input',
          labelNameCN: "主机",
          labelNameEN: "Host",
          name: "host",
          required: true,
          styles: {
            width: "70%",
          },
        },
        {
          defaultValue: "1521",
          inputType: 'input',
          labelNameCN: "端口",
          labelNameEN: "Port",
          name: "port",
          labelTextAlign: "right",
          required: true,
          styles: {
            width: "30%",
            labelWidthEN: "40px",
            labelWidthCN: "40px",
            labelAlign: "right",
          },
        },
        {
          defaultValue: "sid",
          inputType: 'select',
          labelNameCN: "链接类型",
          labelNameEN: "Service type",
          name: "serviceType",
          required: true,
          selects: [
            {
              label: "SID",
              value: "sid",
              items: [
                {
                  defaultValue: "XE",
                  inputType: 'input',
                  labelNameCN: "SID",
                  labelNameEN: "SID",
                  name: "sid",
                  required: true,
                  styles: {
                    width: "70%",
                  },
                },
              ],
              onChange: (data) => {
                data.baseInfo.pattern = /jdbc:oracle:(.*):@(.*):(\d+):(.*)/;
                data.baseInfo.template =
                  "jdbc:oracle:{driver}:@{host}:{port}:{sid}";
                return data;
              },
            },
            {
              label: "Service",
              value: "service",
              items: [
                {
                  defaultValue: "XE",
                  inputType: 'input',
                  labelNameCN: "服务名",
                  labelNameEN: "Service name",
                  name: "serviceName",
                  required: true,
                  styles: {
                    width: "70%",
                  },
                },
              ],
              onChange: (data) => {
                data.baseInfo.pattern =
                  /jdbc:oracle:(.*):@\/\/(.*):(\d+)\/(.*)/;
                data.baseInfo.template =
                  "jdbc:oracle:{driver}:@//{host}:{port}/{serviceName}";
                return data;
              },
            },
          ],
          styles: {
            width: "50%",
          },
        },
        {
          defaultValue: "thin",
          inputType: 'select',
          labelNameCN: "驱动",
          labelNameEN: "Driver",
          name: "driver",
          required: true,
          labelTextAlign: "right",
          selects: [
            {
              value: "THIN",
              label: "thin",
            },
            {
              value: "OCI",
              label: "oci",
            },
            {
              value: "OCI8",
              label: "oci8",
            },
          ],
          styles: {
            width: "30%",
            labelWidthEN: "70px",
            labelWidthCN: "40px",
            labelAlign: "right",
          },
        },
        {
          defaultValue: '1',
          inputType: 'select',
          labelNameCN: "身份验证",
          labelNameEN: "Authentication",
          name: "authenticationType",
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: "root",
                  inputType: 'input',
                  labelNameCN: "用户名",
                  labelNameEN: "User",
                  name: "user",
                  required: true,
                },
                {
                  defaultValue: "",
                  inputType: 'password',
                  labelNameCN: "密码",
                  labelNameEN: "Password",
                  name: "password",
                  required: true,
                },
              ],
              label: "User&Password",
              value: '1',
            },
            {
              label: "NONE",
              value: '2',
              items: [],
            },
          ],
          styles: {
            width: "50%",
          },
        },
        {
          defaultValue: "jdbc:oracle:thin:@localhost:1521:XE",
          inputType: 'input',
          labelNameCN: "URL",
          labelNameEN: "URL",
          name: "url",
          required: true,
        },
      ],
      pattern: /jdbc:oracle:(.*):@(.*):(\d+):(.*)/,
      template: "jdbc:oracle:{driver}:@{host}:{port}:{sid}",
    },
    ssh: sshConfig,
  },
];
