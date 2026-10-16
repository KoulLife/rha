import { useState, type ReactNode } from "react";
import { MenuEmpty, MenuPanel } from "@/components/layout/MenuPanel";
import { SortHeaderButton } from "@/components/ui/SortHeaderButton";
import { PAGE_SIZE, Pagination } from "@/components/ui/Pagination";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import "./instructor-table.css";

type Column<T> = {
  key: string;
  label: string;
  value?: (row: T) => string | number;
  cell: (row: T) => ReactNode;
};
const collator = new Intl.Collator("ko-KR", { numeric: true });

export function InstructorTable<T extends { id: number }>({
  rows,
  columns,
  label,
  selectedId,
  emptyTitle = "조건에 맞는 내역이 없습니다",
}: {
  rows: T[];
  columns: Column<T>[];
  label: string;
  selectedId?: number;
  emptyTitle?: string;
}) {
  const [sort, setSort] = useState({ key: "", descending: false });
  const [page, setPage] = useState(1);
  const column = columns.find((item) => item.key === sort.key);
  const sorted = column?.value
    ? [...rows].sort((a, b) => {
        const left = column.value!(a),
          right = column.value!(b);
        return (
          (typeof left === "number" && typeof right === "number"
            ? left - right
            : collator.compare(String(left), String(right))) * (sort.descending ? -1 : 1)
        );
      })
    : rows;
  const currentPage = Math.min(page, Math.max(1, Math.ceil(rows.length / PAGE_SIZE)));
  return (
    <div className="instructor-table-section">
      <MenuPanel
        variant="table"
        className="instructor-table-panel"
      >
        {rows.length ? (
          <Table
            variant="management"
            aria-label={label}
          >
            <TableHeader>
              <TableRow>
                {columns.map((item) => (
                  <TableHead
                    key={item.key}
                    scope="col"
                    aria-sort={
                      item.value
                        ? sort.key === item.key
                          ? sort.descending
                            ? "descending"
                            : "ascending"
                          : "none"
                        : undefined
                    }
                  >
                    {item.value ? (
                      <SortHeaderButton
                        label={item.label}
                        direction={
                          sort.key === item.key ? (sort.descending ? "desc" : "asc") : undefined
                        }
                        onClick={() => {
                          setSort({
                            key: item.key,
                            descending: sort.key === item.key && !sort.descending,
                          });
                          setPage(1);
                        }}
                      />
                    ) : (
                      item.label
                    )}
                  </TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody>
              {sorted.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE).map((row) => (
                <TableRow
                  key={row.id}
                  data-state={row.id === selectedId ? "selected" : undefined}
                >
                  {columns.map((item) => (
                    <TableCell key={item.key}>{item.cell(row)}</TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        ) : (
          <MenuEmpty
            title={emptyTitle}
            description="새로운 내역이 등록되면 이곳에서 확인할 수 있습니다. 조회 조건도 확인해 주세요."
          />
        )}
      </MenuPanel>
      <Pagination
        total={rows.length}
        page={currentPage}
        pageSize={PAGE_SIZE}
        onPageChange={setPage}
      />
    </div>
  );
}
