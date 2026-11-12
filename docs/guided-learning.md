# Guided learning

The Help menu and contextual page guide open a searchable workflow library for batch setup, feed setup and purchases, mortality, egg collection, and sales. Each guide navigates to the real Pomas page, highlights its section, explains the workflow in short steps, and ends with a completion summary and an explicit **Try with farm data** action. The existing full application tour remains available from contextual page help.

## Data safety boundary

Guided learning is currently a non-persisting practice walkthrough, not a second farm environment or demo database. Tutorial examples are instructional text only. The walkthrough does not populate forms, call write APIs, create synthetic records, or submit anything. Choosing **Try with farm data** closes the guide and leaves the operator on the relevant real workflow; the operator then enters and saves their own farm data deliberately.

Do not add seeded records or auto-submit tutorial workflows to the farm database. If a future release adds hands-on demo records, it must first provide a separately configured, disposable backend/database and an explicit, verifiable boundary from the user's farm database.

The library and walkthrough work offline and add no service or dependency.
