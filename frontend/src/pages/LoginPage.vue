<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ApiError } from '../api';
import { useSession } from '../session';

const route = useRoute();
const router = useRouter();
const session = useSession();
const ticket = ref(typeof route.query.ticket === 'string' ? route.query.ticket : '');
const submitting = ref(false);
const errorMessage = ref('');

async function exchange() {
  if (!ticket.value.trim() || submitting.value) return;
  submitting.value = true;
  errorMessage.value = '';
  try {
    await session.signInWithTicket(ticket.value);
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/courses';
    await router.replace(redirect.startsWith('/') ? redirect : '/courses');
  } catch (error) {
    const requestId = error instanceof ApiError && error.requestId ? `（请求 ${error.requestId}）` : '';
    errorMessage.value = `${error instanceof Error ? error.message : '登录失败'}${requestId}`;
  } finally {
    submitting.value = false;
  }
}

onMounted(() => {
  if (ticket.value) void exchange();
});
</script>

<template>
  <section class="auth-page page-container">
    <div class="auth-story">
      <p class="eyebrow">统一身份入口</p>
      <h1>从原教务系统，一步进入选课</h1>
      <p>CampusEnroll 不保存你的旧系统密码。一次性 Ticket 完成兑换后，只在本机保存短期访问令牌。</p>
      <ol class="auth-steps">
        <li><span>01</span>在学校教务系统完成登录</li>
        <li><span>02</span>点击“进入智慧选课”获取一次性 Ticket</li>
        <li><span>03</span>安全兑换并进入课程广场</li>
      </ol>
    </div>
    <form class="auth-card" @submit.prevent="exchange">
      <div>
        <p class="eyebrow">SSO Ticket</p>
        <h2>验证登录凭证</h2>
        <p class="muted">正常情况下，Ticket 会由旧教务系统自动附加到跳转链接。</p>
      </div>
      <label>
        一次性 Ticket
        <textarea v-model="ticket" rows="4" autocomplete="one-time-code" placeholder="粘贴开发环境签发的 Ticket" />
      </label>
      <p v-if="errorMessage" class="alert alert-danger" role="alert">{{ errorMessage }}</p>
      <button class="button button-primary" type="submit" :disabled="submitting || !ticket.trim()">
        {{ submitting ? '正在验证…' : '验证并进入' }}
      </button>
      <RouterLink class="back-link" to="/courses">暂不登录，先浏览课程</RouterLink>
    </form>
  </section>
</template>
