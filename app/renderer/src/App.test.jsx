import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import App from "./App.jsx";
import { farmApi } from "./services/api.js";

describe("App", () => {
  it("renders the farm setup screen when no farm exists", async () => {
    vi.spyOn(farmApi, "get").mockRejectedValue(
      new Error("No farm has been created yet.")
    );

    render(<App />);

    expect(screen.getByText("Loading farm...")).toBeInTheDocument();

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: /set up your farm/i })
      ).toBeInTheDocument();
    });

    expect(screen.getByText("GRANTINO FARMS")).toBeInTheDocument();
  });
});
