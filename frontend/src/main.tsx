import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./globals.css";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { App } from "./App";
import { mocksEnabled } from "./api/runtime";

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
});
async function start() {
  if (mocksEnabled) {
    const { worker } = await import("./mocks/browser");
    await worker.start({ quiet: true, onUnhandledFrame: "bypass" });
  } else if ("serviceWorker" in navigator) {
    // A previously opened mock preview must not intercept real API requests.
    const registrations = await navigator.serviceWorker.getRegistrations();
    await Promise.all(
      registrations
        .filter((registration) =>
          [registration.active, registration.waiting, registration.installing].some((worker) =>
            worker?.scriptURL.endsWith("/mockServiceWorker.js"),
          ),
        )
        .map((registration) => registration.unregister()),
    );
  }
  createRoot(document.getElementById("root")!).render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <App />
      </QueryClientProvider>
    </StrictMode>,
  );
}
void start();
