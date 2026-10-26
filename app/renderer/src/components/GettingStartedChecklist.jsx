import { useState } from "react";
import "./PageHelp.css";

const DISMISSED_KEY = "pomas:getting-started-dismissed";

export default function GettingStartedChecklist({ houses = [], batches = [], feedTypes = [], dashboard }) {
  const [dismissed, setDismissed] = useState(() => localStorage.getItem(DISMISSED_KEY) === "true");
  const stockMilli = Number(dashboard?.feedSummary?.remainingQuantityMilli || 0);
  const tasks = [
    { id: "house", title: "Create a house or pen", description: "Define where birds will be kept.", done: houses.length > 0, route: "flocks/houses", action: "Open houses" },
    { id: "batch", title: "Open a batch", description: "Record the flock type, placement date, and starting bird count.", done: batches.length > 0, route: "flocks/flocks", action: "Open batches" },
    { id: "feed-type", title: "Configure a feed type", description: "Add the feed types used by this farm.", done: feedTypes.some(item => item.status !== "ARCHIVED"), route: "feed-stock/feed", action: "Configure feed" },
    { id: "feed-stock", title: "Record a feed purchase", description: "Make sure a purchase has left positive stock available.", done: stockMilli > 0, route: "feed-stock/purchases", action: "Record purchase" },
  ];
  const completed = tasks.filter(task => task.done).length;
  if (dismissed || completed === tasks.length) return null;

  function dismiss() {
    localStorage.setItem(DISMISSED_KEY, "true");
    setDismissed(true);
  }

  return (
    <section className="card full getting-started-checklist" aria-labelledby="getting-started-title">
      <div className="section-head">
        <div>
          <p className="eyebrow">GETTING STARTED</p>
          <h3 id="getting-started-title">Finish setting up your farm</h3>
          <p className="muted">Progress follows saved farm data, not just the existence of a house.</p>
        </div>
        <div>
          <span className="getting-started-progress">{completed} of {tasks.length} complete</span>{" "}
          <button type="button" className="getting-started-dismiss" onClick={dismiss}>Dismiss</button>
        </div>
      </div>
      <ol className="getting-started-tasks">
        {tasks.map((task, index) => (
          <li className="getting-started-task" key={task.id}>
            <span className="task-status" aria-label={task.done ? "Complete" : "Not complete"}>{task.done ? "✓" : index + 1}</span>
            <div>
              <strong>{task.title}</strong>
              <p>{task.description}</p>
              {task.done
                ? <span className="muted">Completed</span>
                : <button type="button" onClick={() => { window.location.hash = task.route; }}>{task.action}</button>}
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}
