import { Button } from "@/components/ui/button";

export const PAGE_SIZE = 5;

export function Pagination({ total = 0, page = 1, totalPages = 1, onPageChange }: { total?: number; page?: number; totalPages?: number; onPageChange?: (page: number) => void }) {
  return <nav data-slot="pagination" className="menu-pagination" aria-label="페이지 이동">
    <span>전체 {total}건</span>
    <ul data-slot="pagination-content"><li><Button variant="outline" disabled={page <= 1 || !onPageChange} onClick={() => onPageChange?.(page - 1)}>이전</Button></li><li><span aria-current="page">{page} / {Math.max(1, totalPages)}</span></li><li><Button variant="outline" disabled={page >= totalPages || !onPageChange} onClick={() => onPageChange?.(page + 1)}>다음</Button></li></ul>
  </nav>;
}

