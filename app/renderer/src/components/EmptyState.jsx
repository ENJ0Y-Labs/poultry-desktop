const STATE_COPY = {
  "no-records": {
    icon: "＋",
    heading: "Nothing here yet",
    description: "There are no records to show yet. Add the first record when you are ready.",
    role: "status",
  },
  "no-results": {
    icon: "⌕",
    heading: "No matching records",
    description: "No records match the current search or filters. Adjust your criteria and try again.",
    role: "status",
  },
  "not-applicable": {
    icon: "—",
    heading: "Not applicable",
    description: "This information does not apply to the current selection.",
    role: "status",
  },
  loading: {
    icon: null,
    heading: "Loading records",
    description: "Retrieving the latest information. This may take a moment.",
    role: "status",
  },
  error: {
    icon: "!",
    heading: "Could not load records",
    description: "The information could not be retrieved. Your existing farm records have not been replaced.",
    role: "alert",
  },
};

export default function EmptyState({
  variant = "no-records",
  children,
  icon,
  heading,
  description,
  primaryAction,
  secondaryAction,
  onRetry,
  onClearFilters,
  className = "",
  compact = false,
}) {
  const copy = STATE_COPY[variant] || STATE_COPY["no-records"];
  const resolvedIcon = icon === undefined ? copy.icon : icon;
  const resolvedHeading = heading || copy.heading;
  const resolvedDescription = description || children || copy.description;
  const role = copy.role;

  return (
    <section
      className={`empty-state empty-state--${variant}${compact ? " empty-state--compact" : ""}${className ? ` ${className}` : ""}`}
      role={role}
      aria-live={variant === "error" ? "assertive" : "polite"}
      aria-busy={variant === "loading" ? "true" : undefined}
    >
      {resolvedIcon && <span className="empty-state__icon" aria-hidden="true">{resolvedIcon}</span>}
      {variant === "loading" && <span className="empty-state__spinner" aria-hidden="true" />}
      <div className="empty-state__copy">
        <h3>{resolvedHeading}</h3>
        {resolvedDescription && <p>{resolvedDescription}</p>}
      </div>
      {(primaryAction || secondaryAction || onRetry || onClearFilters) && (
        <div className="empty-state__actions">
          {onRetry && <button type="button" onClick={onRetry}>Try again</button>}
          {onClearFilters && <button type="button" className="secondary-button" onClick={onClearFilters}>Clear filters</button>}
          {primaryAction}
          {secondaryAction}
        </div>
      )}
    </section>
  );
}
