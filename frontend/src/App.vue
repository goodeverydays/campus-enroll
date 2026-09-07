<script setup lang="ts">
import { useRouter } from 'vue-router';
import { useSession } from './session';

const router = useRouter();
const session = useSession();

function logout() {
  session.signOut();
  void router.push('/courses');
}
</script>

<template>
  <div class="app-shell">
    <header class="site-header">
      <RouterLink class="brand" to="/courses" aria-label="CampusEnroll 首页">
        <span class="brand-mark">CE</span>
        <span>
          <strong>CampusEnroll</strong>
          <small>智慧选课中心</small>
        </span>
      </RouterLink>
      <nav class="main-nav" aria-label="主导航">
        <RouterLink to="/courses">课程广场</RouterLink>
        <RouterLink v-if="session.authenticated.value" to="/my-enrollments">我的课程</RouterLink>
      </nav>
      <div class="account-area">
        <div v-if="session.state.profile" class="student-summary">
          <span class="student-avatar">{{ session.state.profile.name.slice(0, 1) }}</span>
          <span><strong>{{ session.state.profile.name }}</strong><small>{{ session.state.profile.studentNo }}</small></span>
          <button class="text-button" type="button" @click="logout">退出</button>
        </div>
        <RouterLink v-else class="button button-secondary button-small" to="/login">登录选课</RouterLink>
      </div>
    </header>
    <main>
      <RouterView />
    </main>
    <footer class="site-footer">
      <span>CampusEnroll · 遗留教务系统增量式现代化</span>
      <span>所有业务请求统一经由 API Gateway</span>
    </footer>
  </div>
</template>
