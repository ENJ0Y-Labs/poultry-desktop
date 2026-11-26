import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
// eslint-disable-next-line no-unused-vars
import UserManagementPage from "./UserManagementPage.jsx";
import { adminUserApi } from "../services/api.js";

vi.mock("../services/api.js", () => ({
  adminUserApi: {
    list: vi.fn().mockResolvedValue([{ id: "worker-1", username: "worker", fullName: "Worker One", role: "WORKER", status: "ACTIVE" }]),
    create: vi.fn().mockResolvedValue({}),
    changeRole: vi.fn().mockResolvedValue({}),
    changeStatus: vi.fn().mockResolvedValue({}),
    resetPassword: vi.fn().mockResolvedValue({}),
  },
}));

describe("UserManagementPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    adminUserApi.list.mockResolvedValue([{ id: "worker-1", username: "worker", fullName: "Worker One", role: "WORKER", status: "ACTIVE" }]);
  });

  it("creates an individual worker account with the selected role", async () => {
    render(<UserManagementPage currentUser={{ id: "admin-1", role: "ADMINISTRATOR" }} />);
    await screen.findByText("Worker One");
    fireEvent.change(screen.getByLabelText("Full name"), { target: { value: "New Worker" } });
    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "new-worker" } });
    fireEvent.change(screen.getByLabelText("Temporary password"), { target: { value: "a-secure-password" } });
    fireEvent.click(screen.getByRole("button", { name: "Create user" }));
    await waitFor(() => expect(adminUserApi.create).toHaveBeenCalledWith(expect.objectContaining({
      fullName: "New Worker", username: "new-worker", role: "WORKER", password: "a-secure-password",
    })));
  });
});
