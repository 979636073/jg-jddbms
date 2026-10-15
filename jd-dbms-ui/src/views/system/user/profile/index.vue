<template>
  <div class="app-container profile-page">
    <div class="profile-page__header">
      <h2>个人中心</h2>
      <p>查看当前账号信息并修改登录密码。头像由平台用户统一维护。</p>
    </div>
    <el-row :gutter="20" class="profile-page__content">
      <el-col :xs="24" :sm="24" :md="8">
        <el-card class="profile-page__card" shadow="never">
          <div slot="header" class="profile-page__card-title">个人信息</div>
          <div class="profile-page__identity">
            <img :src="avatarUrl(user.avatar)" alt="用户头像" class="profile-page__avatar" />
            <strong>{{ user.userName || '—' }}</strong>
            <span>{{ roleGroup || '暂无角色' }}</span>
          </div>
          <div class="profile-page__detail">
            <span>用户名称</span><strong>{{ user.userName || '—' }}</strong>
          </div>
          <div class="profile-page__detail">
            <span>所属角色</span><strong>{{ roleGroup || '—' }}</strong>
          </div>
          <div class="profile-page__detail">
            <span>创建日期</span><strong>{{ user.createTime || '—' }}</strong>
          </div>
          <div class="profile-page__detail">
            <span>前端版本</span><strong>{{ buildVersion }}</strong>
          </div>
          <router-link v-if="canManageUsers" to="/access/user" class="profile-page__manage-link">
            前往平台用户维护头像 <i class="el-icon-arrow-right" />
          </router-link>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="24" :md="16">
        <el-card class="profile-page__card" shadow="never">
          <div slot="header" class="profile-page__card-title">修改密码</div>
          <resetPwd />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import resetPwd from "./resetPwd";
import { getUserProfile } from "@/api/system/user";
import { avatarUrl } from "@/utils/avatar";

export default {
  name: "Profile",
  components: { resetPwd },
  data() {
    return {
      user: {},
      buildVersion: process.env.VUE_APP_BUILD_VERSION,
      roleGroup: ""
    };
  },
  computed: {
    canManageUsers() {
      const permissions = this.$store.getters.permissions || [];
      return permissions.includes('*:*:*') || permissions.includes('system:user:edit');
    }
  },
  created() {
    this.getUser();
  },
  methods: {
    avatarUrl,
    getUser() {
      getUserProfile().then(response => {
        this.user = response.data;
        this.roleGroup = response.roleGroup;
      });
    }
  }
};
</script>
<style scoped>
.profile-page { max-width: 1280px; margin: 0 auto; }
.profile-page__header { margin-bottom: 20px; }
.profile-page__header h2 { margin: 0 0 8px; color: #1f2937; font-size: 22px; }
.profile-page__header p { margin: 0; color: #667085; font-size: 14px; line-height: 1.6; }
.profile-page__content > .el-col { margin-bottom: 20px; }
.profile-page__card { min-height: 100%; border-color: #e5eaf0; border-radius: 10px; }
.profile-page__card-title { font-size: 16px; font-weight: 600; color: #1f2937; }
.profile-page__identity { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 4px 0 20px; }
.profile-page__identity strong { color: #1f2937; font-size: 17px; }
.profile-page__identity span { color: #667085; font-size: 13px; }
.profile-page__avatar { width: 88px; height: 88px; margin-bottom: 4px; border-radius: 50%; object-fit: cover; }
.profile-page__detail { display: flex; justify-content: space-between; gap: 16px; padding: 13px 0; border-top: 1px solid #eef1f5; font-size: 14px; }
.profile-page__detail span { flex: none; color: #667085; }
.profile-page__detail strong { min-width: 0; color: #344054; font-weight: 500; overflow-wrap: anywhere; text-align: right; }
.profile-page__manage-link { display: block; margin-top: 18px; padding: 12px; border-radius: 7px; background: #ecf4ff; color: #1769d1; text-align: center; font-size: 14px; }
.profile-page__manage-link:hover { background: #e1eeff; color: #0f56b1; }
</style>
