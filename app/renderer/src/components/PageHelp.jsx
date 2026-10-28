import "./PageHelp.css";

const helpByPage = {
  dashboard: { title: "Farm overview", description: "Review farm-wide bird counts, production, feed, money, alerts, and recent changes. Figures are calculated from saved farm records.", nextLabel: "Review flocks", nextRoute: "flocks/batch-dashboard" },
  flocks: { title: "Flocks and housing", description: "Create houses or pens, open Layer or Broiler batches, inspect batch performance, and review bird costs. Create the house before opening a batch.", nextLabel: "Manage houses and batches", nextRoute: "flocks/houses" },
  "daily-work": { title: "Daily farm work", description: "Record feed usage, notes, water, mortality, and culling for the correct active batch and farm-local date. Feed usage deducts FIFO stock through the existing feed ledger; population totals are calculated from the event ledger.", nextLabel: "Open daily record", nextRoute: "daily-work/daily-record" },
  "feed-stock": { title: "Feed and stock", description: "Configure feed types, record purchases, review remaining feed stock, and track other farm supplies. Record daily feed consumption in Daily Work; usage allocates FIFO cost and is not a second purchase expense.", nextLabel: "Review feed and usage", nextRoute: "feed-stock/feed" },
  health: { title: "Flock health", description: "Keep health observations, treatments, and vaccination records tied to the correct batch and date. Record what was observed and what action was taken.", nextLabel: "Record a health observation", nextRoute: "health/health-record" },
  production: { title: "Production", description: "Record layer egg collection and quality, or broiler weights and growth. Use the workflow matching the selected batch type; incompatible records are not interchangeable.", nextLabel: "Open egg production", nextRoute: "production/eggs" },
  "sales-money": { title: "Sales and money", description: "Maintain reusable customer records, review the common sales ledger, and record farm or batch expenses. Enter amounts and dates from the original transaction.", nextLabel: "Review customers and sales", nextRoute: "sales-money/customers" },
  reports: { title: "Farm reports", description: "Review farm-wide or single-batch results for a selected as-of date, then export the same report data. Check the date before exporting historical results.", nextLabel: "Open farm report", nextRoute: "reports/farm-report" },
  settings: { title: "Farm settings", description: "Configure the farm profile, operating defaults, account, backups, and pricing preferences. Changes here affect how new records are entered or displayed.", nextLabel: "Review farm profile", nextRoute: "settings/farm" },
};
const tabHelp = {
  flocks: { houses: "Houses and pens define where birds are kept. Add a house before creating its first batch.", flocks: "Open a new permanent batch record for each flock. Do not reuse an old batch for a new placement.", "batch-dashboard": "Select a batch to review its population, costs, production, and current status." },
  "daily-work": { "daily-record": "Select the batch and date once, then record feed usage and daily notes. Feed consumption uses the existing FIFO feed ledger; water entries use configured container sizes and counts.", mortality: "Record birds that died separately from birds culled or sold.", culling: "Record intentional culling separately from mortality and sales.", water: "Enter configured container sizes and counts. The system calculates total water volume." },
  "feed-stock": { feed: "Configure feed units and record batch usage. Unknown kilogram conversions remain unknown rather than being guessed.", purchases: "Record purchase quantity, date, supplier, and total cost to increase stock.", inventory: "Record receipts and issues for non-feed supplies; use a reason for each movement.", suppliers: "Maintain supplier details and select registered suppliers in purchase workflows." },
  health: { "health-record": "Record symptoms, observations, and actions for a batch.", drugs: "Record treatments with their date, quantity, reason, and cost where applicable.", vaccination: "Record the vaccine, date, dose, quantity, and notes for the relevant batch." },
  production: { eggs: "Record good and cracked eggs separately. Cracked eggs remain in history but are not sellable.", broiler: "Record broiler sample weights and sales. Average weight and feed conversion are calculated by the backend." },
  "sales-money": { customers: "Create reusable customer records before linking sales where appropriate.", sales: "Review the common ledger for layer egg sales and broiler bird sales.", expenses: "Record each expense once with its original date, amount, category, and optional batch." },
  reports: { "farm-report": "This report combines relevant farm records for the selected as-of date.", "batch-report": "Select one batch to report only that batch's records for the selected as-of date." },
  settings: { farm: "Keep the farm name, location, timezone, and currency accurate.", "farm-defaults": "Set defaults such as water containers and crate size before relying on new entries.", application: "Manage account details, appearance, and the preferred start page.", backup: "Configure and test backups before relying on them for recovery.", pricing: "Review the target and working margin settings used by broiler pricing." },
};

export default function PageHelp({ page, tab, onStartTour }) {
  const help = helpByPage[page];
  if (!help) return null;
  const description = tabHelp[page]?.[tab] || help.description;

  return (
    <section className="page-help" aria-label={`Help for ${help.title}`}>
      <div className="page-help-copy">
        <p className="eyebrow">PAGE GUIDE</p>
        <strong>{help.title}</strong>
        <p>{description}</p>
      </div>
      <div className="page-help-actions">
        <button type="button" className="page-help-tour" onClick={onStartTour}>Start guided tour</button>
        <button type="button" className="page-help-action" onClick={() => { window.location.hash = help.nextRoute; }}>{help.nextLabel}</button>
      </div>
    </section>
  );
}
