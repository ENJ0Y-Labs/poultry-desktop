# Feed product packaging metadata

V30 adds optional brand, packaging name, and standard package weight in kilograms to feed definitions. Package weight is nullable and must only be populated from a confirmed product label or supplier record. Product names and brands are recorded separately. No bird applicability is inferred from ambiguous product names.

Existing feed purchases and usage remain untouched by this migration. The current feed ledger stores quantities in each feed type's configured unit with three decimal places and uses the configured kg-per-unit conversion for kilogram-based reporting. This change does not yet change the authoritative ledger's base unit; a future conversion must migrate historical purchases, usage, FIFO costing, FCR, and batch cost together, not just relabel existing quantities.
