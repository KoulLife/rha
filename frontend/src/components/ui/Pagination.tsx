import { Button } from "@/components/ui/button";

export function Pagination({ total = 0 }: { total?: number }) {
  return <nav data-slot="pagination" className="menu-pagination" aria-label="페이지 이동">
    <span>전체 {total}건</span>
    <ul data-slot="pagination-content"><li><Button variant="outline" disabled>이전</Button></li><li><span aria-current="page">1 / 1</span></li><li><Button variant="outline" disabled>다음</Button></li></ul>
  </nav>;
}

