import { useContext } from "react";
import { NavigationContext } from "./NavigationContext.jsx";

export default function PageSection({ page, children }) {
  const { activePage } = useContext(NavigationContext);
  if (activePage !== page) return null;
  return children;
}
