import { Button } from "@/components/ui/button";

export const PAGE_SIZE = 5;

export function Pagination({ total = 0, page = 1, totalPages, pageSize = Math.max(total, 1), onPageChange }: { total?: number; page?: number; totalPages?: number; pageSize?: number; onPageChange?: (page: number) => void }) {
  const pages = Math.max(1, totalPages ?? Math.ceil(total / Math.max(1, pageSize)));
  return <nav data-slot="pagination" className="menu-pagination" aria-label="페이지 이동">
    <span>전체 {total}건</span>
    <ul data-slot="pagination-content"><li><Button variant="outline" disabled={page <= 1 || !onPageChange} onClick={() => onPageChange?.(page - 1)}>이전</Button></li><li><span aria-current="page">{page} / {pages}</span></li><li><Button variant="outline" disabled={page >= pages || !onPageChange} onClick={() => onPageChange?.(page + 1)}>다음</Button></li></ul>
  </nav>;
}

