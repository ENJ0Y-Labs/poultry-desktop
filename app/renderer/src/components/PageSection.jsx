import { useContext } from "react";
import { NavigationContext } from "./NavigationContext.jsx";

/**
 * Keeps route visibility rules in one place. Page content remains mounted only
 * when its canonical page and optional tab are active.
 */
export default function PageSection({ page, tab, children }) {
  const { activePage, activeTab } = useContext(NavigationContext);
  if (activePage !== page || (tab && activeTab !== tab)) return null;
  return children;
}
