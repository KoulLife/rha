import { screenLabels, type Screen } from "../../app/routes";

export function Breadcrumb({ screen }: { screen: Screen }) {
  return (
    <nav className="breadcrumb" aria-label="현재 위치">
      <span>회원</span>
      <span aria-hidden="true">/</span>
      <strong>{screenLabels[screen]}</strong>
    </nav>
  );
}

