import { defineConfig } from "orval";

export default defineConfig({
  rha: {
    input: "../api-spec/openapi.yaml",
    output: {
      target: "./src/api/generated/api.ts",
      client: "react-query",
      httpClient: "fetch",
      clean: true,
      override: {
        operations: Object.fromEntries(
          [
            "getLessons",
            "getReservations",
            "getAbsences",
            "getMembers",
            "getInstructors",
            "getPassProducts",
            "getLessonTypes",
            "getRooms",
            "getPayments",
          ].map((operation) => [
            operation,
            { query: { useInfinite: true, useInfiniteQueryParam: "page" } },
          ]),
        ),
        mutator: { path: "./src/api/client.ts", name: "apiRequest" },
        fetch: { includeHttpResponseReturnType: false },
      },
    },
  },
});
