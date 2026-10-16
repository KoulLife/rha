import { useRef, type ReactNode } from "react";
import { Dialog as DialogPrimitive } from "radix-ui";
import type { DialogState } from "@/app/routes";
import { Button } from "@/components/ui/button";
import "./dialog.css";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";

export function Dialog({
  dialog,
  onClose,
  onConfirm,
  children,
  formId,
  closeLabel = "닫기",
  footerAction,
}: {
  dialog: NonNullable<DialogState>;
  onClose: () => void;
  onConfirm?: () => void;
  children?: ReactNode;
  formId?: string;
  closeLabel?: string;
  footerAction?: ReactNode;
}) {
  const opener = useRef(document.activeElement as HTMLElement | null);
  const restoreFocus = (event: Event) => {
    if (opener.current?.isConnected) {
      event.preventDefault();
      opener.current.focus();
    }
  };
  const confirm = () => {
    onConfirm?.();
    onClose();
  };
  if (dialog.destructive)
    return (
      <AlertDialog
        open
        onOpenChange={(open) => {
          if (!open) onClose();
        }}
      >
        <AlertDialogContent
          className="menu-dialog"
          onCloseAutoFocus={restoreFocus}
        >
          <AlertDialogHeader>
            <AlertDialogTitle>{dialog.title}</AlertDialogTitle>
            <AlertDialogDescription className="menu-dialog-body">
              {dialog.body}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={onClose}>취소</AlertDialogCancel>
            <AlertDialogAction
              variant={dialog.actionVariant ?? "destructive"}
              onClick={confirm}
            >
              {dialog.action ?? "확인"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    );
  return (
    <DialogPrimitive.Root
      open
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="menu-dialog-overlay" />
        <DialogPrimitive.Content
          className={`menu-dialog${dialog.details ? " menu-dialog-confirmation" : ""}`}
          onCloseAutoFocus={restoreFocus}
        >
          <DialogPrimitive.Title className="menu-dialog-title">
            {dialog.title}
          </DialogPrimitive.Title>
          <DialogPrimitive.Description
            className={children || dialog.details ? "sr-only" : "menu-dialog-body"}
          >
            {dialog.body}
          </DialogPrimitive.Description>
          {(children || dialog.details) && (
            <div className="menu-dialog-content">
              {dialog.details && (
                <dl className="confirm-dialog-details">
                  {dialog.details.map((item) => (
                    <div key={item.label}>
                      <dt>{item.label}</dt>
                      <dd>{item.value}</dd>
                    </div>
                  ))}
                </dl>
              )}
              {dialog.notice && <p className="confirm-dialog-notice">{dialog.notice}</p>}
              {children}
            </div>
          )}
          <div className="menu-dialog-actions">
            <Button
              variant="cancel"
              onClick={onClose}
            >
              {closeLabel}
            </Button>
            {footerAction ??
              (dialog.action && (
                <Button
                  variant={dialog.actionVariant ?? "default"}
                  type={formId ? "submit" : "button"}
                  form={formId}
                  onClick={formId ? undefined : confirm}
                >
                  {dialog.action}
                </Button>
              ))}
          </div>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
