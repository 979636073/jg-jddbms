<template>
  <div class="login">
    <div class="login_left">
      <div class="login_left_title">
        <h3>数据库管理系统</h3>
        <span>Version 1.0.0.0</span>
      </div>
      <img src="@/assets/login/loginBG.png" alt="" />
    </div>
    <el-form
      ref="loginForm"
      label-position="top"
      :model="loginForm"
      :rules="loginRules"
      class="login-form"
    >
      <h3 class="title">欢迎登录</h3>
      <el-form-item prop="username">
        <span slot="label">用户名</span>
        <el-input
          v-model="loginForm.username"
          type="text"
          auto-complete="off"
          placeholder="用户名"
        >
          <svg-icon
            slot="prefix"
            icon-class="user"
            class="el-input__icon input-icon"
          />
        </el-input>
      </el-form-item>
      <el-form-item prop="password">
        <span slot="label">密码</span>
        <el-input
          :key="passwordType"
          v-model="loginForm.password"
          :type="passwordType"
          auto-complete="off"
          placeholder="密码"
          name="password"
          tabindex="2"
          autocomplete="on"
          ref="password"
          @keyup.enter.native="handleLogin"
        >
          <svg-icon slot="prefix" icon-class="password" class="el-input__icon input-icon" />
          <span class="show-pwd" @click="showPwd" slot="suffix">
            <svg-icon :icon-class="passwordType === 'password' ? 'eye' : 'eye-open'"/>
          </span> 
        </el-input>
      </el-form-item>
      <!-- <el-form-item prop="code" v-if="captchaEnabled">
        <span slot="label">验证码</span>
        <el-input
          v-model="loginForm.code"
          auto-complete="off"
          placeholder="验证码"
          style="width: 63%"
          @keyup.enter.native="handleLogin"
        >
          <svg-icon slot="prefix" icon-class="validCode" class="el-input__icon input-icon" />
        </el-input>
        <div class="login-code">
          <img :src="codeUrl" @click="getCode" class="login-code-img"/>
        </div>
      </el-form-item> -->
      <el-checkbox
        v-model="loginForm.rememberMe"
        style="margin: 0px 0px 25px 0px"
        >记住密码</el-checkbox
      >
      <el-form-item style="width: 100%">
        <el-button
          :loading="loading"
          size="medium"
          type="primary"
          style="width: 100%"
          @click.native.prevent="handleLogin"
        >
          <span v-if="!loading">登 录</span>
          <span v-else>登 录 中...</span>
        </el-button>
        <div style="float: right" v-if="register">
          <router-link class="link-type" :to="'/register'"
            >立即注册</router-link
          >
        </div>
      </el-form-item>
    </el-form>
  </div>
</template>

<script>
import { getCodeImg } from "@/api/login";
import Cookies from "js-cookie";
import { encrypt, decrypt } from "@/utils/jsencrypt";

export default {
  name: "Login",
  data() {
    return {
      passwordType: "password",
      codeUrl: "",
      loginForm: {
        username: "admin",
        password: "admin123",
        rememberMe: false,
        code: "",
        uuid: "",
      },
      loginRules: {
        username: [
          { required: true, trigger: "blur", message: "请输入您的账号" },
        ],
        password: [
          { required: true, trigger: "blur", message: "请输入您的密码" },
        ],
        code: [{ required: true, trigger: "change", message: "请输入验证码" }],
      },
      loading: false,
      // 验证码开关
      captchaEnabled: true,
      // 注册开关
      register: false,
      flag: false,
    };
  },
  created() {
    // this.getCode();
    this.getCookie();
  },
  methods: {
    showPwd() {
      if (this.passwordType === "password") {
        this.passwordType = "";
      } else {
        this.passwordType = "password";
      }
      this.$nextTick(() => {
        this.$refs.password.focus();
      });
    },
    getCookie() {
      const username = Cookies.get("username");
      const password = Cookies.get("password");
      const rememberMe = Cookies.get("rememberMe");
      this.loginForm = {
        username: username === undefined ? this.loginForm.username : username,
        password:
          password === undefined ? this.loginForm.password : decrypt(password),
        rememberMe: rememberMe === undefined ? false : Boolean(rememberMe),
      };
    },
    handleLogin() {
      this.$refs.loginForm.validate((valid) => {
        if (valid) {
          localStorage.clear();
          this.loading = true;
          if (this.loginForm.rememberMe) {
            Cookies.set("username", this.loginForm.username, { expires: 30 });
            Cookies.set("password", encrypt(this.loginForm.password), {
              expires: 30,
            });
            Cookies.set("rememberMe", this.loginForm.rememberMe, {
              expires: 30,
            });
          } else {
            Cookies.remove("username");
            Cookies.remove("password");
            Cookies.remove("rememberMe");
          }
          this.$store
            .dispatch("Login", this.loginForm)
            .then((res) => {
              if (!res.success && !res.code) {
                this.loading = false;
                return this.$message.error(res.errorMessage);
              }
              this.$router.push({ path: "/workspace" });
            })
            .catch((err) => {
              console.log(err);
              this.loading = false;
            });
          }
      });
    },
  },
};
</script>

<style rel="stylesheet/scss" lang="scss">
.login {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  background-image: url("../assets/login/bj.png");
  background-size: cover;
  .login_left {
    margin-right: 100px;
    .login_left_title {
      color: #2a62ff;
      margin-bottom: 20px;
      h3 {
        font-size: 46px;
        font-weight: bold;
        letter-spacing: 4px;
      }
      span {
        font-size: 12px;
      }
    }
  }
}

.login-form {
  width: 464px;
  height: 480px;
  background: #ffffff;
  box-shadow: 0 0 15px 1px rgba(217, 222, 230, 0.2);
  border-radius: 50px 10px 50px 10px;
  padding: 47px 42px 5px 42px;
  .title {
    text-align: center;
    font-size: 28px;
    color: #333333;
    margin-bottom: 25px;
  }
  .el-form-item {
    margin-bottom: 10px;
  }
  .el-form--label-top .el-form-item__label {
    padding: 0;
  }
  .el-input {
    height: 38px;
    input {
      height: 38px;
    }
  }
  .input-icon {
    height: 39px;
    width: 14px;
    margin-left: 2px;
  }
}
.login-tip {
  font-size: 13px;
  text-align: center;
  color: #bfbfbf;
}
.login-code {
  width: 33%;
  height: 38px;
  float: right;
  img {
    cursor: pointer;
    vertical-align: middle;
  }
}
.login-code-img {
  height: 38px;
}
  .show-pwd {
    position: absolute;
    right: 10px;
    top: 3px;
    font-size: 16px;
    color: #889aa4;
    cursor: pointer;
    user-select: none;
  }
</style>
