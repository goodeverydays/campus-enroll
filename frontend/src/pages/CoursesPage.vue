<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ApiError, api } from '../api';
import StatusChip from '../components/StatusChip.vue';
import type { CourseSummary, PageResponse, Semester } from '../types';

const route = useRoute();
const router = useRouter();
const keyword = ref(typeof route.query.keyword === 'string' ? route.query.keyword : '');
const semesterId = ref(typeof route.query.semesterId === 'string' ? Number(route.query.semesterId) : undefined);
const semesters = ref<Semester[]>([]);
const courses = ref<PageResponse<CourseSummary>>({ items: [], page: 0, size: 12, totalElements: 0, totalPages: 0 });
const loading = ref(true);
const errorMessage = ref('');

async function load(page = 0) {
  loading.value = true;
  errorMessage.value = '';
  try {
    courses.value = await api.getCourses({ keyword: keyword.value.trim(), semesterId: semesterId.value, page, size: 12 });
    await router.replace({ query: { keyword: keyword.value.trim() || undefined, semesterId: semesterId.value } });
  } catch (error) {
    errorMessage.value = error instanceof ApiError && error.requestId
      ? `${error.message}（请求 ${error.requestId}）`
      : error instanceof Error ? error.message : '课程加载失败';
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  try {
    semesters.value = await api.getSemesters();
  } catch {
    semesters.value = [];
  }
  await load(Number(route.query.page || 0));
});
</script>

<template>
  <section class="hero-band">
    <div class="page-container hero-content">
      <div>
        <p class="eyebrow">2026 智慧教学服务</p>
        <h1>找到合适的课程，<br /><em>把名额留给热爱。</em></h1>
        <p>统一查看课程、教学班和剩余容量；选课请求异步处理，结果全程可追踪。</p>
      </div>
      <div class="hero-metric">
        <strong>{{ courses.totalElements }}</strong>
        <span>门可选课程</span>
        <small>数据实时来自 Course Service</small>
      </div>
    </div>
  </section>

  <section class="page-container content-section">
    <form class="filter-bar" @submit.prevent="load(0)">
      <label class="search-field">
        <span class="sr-only">搜索课程</span>
        <input v-model="keyword" type="search" maxlength="100" placeholder="搜索课程名称或课程代码" />
      </label>
      <label>
        <span class="sr-only">选择学期</span>
        <select v-model="semesterId">
          <option :value="undefined">全部学期</option>
          <option v-for="semester in semesters" :key="semester.id" :value="semester.id">
            {{ semester.name }} · {{ semester.status }}
          </option>
        </select>
      </label>
      <button class="button button-primary" type="submit">查询课程</button>
    </form>

    <div class="section-heading">
      <div><p class="eyebrow">Course catalog</p><h2>课程广场</h2></div>
      <span class="muted">共 {{ courses.totalElements }} 条结果</span>
    </div>

    <p v-if="errorMessage" class="alert alert-danger" role="alert">{{ errorMessage }}</p>
    <div v-if="loading" class="course-grid" aria-label="正在加载课程">
      <div v-for="index in 6" :key="index" class="course-card skeleton-card" />
    </div>
    <div v-else-if="courses.items.length" class="course-grid">
      <RouterLink v-for="course in courses.items" :key="course.id" class="course-card" :to="`/courses/${course.id}`">
        <div class="course-card-top">
          <span class="course-code">{{ course.code }}</span>
          <StatusChip :status="course.status" />
        </div>
        <div>
          <h3>{{ course.name }}</h3>
          <p>{{ course.credits }} 学分 · {{ course.totalHours }} 学时</p>
        </div>
        <span class="course-link">查看教学班 <span aria-hidden="true">→</span></span>
      </RouterLink>
    </div>
    <div v-else class="empty-state">
      <strong>没有找到匹配的课程</strong>
      <p>换一个关键词或清除学期筛选后再试。</p>
    </div>

    <div v-if="courses.totalPages > 1" class="pagination">
      <button class="button button-secondary" :disabled="courses.page === 0" @click="load(courses.page - 1)">上一页</button>
      <span>第 {{ courses.page + 1 }} / {{ courses.totalPages }} 页</span>
      <button class="button button-secondary" :disabled="courses.page + 1 >= courses.totalPages" @click="load(courses.page + 1)">下一页</button>
    </div>
  </section>
</template>
