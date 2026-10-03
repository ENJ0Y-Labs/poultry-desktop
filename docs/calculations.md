# Calculations

The backend is the authoritative calculation engine. The renderer displays API results and does not implement independent farm formulas.

All historical calculations accept an `asOf` business date where the underlying operation supports historical reporting.

## Bird population

```
current birds =
initial birds
- mortality
- culling
- sold
+ transfers in
- transfers out
```

Population is reconstructed from authoritative population events.

Before a reducing event is committed, the service checks the population immediately before that event date. This prevents a record from creating a negative historical flock.

Reference vector:

```
5,000 - 83 - 20 - 70 + 25 - 10 = 4,842
```

## Livability and mortality

For the standard dashboard population vector:

```
livability % = current birds / initial birds × 100
```

Mortality rate is calculated from mortality events against the relevant initial/current flock population according to the service's reporting context.

The calculation layer is covered by unit and integration tests.

## Bird cost accounting

Money is stored as integer minor units. For NGN:

```
₦500 = 50,000 kobo
```

For a population reduction:

```
allocated cost =
carried cost × birds leaving / birds available
```

Integer arithmetic is used. Remainders remain in the carried cost pool until the relevant population is fully allocated.

A batch cost reconciliation is:

```
initial purchase cost
+ attributable additions
+ transfer-in cost
= allocated reduction cost
+ carried cost
```

## Feed inventory and FIFO

Feed quantity is stored as integer thousandths of the configured feed unit.

Feed purchases form cost lots. Usage consumes the oldest available lot first.

For a partial lot:

```
allocated feed cost =
remaining lot cost × quantity consumed / remaining lot quantity
```

A feed purchase creates one FEED expense. Later feed usage does not create another expense. Its FIFO cost is allocated to the consuming batch's bird-cost pool.

Feed chronology rules prevent historical inserts from silently changing already-accounted FIFO costs.

## Daily operations

Daily bird count comes from the population calculation for that date.

Water is calculated from recorded containers:

```
water units =
container capacity × container count
```

The UI does not invent a measured volume when the worker only recorded container counts.

## Health and drugs

Health and vaccination records are operational history.

A drug record creates one DRUGS expense:

```
drug expense = recorded drug cost
```

The same drug cost is not charged again when reports are read.

## Layer egg production

```
total collected = good collected + cracked collected
good remaining = good collected - good sold
```

Cracked eggs are not sellable inventory.

### Crate conversion

The farm's configured crate size is used at sale time:

```
sold eggs = crates sold × crate size
```

The sale stores the crate size used at that time.

### Egg sale amount

```
sale amount =
sold eggs × price per crate / crate size
```

Money remains integer minor units.

Back-dated sales are validated against the chronological egg ledger so a sale cannot create a negative historical sellable balance.

## Broiler weights and growth

Weight records store total sample weight in grams and sampled bird count.

```
average weight = total sample weight / sampled birds
```

For the batch-level live biomass summary:

```
current live biomass =
current live birds × latest average weight

starting live biomass =
initial birds × first recorded average weight

live-weight gain =
current live biomass - starting live biomass
```

## FCR

The authoritative broiler FCR is:

```
FCR = feed consumed (kg) / live-weight gain (kg)
```

Known vector:

```
400 kg / 200 kg = 2.0
```

If live-weight gain is zero or negative, FCR is undefined and the API returns null.

Only feed usage whose configured unit is kg contributes to FCR until a canonical conversion for other units exists.

## Broiler sales and revenue

Broiler population reduction from a sale is represented by a SOLD population event.

```
sale revenue = quantity × price per bird
```

When the calculated current population reaches zero, an active Broiler batch transitions to SOLD and ordinary production/population writes are locked.

## Pricing and margins

Actual margin:

```
margin % =
(sale revenue - attributable bird cost)
÷ sale revenue × 100
```

Target price for a target margin:

```
target price =
actual cost
÷ (1 - target margin / 100)
```

The backend rounds the required selling price upward to the next whole minor currency unit so currency precision does not undercut the requested margin.

A below-target Broiler sale requires explicit operator confirmation.

## Expenses and profit

Expenses are integer minor currency amounts.

Farm-level expenses have no batch association. Batch expenses carry a batch ID.

Profit is derived from authoritative revenue and cost/expense records rather than stored as a mutable database balance:

```
profit = revenue - attributable costs - relevant expenses
```

The exact report/dashboard breakdown is assembled by backend services.

## Inventory

For non-feed inventory:

```
stock =
received
+ adjustment in
- issued
- adjustment out
- waste
```

Every movement quantity is positive. The movement type supplies the direction.

Stock-reducing movements are rejected when:

```
quantity > current stock
```

Low stock is descriptive:

```
low stock when stock on hand <= reorder level
```

Feed remains on the dedicated FIFO path.

## Dashboard and reports

Dashboards and reports do not duplicate formulas.

The farm report and batch report reuse the same calculation/domain services as the dashboard services. Exporting CSV or PDF therefore does not create a second financial or production calculation path.

## Attention thresholds

Backend-derived attention rules currently include:

- Low feed stock: roughly three days of recent usage cover or less
- Insufficient inventory: zero/negative stock
- Low inventory: stock at or below reorder level
- High mortality: 5% warning, 10% critical
- Missing daily record: active batch has no record for the requested date
- Vaccination due: active batch is at least 14 days old with no vaccination in the prior 28 days
- Unusual production drop: 20% warning, 30% critical compared with the previous seven-day production period
- Broiler nearing sale: warning at 35 days, target sale age 42 days, critical at 49 days
- Backup overdue: automatic backup enabled and no recent backup within seven days

These thresholds are centralized in the backend attention service. The frontend does not invent alerts.

## Settings and calculation integrity

Settings that affect calculations are audited. Historical records preserve values needed to interpret the record at the time it was created, such as the crate size used for an egg sale.

Changing configuration must not rewrite historical transactions.
