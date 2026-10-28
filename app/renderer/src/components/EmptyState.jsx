export default function EmptyState({ children, role, className = "muted empty" }) {
  return <p className={className} role={role}>{children}</p>;
}
