import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
// eslint-disable-next-line no-unused-vars
import { NavigationContext } from "./NavigationContext.jsx";
// eslint-disable-next-line no-unused-vars
import PageSection from "./PageSection.jsx";

describe("PageSection", () => {
  it("renders only the active page and tab", () => {
    render(
      <NavigationContext.Provider value={{ activePage: "flocks", activeTab: "houses" }}>
        <PageSection page="dashboard" tab="overview"><p>Dashboard content</p></PageSection>
        <PageSection page="flocks" tab="batch-dashboard"><p>Flock dashboard content</p></PageSection>
        <PageSection page="flocks" tab="houses"><p>Houses content</p></PageSection>
      </NavigationContext.Provider>,
    );
    expect(screen.queryByText("Dashboard content")).toBeNull();
    expect(screen.queryByText("Flock dashboard content")).toBeNull();
    expect(screen.getByText("Houses content")).not.toBeNull();
  });
});
