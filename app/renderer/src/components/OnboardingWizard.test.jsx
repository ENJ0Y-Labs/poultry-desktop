import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import OnboardingWizard from "./OnboardingWizard.jsx";

const farmForm = { name: "", location: "", timezone: "Africa/Lagos", currency: "NGN" };

describe("OnboardingWizard", () => {
  beforeEach(() => localStorage.clear());

  it("walks through the seven steps and creates the farm only at farm setup", async () => {
    const onSaveProfile = vi.fn().mockResolvedValue(true);
    const onCreateFarm = vi.fn().mockResolvedValue(true);
    const onComplete = vi.fn();
    const setFarmForm = vi.fn();
    render(<OnboardingWizard account={{ fullName: "Ada Farmer", email: "ada@example.com" }} farmForm={farmForm} setFarmForm={setFarmForm} onSaveProfile={onSaveProfile} onCreateFarm={onCreateFarm} onComplete={onComplete} saving={false} error="" />);

    expect(screen.getByRole("heading", { name: "Welcome to Pomas!" })).toBeInTheDocument();
    expect(screen.getByLabelText("First name")).toHaveValue("Ada");
    expect(screen.getByLabelText("Family name")).toHaveValue("Farmer");
    expect(screen.getByLabelText("Email address")).toHaveValue("ada@example.com");
    fireEvent.click(screen.getByRole("button", { name: /next/i }));
    await waitFor(() => expect(onSaveProfile).toHaveBeenCalledWith("Ada Farmer"));

    fireEvent.click(screen.getByLabelText("FARM MANAGER"));
    fireEvent.click(screen.getByRole("button", { name: /next/i }));
    fireEvent.click(screen.getByLabelText("WhatsApp"));
    fireEvent.click(screen.getByRole("button", { name: /next/i }));
    expect(screen.getByRole("heading", { name: "Howdy, Ada!" })).toBeInTheDocument();
    expect(onCreateFarm).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText("Farm name"), { target: { value: "Grantino Farms" } });
    fireEvent.click(screen.getByRole("button", { name: /create farm & continue/i }));
    await waitFor(() => expect(onCreateFarm).toHaveBeenCalled());
    expect(screen.getByRole("heading", { name: "What type of poultry do you manage?" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "BACK" }));
    fireEvent.click(screen.getByRole("button", { name: /continue/i }));
    expect(onCreateFarm).toHaveBeenCalledOnce();

    fireEvent.click(screen.getByLabelText("BROILERS"));
    fireEvent.click(screen.getByLabelText("LAYERS"));
    fireEvent.click(screen.getByRole("button", { name: /next/i }));
    fireEvent.click(screen.getByLabelText("DAILY FARM RECORDS"));
    fireEvent.click(screen.getByRole("button", { name: /finish setup/i }));
    expect(screen.getByRole("heading", { name: "Welcome to Pomas, Ada!" })).toBeInTheDocument();
    expect(localStorage.getItem("pomas:onboarding:v1")).toContain('"farmCreated":true');
    fireEvent.click(screen.getByRole("button", { name: /go to dashboard/i }));
    expect(onComplete).toHaveBeenCalledOnce();
    expect(localStorage.getItem("pomas:onboarding:v1")).toBeNull();
  });

  it("restores the saved step and selections after remounting", () => {
    localStorage.setItem("pomas:onboarding:v1", JSON.stringify({
      step: 5, firstName: "Ada", familyName: "Farmer", role: "FARM MANAGER",
      discovery: "WhatsApp", poultryTypes: ["TURKEYS"], priorities: [], farmCreated: true,\n      farmDraft: { name: "Saved Farm", location: "Port Harcourt", timezone: "Africa/Lagos", currency: "NGN" },
    }));
    render(<OnboardingWizard account={{ fullName: "Ada Farmer" }} farmForm={farmForm} setFarmForm={vi.fn()} onSaveProfile={vi.fn().mockResolvedValue(true)} onCreateFarm={vi.fn()} onComplete={vi.fn()} saving={false} error="" />);
    expect(screen.getByRole("heading", { name: "What type of poultry do you manage?" })).toBeInTheDocument();
    expect(screen.getByLabelText("TURKEYS")).toBeChecked();\n    expect(screen.getByRole("heading", { name: "Saved Farm" })).toBeInTheDocument();
  });
});
