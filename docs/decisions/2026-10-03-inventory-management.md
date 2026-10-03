# Decision: Stage 16 inventory management

Date: 2026-10-03

## Decision

Add a transaction-based inventory module for non-feed farm stock:

- drugs
- vaccines
- farm supplies

Inventory items are reusable definitions. Stock is never stored as a mutable balance. Current stock is derived from append-only inventory movements.

## Movement rules

Every new generic inventory movement requires:

- business date
- positive quantity
- movement type
- reason
- source
- optional batch association

Supported movement types are:

- RECEIVE
- ISSUE
- ADJUST_IN
- ADJUST_OUT
- WASTE

A stock-reducing movement is rejected when it would make calculated stock negative.

Items are archived rather than deleted. Movements are never deleted.

## Feed boundary

Feed already has a specialized inventory implementation with purchase lots, FIFO costing, feed usage, and bird-cost allocation. Replacing it with a second generic stock ledger would create two competing sources of truth.

Therefore Stage 16 keeps feed inventory authoritative in the existing feed subsystem. Feed usage now stores a reason, defaulting to "Production feed usage" for backwards compatibility.

The Inventory screen presents the generic inventory system while explicitly pointing operators to the Feed section for feed stock.

## Why

The farm needs one simple transaction discipline for drugs, vaccines, and supplies without destabilizing the already-tested feed cost engine.

The model also keeps future reporting possible because every generic stock change has an auditable reason and source.
