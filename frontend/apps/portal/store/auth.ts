import { create } from 'zustand';
import type { UserInfo } from '@lazyday/types';

interface AuthState {
  user: UserInfo | null;
  tenantId: number | null;
  isAuthenticated: boolean;
  setUser: (user: UserInfo) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  tenantId: null,
  isAuthenticated: false,
  setUser: (user) =>
    set({
      user,
      tenantId: user.tenant_id ?? null,
      isAuthenticated: true,
    }),
  logout: () =>
    set({
      user: null,
      tenantId: null,
      isAuthenticated: false,
    }),
}));
