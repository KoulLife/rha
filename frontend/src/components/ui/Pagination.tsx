export function Pagination() {
  return (
    <div className="pagination">
      <button className="button outline" type="button" disabled>
        이전
      </button>
      <span>1 / 1</span>
      <button className="button outline" type="button" disabled>
        다음
      </button>
    </div>
  );
}

