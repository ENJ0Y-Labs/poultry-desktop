# Feed product packaging metadata

V30 adds optional brand, packaging name, and standard package weight in kilograms to feed definitions. Package weight is nullable and must only be populated from a confirmed product label or supplier record. Product names and brands are recorded separately. No bird applicability is inferred from ambiguous product names.

Existing feed purchases and usage remain untouched by this migration. The current feed ledger stores quantities in each feed type's configured unit with three decimal places and uses the configured kg-per-unit conversion for kilogram-based reporting. This change does not yet change the authoritative ledger's base unit; a future conversion must migrate historical purchases, usage, FIFO costing, FCR, and batch cost together, not just relabel existing quantities.


## Brand-specific product identity and editing

V33 replaces the legacy farm/name uniqueness rule with a brand-aware identity rule: feed name plus brand must be unique within a farm. A blank or NULL brand is one unbranded identity. FeedService trims and normalizes name/brand for duplicate validation. The database's exact unique index preserves legacy records that may differ only by casing rather than silently rewriting or merging them. Two different brands may therefore use the same generic feed name without sharing a feed type ID.

Feed product identity edits update only the existing feed definition row. They must not create a new feed type or rewrite feed purchases, opening balances, feed usage, expenses, FIFO lots, or batch costs. Unit and bird applicability are intentionally not part of this descriptive edit because changing either would reinterpret existing ledger records. Every identity edit is audited with before/after snapshots.

Where a historical feed CSV uses a name shared by multiple brands, `feed_type_brand` is required to resolve the correct permanent feed type ID. Unknown package weight remains NULL; do not infer it from brand, product name, package label text, or a similar product.
