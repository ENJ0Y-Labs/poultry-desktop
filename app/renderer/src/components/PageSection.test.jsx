import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { NavigationContext } from "./NavigationContext.jsx";
import PageSection from "./PageSection.jsx";

describe("PageSection", () => {
  it("renders content only for the active page", () => {
    render(
      <NavigationContext.Provider value={{ activePage: "flocks", activeTab: "houses" }}>
        <PageSection page="dashboard" tab="overview"><p>Dashboard content</p></PageSection>
        <PageSection page="flocks" tab="batch-dashboard"><p>Flock content</p></PageSection>
      </NavigationContext.Provider>,
    );
    expect(screen.queryByText("Dashboard content")).not.toBeInTheDocument();
    expect(screen.queryByText("Flock content")).not.toBeInTheDocument();
    expect(screen.getByText("Houses content")).toBeInTheDocument();
  });
});
