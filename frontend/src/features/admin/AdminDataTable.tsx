import { useState, type ReactNode } from "react";
import type { PageInfo } from "@/api/generated/api";
import { MenuEmpty, MenuPanel } from "@/components/layout/MenuPanel";
import { PAGE_SIZE, Pagination } from "@/components/ui/Pagination";
import { SortHeaderButton } from "@/components/ui/SortHeaderButton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

export function DataTable<T extends { id: number }>({
  rows,
  columns,
  pageInfo,
  onPage,
  action,
  empty = "조회 결과가 없습니다",
  paginate = true,
  selectedId,
  onRowClick,
  title,
  titleAction,
}: {
  rows: T[];
  columns: {
    label: string;
    render: (row: T) => ReactNode;
    money?: boolean;
    sort?: { direction: "asc" | "desc"; onToggle: () => void };
  }[];
  pageInfo?: PageInfo;
  onPage?: (page: number) => void;
  action?: ReactNode;
  empty?: string;
  paginate?: boolean;
  selectedId?: number | null;
  onRowClick?: (row: T) => void;
  title?: string;
  titleAction?: ReactNode;
}) {
  const [localPage, setLocalPage] = useState(1);
  const localTotalPages = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
  const visiblePage = Math.min(localPage, localTotalPages);
  const visibleRows =
    pageInfo || !paginate
      ? rows
      : rows.slice((visiblePage - 1) * PAGE_SIZE, visiblePage * PAGE_SIZE);
  const panel = (
    <MenuPanel
      variant="table"
      title={title}
      action={titleAction}
    >
      {rows.length ? (
        <Table
          variant="management"
          className={selectedId === undefined ? undefined : "timetable-list-table"}
        >
          <TableHeader>
            <TableRow>
              {columns.map((c) => (
                <TableHead
                  key={c.label}
                  aria-sort={
                    c.sort ? (c.sort.direction === "asc" ? "ascending" : "descending") : undefined
                  }
                >
                  {c.sort ? (
                    <SortHeaderButton
                      label={c.label}
                      direction={c.sort.direction}
                      onClick={c.sort.onToggle}
                    />
                  ) : (
                    c.label
                  )}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {visibleRows.map((row) => (
              <TableRow
                key={row.id}
                data-selected={selectedId === row.id}
                data-selectable={onRowClick ? "true" : undefined}
                aria-selected={onRowClick ? selectedId === row.id : undefined}
                tabIndex={onRowClick ? 0 : undefined}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
                onKeyDown={
                  onRowClick
                    ? (event) => {
                        if (
                          event.target === event.currentTarget &&
                          (event.key === "Enter" || event.key === " ")
                        ) {
                          event.preventDefault();
                          onRowClick(row);
                        }
                      }
                    : undefined
                }
              >
                {columns.map((c) => (
                  <TableCell
                    key={c.label}
                    className={c.money ? "admin-money" : undefined}
                  >
                    {c.render(row)}
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      ) : (
        <MenuEmpty
          title={empty}
          description="조회 조건을 변경하거나 새 항목을 등록해 주세요."
        />
      )}
    </MenuPanel>
  );
  return action || pageInfo || (paginate && localTotalPages > 1) ? (
    <div className="admin-table-area">
      {action && <div className="admin-table-toolbar">{action}</div>}
      {panel}
      {pageInfo ? (
        <Pagination
          total={pageInfo.totalElements}
          page={pageInfo.page}
          totalPages={pageInfo.totalPages}
          onPageChange={onPage}
        />
      ) : (
        paginate &&
        localTotalPages > 1 && (
          <Pagination
            total={rows.length}
            page={visiblePage}
            totalPages={localTotalPages}
            onPageChange={setLocalPage}
          />
        )
      )}
    </div>
  ) : (
    panel
  );
}
