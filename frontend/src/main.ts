import { createApp } from 'vue';
import App from './App.vue';
import { router } from './router';
import { useSession } from './session';
import './styles.css';

const app = createApp(App);
app.use(router);
app.mount('#app');

const session = useSession();
if (session.authenticated.value) {
  void session.refreshProfile().catch(() => undefined);
}
