#服务部署，
#1、配置dm按照路径下的环境变量
cd /root/
vim source .bash_profile

export LD_LIBRARY_PATH="$LD_LIBRARY_PATH:/opt/dmdbms/bin"
export DM_HOME="/opt/dmdbms"
export PATH=$PATH:$DM_HOME/bin:$DM_HOME/tools

#启用生效
source .bash_profile
#2、配置文件目录权限
chmod -R 777 /home/dmdbms/bin
