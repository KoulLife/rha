import { useState, type ReactNode } from "react";
import { PAGE_SIZE, Pagination } from "./Pagination";
import "./paginated-list.css";

/** Client-side lists only; API-paginated lists retain their server pageInfo. */
export function PaginatedList<T>({ items, pageSize = PAGE_SIZE, alwaysShowPagination = false, children }: {
  items: T[]; pageSize?: number; alwaysShowPagination?: boolean; children: (rows: T[]) => ReactNode;
}) {
  const [page, setPage] = useState(1);
  const current = Math.min(page, Math.max(1, Math.ceil(items.length / pageSize)));
  return <div className="paginated-list">
    {children(items.slice((current - 1) * pageSize, current * pageSize))}
    {(alwaysShowPagination || items.length > pageSize) && <Pagination total={items.length} page={current} pageSize={pageSize} onPageChange={setPage} />}
  </div>;
}
