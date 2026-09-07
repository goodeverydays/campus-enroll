import { createRouter, createWebHistory } from 'vue-router';
import { useSession } from './session';
import CoursesPage from './pages/CoursesPage.vue';
import CourseDetailPage from './pages/CourseDetailPage.vue';
import LoginPage from './pages/LoginPage.vue';
import MyEnrollmentsPage from './pages/MyEnrollmentsPage.vue';
import NotFoundPage from './pages/NotFoundPage.vue';

export const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', redirect: '/courses' },
    { path: '/login', component: LoginPage },
    { path: '/sso', component: LoginPage },
    { path: '/courses', component: CoursesPage },
    { path: '/courses/:courseId', component: CourseDetailPage },
    { path: '/my-enrollments', component: MyEnrollmentsPage, meta: { requiresAuth: true } },
    { path: '/:pathMatch(.*)*', component: NotFoundPage },
  ],
});

router.beforeEach((to) => {
  const { authenticated } = useSession();
  if (to.meta.requiresAuth && !authenticated.value) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  if (to.path === '/login' && authenticated.value) {
    return '/courses';
  }
  return true;
});
