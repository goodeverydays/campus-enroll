<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ApiError, api } from '../api';
import StatusChip from '../components/StatusChip.vue';
import type { CourseDetail, Enrollment } from '../types';

interface EnrollmentView extends Enrollment { course?: CourseDetail }

const items = ref<EnrollmentView[]>([]);
const loading = ref(true);
const droppingCourseId = ref<number | null>(null);
const message = ref('');
const errorMessage = ref('');

async function load() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const enrollments = await api.getEnrollments();
    const courses = new Map<number, CourseDetail>();
    await Promise.all([...new Set(enrollments.map((item) => item.courseId))].map(async (courseId) => {
      courses.set(courseId, await api.getCourse(courseId));
    }));
    items.value = enrollments.map((item) => ({ ...item, course: courses.get(item.courseId) }));
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '已选课程加载失败';
  } finally {
    loading.value = false;
  }
}

async function drop(item: EnrollmentView) {
  if (!window.confirm(`确认退选“${item.course?.name || item.courseId}”吗？`)) return;
  droppingCourseId.value = item.courseId;
  message.value = '';
  errorMessage.value = '';
  try {
    const result = await api.drop(item.courseId);
    if (result.status === 'FAILED') {
      throw new ApiError(result.failureMessage || '退课失败', 409, undefined, result.requestId);
    }
    message.value = `退课成功，请求号 ${result.requestId}`;
    await load();
  } catch (error) {
    const requestId = error instanceof ApiError && error.requestId ? `（请求 ${error.requestId}）` : '';
    errorMessage.value = `${error instanceof Error ? error.message : '退课失败'}${requestId}`;
  } finally {
    droppingCourseId.value = null;
  }
}

onMounted(load);
</script>

<template>
  <section class="page-container content-section">
    <div class="section-heading large-heading">
      <div><p class="eyebrow">My enrollments</p><h1>我的课程</h1><p>这里展示已正式落库的选课结果。</p></div>
      <RouterLink class="button button-secondary" to="/courses">继续选课</RouterLink>
    </div>
    <p v-if="message" class="alert alert-success">{{ message }}</p>
    <p v-if="errorMessage" class="alert alert-danger">{{ errorMessage }}</p>
    <div v-if="loading" class="detail-loading">正在读取个人课程…</div>
    <div v-else-if="items.length" class="enrollment-list">
      <article v-for="item in items" :key="item.id" class="enrollment-row">
        <div class="enrollment-course">
          <span class="course-code">{{ item.course?.code || `COURSE-${item.courseId}` }}</span>
          <div><h3>{{ item.course?.name || '课程信息加载中' }}</h3><p>{{ item.course?.credits }} 学分 · 教学班 {{ item.offeringId }}</p></div>
        </div>
        <div class="enrollment-meta"><StatusChip :status="item.status" /><span>选课时间 {{ new Date(item.enrolledAt).toLocaleString('zh-CN') }}</span></div>
        <button class="button button-danger" type="button" :disabled="droppingCourseId === item.courseId" @click="drop(item)">
          {{ droppingCourseId === item.courseId ? '正在退课…' : '退选' }}
        </button>
      </article>
    </div>
    <div v-else class="empty-state"><strong>还没有已选课程</strong><p>前往课程广场，选择适合你的教学班。</p><RouterLink class="button button-primary" to="/courses">浏览课程</RouterLink></div>
  </section>
</template>
