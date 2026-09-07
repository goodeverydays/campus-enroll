import { computed, reactive } from 'vue';
import { api, getAccessToken, setAccessToken } from './api';
import type { StudentProfile } from './types';

const state = reactive<{
  token: string | null;
  profile: StudentProfile | null;
  loading: boolean;
}>({
  token: getAccessToken(),
  profile: null,
  loading: false,
});

async function refreshProfile(): Promise<void> {
  if (!state.token) {
    state.profile = null;
    return;
  }
  state.loading = true;
  try {
    state.profile = await api.getProfile();
  } catch (error) {
    signOut();
    throw error;
  } finally {
    state.loading = false;
  }
}

async function signInWithTicket(ticket: string): Promise<void> {
  const token = await api.exchangeTicket(ticket.trim());
  setAccessToken(token.accessToken);
  state.token = token.accessToken;
  await refreshProfile();
}

function signOut(): void {
  setAccessToken(null);
  state.token = null;
  state.profile = null;
}

export function useSession() {
  return {
    state,
    authenticated: computed(() => Boolean(state.token)),
    refreshProfile,
    signInWithTicket,
    signOut,
  };
}
