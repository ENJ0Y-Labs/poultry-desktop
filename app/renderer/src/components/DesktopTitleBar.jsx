import { useEffect, useRef, useState } from "react";
import pomasLogo from "../assets/pomas-logo.png";
import "./DesktopTitleBar.css";

const farmDestinations = [
  ["Dashboard", "#dashboard/overview"],
  ["Flocks", "#flocks/batch-dashboard"],
  ["Daily Work", "#daily-work/daily-record"],
  ["Feed & Stock", "#feed-stock/feed"],
  ["Health", "#health/health-record"],
  ["Production", "#production/eggs"],
  ["Sales & Money", "#sales-money/sales"],
  ["Reports", "#reports/farm-report"],
  ["Settings", "#settings/farm"],
];

export default function DesktopTitleBar({ onNavigate, onQuickActions, onToggleSidebar, onToggleAttention, onShortcuts, onTutorials, sidebarCollapsed, attentionCollapsed }) {
  const [openMenu, setOpenMenu] = useState("");
  const rootRef = useRef(null);
  const menuRef = useRef(null);

  const menus = [
    { label: "File", items: [
      { label: "New record", action: onQuickActions, shortcut: "Ctrl+K" },
      { label: "Import / Export", action: () => onNavigate("#import-export/overview") },
      { label: "Backup & restore", action: () => onNavigate("#settings/backup") },
      { separator: true },
      { label: "Quit Pomas", action: () => window.poultryDesktop?.quit?.() },
    ] },
    { label: "Edit", items: [
      { label: "Undo", command: "undo", shortcut: "Ctrl+Z" },
      { label: "Redo", command: "redo", shortcut: "Ctrl+Y" },
      { separator: true },
      { label: "Cut", command: "cut", shortcut: "Ctrl+X" },
      { label: "Copy", command: "copy", shortcut: "Ctrl+C" },
      { label: "Paste", command: "paste", shortcut: "Ctrl+V" },
      { label: "Select all", command: "selectAll", shortcut: "Ctrl+A" },
    ] },
    { label: "View", items: [
      { label: sidebarCollapsed ? "Expand navigation" : "Collapse navigation", action: onToggleSidebar },
      { label: attentionCollapsed ? "Expand attention panel" : "Collapse attention panel", action: onToggleAttention },
      { label: "Keyboard shortcuts", action: onShortcuts },
    ] },
    { label: "Farm", items: farmDestinations.map(([label, target]) => ({ label, action: () => onNavigate(target) })) },
    { label: "Tools", items: [
      { label: "Quick actions", action: onQuickActions, shortcut: "Ctrl+K" },
      { label: "Keyboard shortcuts", action: onShortcuts },
    ] },
    { label: "Help", items: [
      { label: "Tutorials & Help", action: onTutorials },
      { label: "Application settings", action: () => onNavigate("#settings/application") },
      { label: "Keyboard shortcuts", action: onShortcuts },
    ] },
  ];

  useEffect(() => {
    if (!openMenu) return undefined;
    const closeOutside = event => {
      if (!rootRef.current?.contains(event.target)) setOpenMenu("");
    };
    document.addEventListener("pointerdown", closeOutside);
    return () => document.removeEventListener("pointerdown", closeOutside);
  }, [openMenu]);

  useEffect(() => {
    menuRef.current?.querySelector('[role="menuitem"]')?.focus();
  }, [openMenu]);

  function activate(item) {
    setOpenMenu("");
    if (item.command) {
      document.execCommand(item.command);
      return;
    }
    item.action?.();
  }

  function handleMenuKeyDown(event) {
    if (event.key === "Escape") {
      event.preventDefault();
      setOpenMenu("");
      rootRef.current?.querySelector(`[data-menu="${openMenu}"]`)?.focus();
      return;
    }
    if (!["ArrowDown", "ArrowUp"].includes(event.key)) return;
    const items = [...(menuRef.current?.querySelectorAll('[role="menuitem"]') || [])];
    if (!items.length) return;
    event.preventDefault();
    const current = items.indexOf(document.activeElement);
    const step = event.key === "ArrowDown" ? 1 : -1;
    items[(current + step + items.length) % items.length].focus();
  }

  return <div className="desktop-titlebar" ref={rootRef}>
    <div className="desktop-titlebar-brand" aria-label="Pomas application">
      <img src={pomasLogo} alt="" draggable="false" />
      <strong>Pomas</strong>
    </div>
    <nav className="desktop-menu-bar" aria-label="Application menu" onKeyDown={handleMenuKeyDown}>
      {menus.map(menu => <div className="desktop-menu" key={menu.label}>
        <button type="button" className={openMenu === menu.label ? "desktop-menu-trigger open" : "desktop-menu-trigger"} data-menu={menu.label} aria-haspopup="menu" aria-expanded={openMenu === menu.label} onMouseEnter={() => { if (openMenu) setOpenMenu(menu.label); }} onClick={() => setOpenMenu(value => value === menu.label ? "" : menu.label)}>{menu.label}</button>
        {openMenu === menu.label && <div className="desktop-menu-popup" role="menu" aria-label={`${menu.label} menu`} ref={menuRef}>
          {menu.items.map((item, index) => item.separator
            ? <div className="desktop-menu-separator" role="separator" key={`separator-${index}`} />
            : <button type="button" role="menuitem" key={item.label} onClick={() => activate(item)}>
              <span>{item.label}</span>{item.shortcut && <kbd>{item.shortcut.replace("Ctrl", "⌃")}</kbd>}
            </button>)}
        </div>}
      </div>)}
    </nav>
    <div className="desktop-titlebar-drag" aria-hidden="true" />
  </div>;
}
