<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ApiError, api, waitForEnrollmentRequest } from '../api';
import StatusChip from '../components/StatusChip.vue';
import { useSession } from '../session';
import type { CourseDetail, CourseOfferingDetail } from '../types';

const weekdays = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日'];
const route = useRoute();
const router = useRouter();
const session = useSession();
const courseId = Number(route.params.courseId);
const course = ref<CourseDetail | null>(null);
const offerings = ref<CourseOfferingDetail[]>([]);
const loading = ref(true);
const actionCourseId = ref<number | null>(null);
const message = ref('');
const errorMessage = ref('');
const openOfferings = computed(() => offerings.value.filter(({ offering }) => offering.status === 'OPEN'));

function scheduleText(detail: CourseOfferingDetail): string {
  if (!detail.schedules.length) return '时间待定';
  return detail.schedules.map((item) => `${weekdays[item.dayOfWeek]} 第${item.startSection}-${item.endSection}节 · ${item.location} · ${item.startWeek}-${item.endWeek}周`).join('；');
}

async function load() {
  if (!Number.isSafeInteger(courseId) || courseId <= 0) {
    errorMessage.value = '无效的课程编号';
    loading.value = false;
    return;
  }
  loading.value = true;
  errorMessage.value = '';
  try {
    course.value = await api.getCourse(courseId);
    const summaries = await api.getOfferings(courseId);
    offerings.value = await Promise.all(summaries.map((item) => api.getOffering(item.id)));
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '课程详情加载失败';
  } finally {
    loading.value = false;
  }
}

async function enroll() {
  if (!session.authenticated.value) {
    await router.push({ path: '/login', query: { redirect: route.fullPath } });
    return;
  }
  actionCourseId.value = courseId;
  errorMessage.value = '';
  message.value = '';
  try {
    const accepted = await api.enroll(courseId);
    const result = await waitForEnrollmentRequest(accepted);
    if (result.status === 'FAILED') {
      throw new ApiError(result.failureMessage || '选课失败', 409, undefined, result.requestId);
    }
    message.value = `选课成功，请求号 ${result.requestId}`;
    await load();
  } catch (error) {
    const requestId = error instanceof ApiError && error.requestId ? `（请求 ${error.requestId}）` : '';
    errorMessage.value = `${error instanceof Error ? error.message : '选课失败'}${requestId}`;
  } finally {
    actionCourseId.value = null;
  }
}

onMounted(load);
</script>

<template>
  <section class="page-container detail-page">
    <RouterLink class="back-link" to="/courses">← 返回课程广场</RouterLink>
    <div v-if="loading" class="detail-loading">正在加载课程详情…</div>
    <p v-else-if="errorMessage && !course" class="alert alert-danger">{{ errorMessage }}</p>
    <template v-else-if="course">
      <div class="detail-hero">
        <div>
          <div class="course-card-top"><span class="course-code">{{ course.code }}</span><StatusChip :status="course.status" /></div>
          <h1>{{ course.name }}</h1>
          <p>{{ course.credits }} 学分 · {{ course.totalHours }} 学时 · 院系编号 {{ course.departmentId || '未设置' }}</p>
        </div>
        <div class="detail-summary"><strong>{{ openOfferings.length }}</strong><span>个开放教学班</span></div>
      </div>

      <p v-if="message" class="alert alert-success" role="status">{{ message }}</p>
      <p v-if="errorMessage" class="alert alert-danger" role="alert">{{ errorMessage }}</p>

      <div class="section-heading"><div><p class="eyebrow">Available sections</p><h2>教学班与余量</h2></div></div>
      <div v-if="offerings.length" class="offering-list">
        <article v-for="detail in offerings" :key="detail.offering.id" class="offering-card">
          <div class="offering-main">
            <div><span class="course-code">{{ detail.offering.semesterName }} · {{ detail.offering.sectionNo }}班</span><h3>{{ detail.offering.teacherName }}</h3></div>
            <StatusChip :status="detail.offering.status" />
          </div>
          <p class="schedule-line">{{ scheduleText(detail) }}</p>
          <div class="capacity-row">
            <div class="capacity-copy"><strong>{{ detail.offering.remainingCount }}</strong><span>/ {{ detail.offering.capacity }} 个剩余名额</span></div>
            <div class="capacity-track" aria-hidden="true"><span :style="{ width: `${Math.max(0, detail.offering.remainingCount / detail.offering.capacity * 100)}%` }" /></div>
          </div>
          <button class="button button-primary" type="button" :disabled="detail.offering.status !== 'OPEN' || detail.offering.remainingCount < 1 || actionCourseId === courseId" @click="enroll">
            {{ actionCourseId === courseId ? '正在确认结果…' : detail.offering.remainingCount < 1 ? '名额已满' : '提交选课' }}
          </button>
        </article>
      </div>
      <div v-else class="empty-state"><strong>暂无教学班</strong><p>该课程当前没有可展示的开课安排。</p></div>
    </template>
  </section>
</template>
