export type ThemePreference = 'LIGHT' | 'DARK';

export interface User {
  id: string;
  email: string;
  displayName: string;
  timezone: string;
  themePreference: ThemePreference;
}

export interface LoginRequest {
  email: string;
  password: string;
  rememberMe: boolean;
  timezone: string;
}

export interface RegisterRequest {
  displayName: string;
  email: string;
  password: string;
  timezone: string;
}
