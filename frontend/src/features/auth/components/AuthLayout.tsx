import type { ComponentProps, ReactNode } from "react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { BrandLogo } from "@/components/layout/BrandLogo";
import "../auth.css";

export function AuthLayout({
  title,
  description,
  children,
  preview,
  variant = "default",
}: {
  title: string;
  description: string;
  children: ReactNode;
  preview?: ReactNode;
  variant?: "default" | "login";
}) {
  return (
    <main className={`auth-page${variant === "login" ? " auth-page-login" : ""}`}>
      <div className="auth-container">
        <header className="auth-brand">
          <BrandLogo variant="auth" />
          <p className="auth-brand-caption">매일의 수업을 더 편안하게</p>
        </header>
        <Card className="auth-card">
          <CardHeader className="auth-card-header">
            <CardTitle>
              <h1 className="auth-title">{title}</h1>
            </CardTitle>
            <CardDescription>{description}</CardDescription>
          </CardHeader>
          <CardContent>{children}</CardContent>
        </Card>
        {preview && <section className="auth-preview">{preview}</section>}
        <footer className="auth-footer">R.HA Pilates Center</footer>
      </div>
    </main>
  );
}

export function AuthField({
  label,
  error,
  description,
  id,
  ...props
}: ComponentProps<typeof Input> & {
  label: string;
  error?: string;
  description?: string;
  id: string;
}) {
  const describedBy =
    [description ? `${id}-description` : "", error ? `${id}-error` : ""]
      .filter(Boolean)
      .join(" ") || undefined;
  return (
    <Field data-invalid={Boolean(error)}>
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <Input
        {...props}
        id={id}
        className="auth-input"
        aria-invalid={Boolean(error)}
        aria-describedby={describedBy}
      />
      {description && <FieldDescription id={`${id}-description`}>{description}</FieldDescription>}
      {error && <FieldError id={`${id}-error`}>{error}</FieldError>}
    </Field>
  );
}
