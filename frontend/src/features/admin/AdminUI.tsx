import { useEffect, useId, useRef, useState, type ComponentProps, type ReactNode } from "react";
import { Controller, useForm } from "react-hook-form";
import { useQueryClient } from "@tanstack/react-query";
import { Dialog as DialogPrimitive, Popover } from "radix-ui";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Field, FieldLabel, FieldDescription, FieldError } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { DatePicker } from "@/components/ui/date-picker";
import {
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
} from "@/components/ui/select";
import { Checkbox } from "@/components/ui/checkbox";
import { Alert, AlertTitle, AlertDescription } from "@/components/ui/alert";
import { Skeleton } from "@/components/ui/skeleton";
import { PageTitle } from "@/components/ui/PageTitle";
import { MenuPanel, MenuEmpty } from "@/components/layout/MenuPanel";
import {
  AlertDialog,
  AlertDialogContent,
  AlertDialogTitle,
  AlertDialogDescription,
} from "@/components/ui/alert-dialog";
import "./admin.css";
import { errorMessage, type FormField, type Option, type Values } from "./adminUtils";

export { DataTable } from "./AdminDataTable";
export * from "./adminUtils";

export function AdminPage({
  title,
  description,
  action,
  children,
}: {
  title: string;
  description: string;
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="menu-page admin-page">
      <PageTitle
        title={title}
        description={description}
        action={action}
      />
      {children}
    </section>
  );
}
export function AdminLinks({ items }: { items: { label: string; onClick: () => void }[] }) {
  return (
    <nav
      className="menu-actions"
      aria-label="관련 관리 메뉴"
    >
      {items.map((item) => (
        <Button
          key={item.label}
          variant="outline"
          onClick={item.onClick}
        >
          {item.label}
        </Button>
      ))}
    </nav>
  );
}
export function Facts({ items }: { items: [string, ReactNode][] }) {
  return (
    <dl className="menu-facts">
      {items.map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{value ?? "—"}</dd>
        </div>
      ))}
    </dl>
  );
}
export function PendingFeature({ title, description }: { title: string; description: string }) {
  return (
    <MenuPanel>
      <MenuEmpty
        title={`${title} · 준비 중`}
        description={description}
      />
    </MenuPanel>
  );
}
export function QueryState({
  query,
  children,
}: {
  query: { isPending: boolean; isError: boolean; error: unknown; refetch: () => unknown };
  children: ReactNode;
}) {
  if (query.isPending)
    return (
      <div
        className="admin-loading"
        role="status"
        aria-label="불러오는 중"
      >
        {[1, 2, 3, 4].map((i) => (
          <Skeleton
            key={i}
            className="admin-skeleton"
          />
        ))}
      </div>
    );
  if (query.isError)
    return (
      <Alert variant="destructive">
        <AlertTitle>정보를 불러오지 못했습니다</AlertTitle>
        <AlertDescription>
          {errorMessage(query.error)}
          <Button
            variant="outline"
            onClick={() => query.refetch()}
          >
            다시 시도
          </Button>
        </AlertDescription>
      </Alert>
    );
  return <>{children}</>;
}
export function SearchChoice({
  id,
  value,
  onChange,
  choices,
  disabled,
}: {
  id: string;
  value: string;
  onChange: (value: string) => void;
  choices: Option[];
  disabled?: boolean;
}) {
  const [open, setOpen] = useState(false),
    [term, setTerm] = useState("");
  return (
    <Popover.Root
      open={open}
      onOpenChange={setOpen}
    >
      <Popover.Trigger asChild>
        <Button
          id={id}
          type="button"
          variant="outline"
          role="combobox"
          aria-expanded={open}
          disabled={disabled}
          className="admin-choice"
        >
          {choices.find((o) => o.value === value)?.label ?? "검색하여 선택"}
        </Button>
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content
          className="admin-choice-popup"
          align="start"
        >
          <Input
            autoFocus
            aria-label="선택 항목 검색"
            value={term}
            onChange={(e) => setTerm(e.target.value)}
          />
          <div
            role="listbox"
            aria-label="검색 결과"
          >
            {choices
              .filter((o) => o.label.includes(term))
              .map((o) => (
                <Button
                  key={o.value}
                  role="option"
                  aria-selected={o.value === value}
                  variant="ghost"
                  onClick={() => {
                    onChange(o.value);
                    setOpen(false);
                  }}
                >
                  {o.label}
                </Button>
              ))}
            {!choices.some((o) => o.label.includes(term)) && <p>검색 결과가 없습니다.</p>}
          </div>
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  );
}
export function AdminForm({
  fields,
  initial = {},
  submitLabel = "저장",
  submitVariant = "default",
  variant = "default",
  primaryFilterCount,
  onSubmit,
  onCancel,
  toolbarAction,
  children,
  successToast = true,
  onFieldChange,
  deferSuccess = false,
  submitDisabled = false,
}: {
  fields: FormField[];
  initial?: Values;
  submitLabel?: string;
  submitVariant?: ComponentProps<typeof Button>["variant"];
  variant?: "default" | "filter" | "settings";
  primaryFilterCount?: number;
  onSubmit: (values: Values) => Promise<unknown>;
  onCancel?: () => void;
  toolbarAction?: ReactNode;
  children?: ReactNode;
  successToast?: boolean;
  deferSuccess?: boolean;
  submitDisabled?: boolean;
  onFieldChange?: (name: string, values: Values) => Values;
}) {
  const uid = useId();
  const {
    register,
    control,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
    setError,
    setValue,
    getValues,
  } = useForm<Values>({ defaultValues: initial });
  const fieldChange = useRef(onFieldChange);
  fieldChange.current = onFieldChange;
  useEffect(() => {
    const subscription = watch((_, info) => {
      if (!info.name || !fieldChange.current) return;
      const changes = fieldChange.current(info.name, getValues());
      for (const [name, value] of Object.entries(changes)) {
        if (getValues(name) !== value) setValue(name, value, { shouldDirty: true });
      }
    });
    return () => subscription.unsubscribe();
  }, [watch, getValues, setValue]);
  const client = useQueryClient();
  const submit = handleSubmit(async (values) => {
    try {
      await onSubmit(values);
      if (!deferSuccess && submitLabel !== "조회" && !submitLabel.endsWith("확인")) {
        await client.invalidateQueries();
        if (successToast) toast.success(`${submitLabel} 완료`);
      }
    } catch (error) {
      setError("root", { message: errorMessage(error) });
    }
  });
  const [expanded, setExpanded] = useState(false);
  const hasDesktopAdvanced = variant === "filter" && fields.length > (primaryFilterCount ?? 6);
  const hasMobileAdvanced = variant === "filter" && fields.length > 2;
  const hasAdvanced = hasDesktopAdvanced || hasMobileAdvanced;
  const values = watch();
  const filterSubmit = useRef(onSubmit);
  filterSubmit.current = onSubmit;
  const filterFields = useRef(fields);
  filterFields.current = fields;
  useEffect(() => {
    if (variant !== "filter") return;
    let timer: ReturnType<typeof setTimeout>;
    const subscription = watch((next, info) => {
      clearTimeout(timer);
      timer = setTimeout(
        () => {
          void Promise.resolve(filterSubmit.current(next as Values)).catch((error) =>
            setError("root", { message: errorMessage(error) }),
          );
        },
        info.name &&
          filterFields.current.find((field) => field.name === info.name)?.type !== "select" &&
          filterFields.current.find((field) => field.name === info.name)?.type !== "search"
          ? 250
          : 0,
      );
    });
    return () => {
      subscription.unsubscribe();
      clearTimeout(timer);
    };
  }, [variant, watch, setError]);
  const advancedCount = hasAdvanced
    ? fields
        .slice(primaryFilterCount ?? 1)
        .filter(
          (field) =>
            values[field.name] &&
            values[field.name] !== "ALL" &&
            values[field.name] !== initial[field.name],
        ).length
    : 0;
  const renderField = (field: FormField, index: number) => {
    if (field.showWhen && values[field.showWhen.name] !== field.showWhen.value) return null;
    const id = `${uid}-${field.name}`,
      error = errors[field.name];
    return (
      <Field
        key={field.name}
        data-field={field.name}
        data-filter-kind={field.type ?? "text"}
        data-filter-desktop-extra={
          hasDesktopAdvanced && index >= (primaryFilterCount ?? 4) ? "" : undefined
        }
        data-filter-mobile-extra={hasMobileAdvanced && index >= 1 ? "" : undefined}
        data-invalid={!!error}
        orientation={field.type === "checkbox" ? "horizontal" : "vertical"}
      >
        {field.type !== "checkbox" && (
          <FieldLabel htmlFor={id}>
            {field.label}
            {field.required ? " *" : ""}
          </FieldLabel>
        )}
        {field.type === "select" || field.type === "search" ? (
          <Controller
            name={field.name}
            control={control}
            rules={{ required: field.required ? `${field.label}을 선택해 주세요.` : false }}
            render={({ field: f }) =>
              field.type === "search" ? (
                <SearchChoice
                  id={id}
                  value={String(f.value ?? "")}
                  onChange={f.onChange}
                  choices={field.options ?? []}
                  disabled={field.disabled}
                />
              ) : (
                <Select
                  value={String(f.value ?? "")}
                  onValueChange={f.onChange}
                  disabled={field.disabled}
                >
                  <SelectTrigger
                    id={id}
                    aria-invalid={!!error}
                  >
                    <SelectValue placeholder="선택해 주세요" />
                  </SelectTrigger>
                  <SelectContent className="admin-select-popup">
                    {field.options?.map((o) => (
                      <SelectItem
                        value={o.value}
                        key={o.value}
                      >
                        {o.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )
            }
          />
        ) : field.type === "checkbox" ? (
          <>
            <Controller
              name={field.name}
              control={control}
              render={({ field: f }) => (
                <Checkbox
                  id={id}
                  checked={f.value === true}
                  onCheckedChange={(v) => f.onChange(v === true)}
                  disabled={field.disabled}
                />
              )}
            />
            <FieldLabel htmlFor={id}>{field.label}</FieldLabel>
          </>
        ) : field.type === "date" ? (
          <Controller
            name={field.name}
            control={control}
            rules={{ required: field.required ? `${field.label}을 입력해 주세요.` : false }}
            render={({ field: f }) => (
              <DatePicker
                id={id}
                ref={f.ref}
                value={String(f.value ?? "")}
                onChange={f.onChange}
                onBlur={f.onBlur}
                min={field.min === undefined ? undefined : String(field.min)}
                max={field.max === undefined ? undefined : String(field.max)}
                disabled={field.disabled}
                aria-invalid={!!error}
                aria-describedby={error ? `${id}-error` : undefined}
              />
            )}
          />
        ) : field.type === "textarea" ? (
          <textarea
            id={id}
            className="admin-textarea"
            maxLength={field.maxLength ?? 200}
            {...register(field.name, { required: field.required ? "필수 입력입니다." : false })}
          />
        ) : (
          <Input
            id={id}
            type={field.type ?? "text"}
            disabled={field.disabled}
            min={field.min}
            max={field.max}
            step={field.type === "number" ? 1 : undefined}
            maxLength={field.maxLength}
            pattern={field.pattern}
            aria-invalid={!!error}
            aria-describedby={error ? `${id}-error` : undefined}
            {...register(field.name, {
              required: field.required ? `${field.label}을 입력해 주세요.` : false,
              validate: (value) =>
                !field.required ||
                typeof value !== "string" ||
                value.trim().length > 0 ||
                "공백만 입력할 수 없습니다.",
            })}
          />
        )}
        {field.description && <FieldDescription>{field.description}</FieldDescription>}
        {error && <FieldError id={`${id}-error`}>{error.message}</FieldError>}
      </Field>
    );
  };
  return (
    <form
      className="admin-form"
      data-variant={variant}
      data-expanded={expanded}
      aria-label={variant === "filter" ? "목록 조회 조건" : undefined}
      onSubmit={submit}
    >
      {fields.map(renderField)}
      {children}
      {errors.root && (
        <Alert variant="destructive">
          <AlertDescription>{errors.root.message}</AlertDescription>
        </Alert>
      )}
      {variant === "filter" ? (
        <>
          <div className="menu-actions admin-filter-submit">
            {hasAdvanced && (
              <Button
                type="button"
                variant="ghost"
                className="admin-filter-toggle"
                data-desktop-visible={hasDesktopAdvanced}
                aria-expanded={expanded}
                onClick={() => setExpanded((value) => !value)}
              >
                {expanded ? "상세 조건 접기" : "상세 조건"}
                {advancedCount > 0 ? ` (${advancedCount})` : ""}
              </Button>
            )}
          </div>
          {toolbarAction && (
            <div className="menu-actions admin-form-actions admin-filter-actions">
              {toolbarAction}
            </div>
          )}
        </>
      ) : (
        <div className="menu-actions admin-form-actions">
          {toolbarAction}
          {onCancel && (
            <Button
              type="button"
              variant="cancel"
              disabled={isSubmitting}
              onClick={onCancel}
            >
              취소
            </Button>
          )}
          <Button
            type="submit"
            variant={submitVariant}
            disabled={isSubmitting || submitDisabled}
          >
            {isSubmitting ? "처리 중…" : submitLabel}
          </Button>
        </div>
      )}
    </form>
  );
}
export type Action = {
  title: string;
  description: string;
  submitLabel: string;
  fields?: FormField[];
  initial?: Values;
  danger?: boolean;
  submitVariant?: ComponentProps<typeof Button>["variant"];
  content?: ReactNode;
  onSubmit: (values: Values) => Promise<unknown>;
};
export function ActionDialog({ action, onClose }: { action: Action; onClose: () => void }) {
  const opener = useRef(document.activeElement as HTMLElement | null);
  const [busy, setBusy] = useState(false);
  const close = () => {
    if (!busy) onClose();
  };
  const restore = (e: Event) => {
    if (opener.current?.isConnected) {
      e.preventDefault();
      opener.current.focus();
    }
  };
  const form = (
    <AdminForm
      fields={action.fields ?? []}
      initial={action.initial}
      submitLabel={action.submitLabel}
      submitVariant={action.submitVariant}
      onCancel={close}
      onSubmit={async (values) => {
        setBusy(true);
        try {
          await action.onSubmit(values);
          onClose();
        } finally {
          setBusy(false);
        }
      }}
    >
      {action.content}
    </AdminForm>
  );
  if (action.danger)
    return (
      <AlertDialog
        open
        onOpenChange={(open) => {
          if (!open) close();
        }}
      >
        <AlertDialogContent
          className="menu-dialog admin-dialog"
          onCloseAutoFocus={restore}
        >
          <AlertDialogTitle>{action.title}</AlertDialogTitle>
          <AlertDialogDescription>{action.description}</AlertDialogDescription>
          {form}
        </AlertDialogContent>
      </AlertDialog>
    );
  return (
    <DialogPrimitive.Root
      open
      onOpenChange={(open) => {
        if (!open) close();
      }}
    >
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="menu-dialog-overlay" />
        <DialogPrimitive.Content
          className="menu-dialog admin-dialog"
          onCloseAutoFocus={restore}
          onInteractOutside={(e) => {
            if (busy) e.preventDefault();
          }}
        >
          <DialogPrimitive.Title className="menu-dialog-title">
            {action.title}
          </DialogPrimitive.Title>
          <DialogPrimitive.Description className="menu-dialog-body">
            {action.description}
          </DialogPrimitive.Description>
          {form}
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
export function Filters({
  fields,
  initial = {},
  primaryFilterCount,
  action,
  onApply,
}: {
  fields: FormField[];
  initial?: Values;
  primaryFilterCount?: number;
  action?: ReactNode;
  onApply: (values: Values) => void;
}) {
  const defaults: Values = {
    ...Object.fromEntries(
      fields
        .filter((field) => field.options?.some((option) => option.value === "ALL"))
        .map((field) => [field.name, "ALL"]),
    ),
    ...initial,
  };
  return (
    <AdminForm
      variant="filter"
      fields={fields}
      initial={defaults}
      primaryFilterCount={primaryFilterCount}
      toolbarAction={action}
      submitLabel="조회"
      submitVariant="outline"
      onSubmit={async (v) => onApply(v)}
    />
  );
}
