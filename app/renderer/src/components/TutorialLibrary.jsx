import { useMemo, useState } from "react";
// eslint-disable-next-line no-unused-vars -- Modal is rendered in JSX.
import { Modal } from "./Modal.jsx";
import "./TutorialLibrary.css";

export const tutorials = [
  { id: "batch", title: "Create a batch", category: "Farm", duration: "3 min", route: "flocks/flocks", tab: "tab-flocks", summary: "Register a new flock and assign it to a poultry house.", steps: ["Open Flocks and choose the Batches section. A house must exist before a batch can be assigned.", "Review the batch form: select the house and bird type, then enter placement date and initial bird count.", "Record a purchase cost only when it is known. Saving creates a permanent batch record; this guide never submits the form."], learned: ["Choose the house and poultry type", "Enter placement date and initial bird count", "Keep unknown purchase cost unknown"] },
  { id: "feed-type", title: "Add a feed type", category: "Inventory", duration: "2 min", route: "feed-stock/feed", tab: "tab-feed", summary: "Configure a feed definition and its applicable bird types.", steps: ["Open Feed & Stock, then choose Feed types.", "Enter the feed name, unit, and only confirmed package details. Select every poultry type that may use this feed.", "Unknown package weights must stay unset. Saving would create a farm record, so this guide stops before submit."], learned: ["Choose the unit used on the farm", "Set applicability explicitly", "Never guess package weight"] },
  { id: "feed-purchase", title: "Record a feed purchase", category: "Inventory", duration: "3 min", route: "feed-stock/purchases", tab: "tab-purchases", summary: "Record purchased feed and update stock through the purchase workflow.", steps: ["Open Feed & Stock and choose Purchases.", "Select the registered feed and supplier, then enter the purchase date, quantity, and total cost from the invoice.", "A purchase adds stock and records the cost once. This guide will not submit a purchase."], learned: ["Select the correct feed and supplier", "Enter actual quantity and total cost", "Purchase and consumption are separate events"] },
  { id: "mortality", title: "Record daily mortality", category: "Daily Records", duration: "2 min", route: "daily-work/mortality", tab: "tab-mortality", summary: "Record deaths against the right batch and business date.", steps: ["Open Daily Work and choose Mortality.", "Select the active batch and farm-local date, then enter the number of birds that died.", "Mortality reduces the calculated live population. Keep culling and sales in their own workflows; this guide does not save a record."], learned: ["Select the correct batch and date", "Record deaths separately from culling and sales", "Population is calculated from dated events"] },
  { id: "eggs", title: "Record egg production", category: "Production", duration: "2 min", route: "production/eggs", tab: "tab-eggs", summary: "Enter daily egg collection for a layer batch.", steps: ["Open Production and choose Egg production. Egg collection is for Layer batches.", "Choose the batch and date, then enter good and cracked quantities separately.", "Cracked eggs remain in history but are not sellable. This guide stops before saving."], learned: ["Use a Layer batch", "Separate good and cracked eggs", "Cracked eggs are not included in sellable stock"] },
  { id: "sale", title: "Record a sale", category: "Commerce", duration: "3 min", route: "sales-money/sales", tab: "tab-sales", summary: "Create a customer sale and record the transaction.", steps: ["Open Sales & Money and choose Sales.", "Select the customer and the correct sale workflow: eggs for Layers or birds for meat batches.", "Check quantity, price, date, and batch before saving. A real sale affects farm records; this guide does not submit it."], learned: ["Choose or create the right customer", "Match sale type to batch type", "Verify quantity, unit price, and date"] },
];

const categories = ["All", "Farm", "Inventory", "Daily Records", "Production", "Commerce"];

export default function TutorialLibrary({ open, onClose, onStart }) {
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState("All");
  const [selectedId, setSelectedId] = useState("batch");
  const filtered = useMemo(() => tutorials.filter(item => (category === "All" || item.category === category)
    && `${item.title} ${item.summary} ${item.category}`.toLowerCase().includes(query.trim().toLowerCase())), [category, query]);
  const selected = filtered.find(item => item.id === selectedId) || filtered[0];

  return <Modal open={open} onClose={onClose} title="Learn how to use Pomas" description="Choose a short interactive guide. Guides explain the real screens but never submit forms for you." size="large">
    <div className="tutorial-library">
      <div className="tutorial-library-tools">
        <label>Search tutorials<input value={query} onChange={event => setQuery(event.target.value)} placeholder="Search workflows" /></label>
        <div className="tutorial-categories" aria-label="Tutorial categories">{categories.map(item => <button type="button" key={item} className={category === item ? "active" : ""} aria-pressed={category === item} onClick={() => setCategory(item)}>{item}</button>)}</div>
      </div>
      <div className="tutorial-library-layout">
        <div className="tutorial-list" aria-label="Interactive guides">
          {filtered.map(item => <button type="button" key={item.id} className={`tutorial-card${selected?.id === item.id ? " selected" : ""}`} onClick={() => setSelectedId(item.id)}>
            <strong>{item.title}</strong><span>{item.summary}</span><small>{item.category} · {item.duration}</small>
          </button>)}
          {filtered.length === 0 && <p className="tutorial-empty">No guides match this search.</p>}
        </div>
        <aside className="tutorial-preview" aria-label="Selected tutorial preview">
          <p className="eyebrow">INTERACTIVE GUIDE</p>
          <h3>{selected?.title || "Select a tutorial"}</h3>
          <p>{selected?.summary || "Choose a workflow to preview its steps."}</p>
          <ol>{selected?.steps.map(step => <li key={step}>{step}</li>)}</ol>
          <p className="tutorial-safety-note">These guides use the real farm screens. A save you choose will create a real farm record; the guide itself never submits forms or creates demo data.</p>
          <button type="button" className="primary-button" disabled={!selected} onClick={() => { onClose(); onStart(selected); }}>Start guide</button>
        </aside>
      </div>
    </div>
  </Modal>;
}
