import { cva } from "class-variance-authority";
import logoUrl from "../../../../docs/R.ha.png";
import sidebarLogoUrl from "../../../../docs/R.ha_logo_2.png";
import "./brand-logo.css";

const logoVariants = cva("brand-logo", {
  variants: {
    variant: {
      auth: "brand-logo-auth",
      sidebar: "brand-logo-sidebar",
    },
  },
});

export function BrandLogo({ variant }: { variant: "auth" | "sidebar" }) {
  if (variant === "sidebar")
    return (
      <span
        className={logoVariants({ variant })}
        role="img"
        aria-label="르하 R.HA Pilates Center"
      >
        <span
          className="brand-logo-mark"
          aria-hidden="true"
        >
          <img
            src={sidebarLogoUrl}
            alt=""
            width={2752}
            height={1536}
          />
        </span>
        <span
          className="brand-logo-caption"
          aria-hidden="true"
        >
          <span>PILATES</span>
          <span>CENTER</span>
        </span>
      </span>
    );
  return (
    <span className={logoVariants({ variant })}>
      <img
        src={logoUrl}
        alt="르하 R.HA Pilates Center"
        width={3368}
        height={2382}
      />
    </span>
  );
}
