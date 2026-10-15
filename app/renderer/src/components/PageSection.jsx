import { useContext } from "react";
import { NavigationContext } from "./NavigationContext.jsx";

export default function PageSection({ page, tab, children }) {
  const { activePage, activeTab } = useContext(NavigationContext);
  if (activePage !== page || (tab && activeTab !== tab)) return null;
  return children;
}
