import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./globals.css";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { App } from "./App";

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } } });
async function start() {
  if (import.meta.env.DEV && import.meta.env.VITE_ENABLE_MOCKS !== 'false') {
    const { worker } = await import('./mocks/browser');
    await worker.start({ quiet: true, onUnhandledFrame: 'bypass' });
  }
createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}><App /></QueryClientProvider>
  </StrictMode>,
);

}
void start();
