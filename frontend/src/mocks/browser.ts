import { setupWorker } from "msw/browser";
import { adminHandlers } from "./admin/handlers";
export const worker = setupWorker(...adminHandlers);
