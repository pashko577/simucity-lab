import { create } from 'zustand';

import { ApiError } from '../api/client';
import { authApi } from '../api/endpoints';
import type { User } from '../api/types';

type SessionStatus = 'unknown' | 'anonymous' | 'authenticated';

interface SessionState {
  status: SessionStatus;
  user: User | null;
  /** Pregunta al backend si hay sesión (la cookie HttpOnly no es legible desde JavaScript). */
  refresh(): Promise<void>;
  login(email: string, password: string): Promise<void>;
  logout(): Promise<void>;
}

export const useSession = create<SessionState>()((set) => ({
  status: 'unknown',
  user: null,

  refresh: async () => {
    try {
      const user = await authApi.me();
      set({ status: 'authenticated', user });
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        set({ status: 'anonymous', user: null });
      } else {
        throw error;
      }
    }
  },

  login: async (email, password) => {
    const user = await authApi.login(email, password);
    set({ status: 'authenticated', user });
  },

  logout: async () => {
    await authApi.logout();
    set({ status: 'anonymous', user: null });
  },
}));
