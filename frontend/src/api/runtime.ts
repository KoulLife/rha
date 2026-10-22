export const mocksEnabled = import.meta.env.DEV && import.meta.env.VITE_ENABLE_MOCKS === "true";

export const currentTime = () => (mocksEnabled ? "2026-10-13T03:00:00Z" : new Date().toISOString());
export const todayInSeoul = () =>
  new Date(Date.parse(currentTime()) + 9 * 3600000).toISOString().slice(0, 10);
export const yesterdayInSeoul = () =>
  new Date(Date.parse(`${todayInSeoul()}T00:00:00Z`) - 86400000).toISOString().slice(0, 10);
