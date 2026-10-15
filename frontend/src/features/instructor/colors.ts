import { useSyncExternalStore } from 'react';

const storageKey = 'rha.instructorColors';
const changedEvent = 'rha:instructor-colors-changed';
const colorPattern = /^#[0-9a-fA-F]{6}$/;

export function fallbackInstructorColor(instructorId: number) {
  const token = getComputedStyle(document.documentElement).getPropertyValue(`--instructor-${instructorId % 6}`).trim();
  return colorPattern.test(token) ? token.toUpperCase() : '#526B61';
}

function snapshot() {
  try { return window.localStorage.getItem(storageKey) ?? '{}'; }
  catch { return '{}'; }
}

function subscribe(onChange: () => void) {
  const onStorage = (event: StorageEvent) => { if (event.key === storageKey) onChange(); };
  window.addEventListener('storage', onStorage);
  window.addEventListener(changedEvent, onChange);
  return () => {
    window.removeEventListener('storage', onStorage);
    window.removeEventListener(changedEvent, onChange);
  };
}

function parseColors(value: string): Record<string, string> {
  try {
    const parsed: unknown = JSON.parse(value);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {};
    return Object.fromEntries(Object.entries(parsed).filter((entry): entry is [string, string] => typeof entry[1] === 'string' && colorPattern.test(entry[1])));
  } catch { return {}; }
}

export function useInstructorColors() {
  return parseColors(useSyncExternalStore(subscribe, snapshot, () => '{}'));
}

// TODO(api): 강사 색상 저장·조회 계약이 준비되면 브라우저 저장소 대신 생성된 API 훅을 사용한다.
export function saveInstructorColor(instructorId: number, color: string) {
  if (!colorPattern.test(color)) return false;
  try {
    window.localStorage.setItem(storageKey, JSON.stringify({ ...parseColors(snapshot()), [instructorId]: color.toUpperCase() }));
    window.dispatchEvent(new Event(changedEvent));
    return true;
  } catch { return false; }
}
