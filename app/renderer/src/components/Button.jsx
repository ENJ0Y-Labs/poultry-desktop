import PropTypes from "prop-types";

const variants = new Set(["primary", "secondary", "outline", "ghost", "destructive"]);

/** Shared action button. Keep actions in the renderer; domain validation stays in the API. */
export default function Button({
  children,
  variant = "secondary",
  size = "md",
  iconOnly = false,
  loading = false,
  disabled = false,
  className = "",
  type = "button",
  ariaLabel,
  title,
  ...props
}) {
  const safeVariant = variants.has(variant) ? variant : "secondary";
  const label = ariaLabel || (typeof children === "string" ? children : undefined);
  return (
    <button
      {...props}
      type={type}
      className={[
        "ui-button",
        `ui-button--${safeVariant}`,
        `ui-button--${size}`,
        iconOnly && "ui-button--icon-only",
        loading && "is-loading",
        className,
      ].filter(Boolean).join(" ")}
      disabled={disabled || loading}
      aria-label={iconOnly ? label : props["aria-label"]}
      aria-busy={loading || undefined}
      title={title || (iconOnly ? label : undefined)}
    >
      {loading && <span className="ui-button__spinner" aria-hidden="true" />}
      <span className="ui-button__content">{children}</span>
    </button>
  );
}
