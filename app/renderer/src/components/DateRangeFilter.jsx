import { useId } from "react";
import { DATE_RANGE_PRESETS, createDateRange, formatDateRange, isValidDateRange } from "../utils/dateRange.js";

export default function DateRangeFilter({ value, timezone, dateFormat, onChange }) {
  const id = useId();
  const valid = isValidDateRange(value);
  const preset = value?.preset || "LAST_30_DAYS";

  function selectPreset(nextPreset) {
    if (nextPreset === "CUSTOM") {
      onChange({ ...value, preset: "CUSTOM" });
      return;
    }
    onChange(createDateRange(nextPreset, timezone));
  }

  function updateDate(field, nextValue) {
    onChange({ ...value, preset: "CUSTOM", [field]: nextValue });
  }

  return (
    <div className="date-range-filter" aria-label="Reporting date range">
      <label className="date-range-preset-label" htmlFor={`${id}-preset`}>Reporting period</label>
      <select id={`${id}-preset`} aria-label="Reporting period" value={preset} onChange={event => selectPreset(event.target.value)}>
        {DATE_RANGE_PRESETS.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
      </select>
      <div className="date-range-fields">
        <label htmlFor={`${id}-from`}>From
          <input id={`${id}-from`} type="date" aria-label="Range start date" value={value?.from || ""} aria-invalid={!valid} onChange={event => updateDate("from", event.target.value)} />
        </label>
        <label htmlFor={`${id}-to`}>To
          <input id={`${id}-to`} type="date" aria-label="Range end date" value={value?.to || ""} aria-invalid={!valid} onChange={event => updateDate("to", event.target.value)} />
        </label>
      </div>
      {valid
        ? <p className="date-range-summary" aria-live="polite">Showing {formatDateRange(value, dateFormat)} (inclusive)</p>
        : <p className="date-range-error" role="alert">Enter valid dates and make sure the start date is on or before the end date.</p>}
    </div>
  );
}
