import type { DialogState } from "../../app/routes";

export function Dialog({
  dialog,
  onClose,
}: {
  dialog: NonNullable<DialogState>;
  onClose: () => void;
}) {
  return (
    <div className="dialog-backdrop" role="presentation" onClick={onClose}>
      <section
        className="dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="dialog-title"
        onClick={(event) => event.stopPropagation()}
      >
        <h2 id="dialog-title">{dialog.title}</h2>
        <p>{dialog.body}</p>
        <div className="dialog-actions">
          <button className="button outline" type="button" onClick={onClose}>
            닫기
          </button>
          {dialog.action && (
            <button className="button primary" type="button" onClick={onClose}>
              {dialog.action}
            </button>
          )}
        </div>
      </section>
    </div>
  );
}

