export default function ToggleSwitch({
  label,
  ariaLabel,
  checked,
  disabled = false,
  onChange,
  title,
}) {
  return (
    <label className={`toggle-switch${disabled ? " is-disabled" : ""}`}>
      <span className="toggle-switch-label">{label}</span>
      <span className="toggle-switch-control">
        <input
          className="toggle-switch-input"
          type="checkbox"
          role="switch"
          aria-label={ariaLabel || label}
          checked={checked}
          disabled={disabled}
          title={title}
          onChange={event => {
            if (!disabled) onChange(event.target.checked);
          }}
        />
        <span className="toggle-switch-track" aria-hidden="true">
          <span className="toggle-switch-thumb" />
        </span>
      </span>
    </label>
  );
}
