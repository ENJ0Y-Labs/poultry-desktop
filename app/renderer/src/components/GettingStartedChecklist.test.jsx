import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import GettingStartedChecklist from "./GettingStartedChecklist.jsx";
import PageHelp from "./PageHelp.jsx";

beforeEach(() => {
  localStorage.clear();
  window.location.hash = "";
});

describe("GettingStartedChecklist", () => {
  it("derives completion from farm setup data rather than house existence alone", () => {
    render(<GettingStartedChecklist houses={[{ id: "h1" }]} dashboard={{ feedSummary: { remainingQuantityMilli: 0 } }} />);
    expect(screen.getByText("1 of 4 complete")).toBeInTheDocument();
    expect(screen.getByText("Open a batch")).toBeInTheDocument();
    expect(screen.getByText("Configure a feed type")).toBeInTheDocument();
    expect(screen.getByText("Record a feed purchase")).toBeInTheDocument();
  });

  it("hides when all setup tasks are complete", () => {
    const { container } = render(<GettingStartedChecklist
      houses={[{ id: "h1" }]}
      batches={[{ id: "b1" }]}
      feedTypes={[{ id: "f1", status: "ACTIVE" }]}
      dashboard={{ feedSummary: { remainingQuantityMilli: 2500 } }}
    />);
    expect(container).toBeEmptyDOMElement();
  });

  it("stays visible until dismissed and remembers the dismissal", () => {
    const { rerender, container } = render(<GettingStartedChecklist />);
    fireEvent.click(screen.getByRole("button", { name: "Dismiss" }));
    expect(container).toBeEmptyDOMElement();
    expect(localStorage.getItem("pomas:getting-started-dismissed")).toBe("true");
    rerender(<GettingStartedChecklist houses={[{ id: "h1" }]} />);
    expect(container).toBeEmptyDOMElement();
  });
});

describe("PageHelp", () => {
  it("explains a page and routes to a useful next action", () => {
    render(<PageHelp page="health" tab="vaccination" />);
    expect(screen.getByText(/Record the vaccine, date, dose, quantity, and notes/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Record a health observation" }));
    expect(window.location.hash).toBe("#health/health-record");
  });

  it("renders guidance for all top-level pages", () => {
    for (const page of ["dashboard", "flocks", "daily-work", "feed-stock", "health", "production", "sales-money", "reports", "settings"]) {
      const { unmount } = render(<PageHelp page={page} tab="overview" />);
      expect(screen.getByLabelText(/Help for/)).toBeInTheDocument();
      unmount();
    }
  });
});
