import { ArrowDown, ArrowUp, ArrowUpDown } from "lucide-react";
import { Button } from "./button";
import "./sort-header-button.css";

export function SortHeaderButton({
  label,
  direction,
  onClick,
}: {
  label: string;
  direction?: "asc" | "desc";
  onClick: () => void;
}) {
  const Icon = direction === "asc" ? ArrowUp : direction === "desc" ? ArrowDown : ArrowUpDown;
  return (
    <Button
      type="button"
      variant="ghost"
      size="sm"
      className="sort-header-button"
      data-direction={direction}
      aria-label={`${label} ${direction === "asc" ? "내림차순" : "오름차순"} 정렬`}
      onClick={onClick}
    >
      {label}
      <Icon aria-hidden="true" />
    </Button>
  );
}
