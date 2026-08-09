export const ROUTES = {
  home: "/",
  register: "/register",
  login: "/login",
  profile: "/profile",
  shareProfile: "/profile/share",
  settings: "/settings",
  publicProfile: (slug: string) => `/u/${slug}`,
} as const;
