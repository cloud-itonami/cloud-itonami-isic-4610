# cloud-itonami-isic-4610

Open Business Blueprint for **ISIC Rev.5 4610**: Wholesale on a Fee or
Contract Basis -- mandate intake, per-jurisdiction principal-diligence /
dual-agency / sanctions regulatory verification, deal confirmation
between two principals, and commission-invoice settlement for a
commission broker / trade intermediary.

This repository publishes a commission-brokerage actor -- mandate
intake, per-jurisdiction commercial-agency / sanctions regulatory
verification, deal confirmation and commission-invoice settlement -- as
an OSS business that any qualified operator can fork, deploy, run,
improve and sell, so a regional commission broker never surrenders
principal, mandate, credit and sanctions data to a closed brokerage /
CRM SaaS.

**What is a "commission broker" (ISIC 4610)?** ISIC 4610 is the
classification for commission agents / trade brokers / wholesale
intermediaries who arrange the sale or purchase of goods on behalf of
others for a fee or commission, **WITHOUT ever taking ownership/title
of the goods themselves**. This is the single structural fact that
distinguishes this vertical from its two siblings already registered
in this fleet: `cloud-itonami-isic-4671` (FuelTradeAdvisor, wholesale
of solid/liquid/gaseous fuels) and `cloud-itonami-isic-4690`
(ShoshaAdvisor, non-specialized wholesale trade) -- BOTH of those take
PRINCIPAL positions (they buy, then resell; invoice settlement is
their own money moving). A commission broker is engaged by one or both
principals (buyer-side, seller-side, or both -- a "dual mandate") to
arrange a deal for a fee; the actual sale settles **directly between
the two principals**, and the broker's own actuation is arranging /
confirming the deal and invoicing its OWN commission, never settling
the underlying trade.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet, including the fuel-wholesale and
general-trading siblings -- here it is **CommTradeAdvisor ⊣ Commission
Broker Governor**. This blueprint's own
`:itonami.blueprint/governor` keyword, `:commission-broker-governor`,
is a UNIQUE keyword fleet-wide (grep-verified: no other blueprint
declares it) -- a fresh, independent build.

**Like the fuel-wholesale and general-trading siblings, this vertical
is SELF-CONTAINED**: there is no `kotoba-lang/commtrade` to delegate
brokerage validation to, so the mandate-on-file / principal-identity /
dual-agency-disclosure / sanctions-screening checks live as direct
entity boolean reads in `commtrade.governor` (off dedicated
`:mandate-terms` / `:buyer-kyc-cleared?` / `:seller-kyc-cleared?` /
`:dual-agency-disclosed?` / `:buyer-sanctions-screened?` / `:seller-
sanctions-screened?` facts on the `brokerage-deal` record), rather
than wrapping an external capability library's own validated
function.

> **Why an actor layer at all?** An LLM is great at drafting a mandate
> summary, normalizing records, and reading a KYC file -- but it has
> **no notion of which jurisdiction's commercial-agency / brokerage
> law is official, no license to confirm a real deal between two
> principals or invoice a real commission, and no way to know on its
> own whether both principals' identity has actually been verified,
> whether a mandate/engagement letter is actually on file, whether
> acting for BOTH sides of the same deal has actually been disclosed
> and consented to, or whether OFAC / equivalent sanctions screening
> has actually been passed for BOTH principals**. Letting it confirm a
> deal or invoice a commission directly invites fabricated regulatory
> citations, a deal confirmed for an unverified or unscreened
> principal, and -- the classic brokerage-specific failure -- an
> undisclosed conflict of interest where the broker silently
> represents both sides of the same deal, exposing the operator to
> real enforcement, fiduciary-breach and financial liability. This
> project seals the CommTradeAdvisor into a single node and wraps it
> with an independent **Commission Broker Governor**, a human
> **approval workflow**, and an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers mandate intake through per-jurisdiction commercial-
agency / dual-agency / sanctions regulatory verification, deal
confirmation between two principals, and commission-invoice
settlement. It does **not**, by itself, hold any brokerage licence or
operating authority required to run a commission-brokerage business in
a given jurisdiction, and it does not claim to. It also does not
perform or arrange the actual physical delivery of goods or the
underlying trade's payment settlement between the two principals --
those happen directly between the principals, entirely outside this
actor's operating boundary -- and it does not judge deal-matching
economics: principal-matching / deal-sourcing optimization (the
blueprint's own `:optimization` technology) is a follow-up slice, not
in this R0. Whoever deploys and operates a live instance (a qualified
trading supervisor) supplies any jurisdiction-specific operating
authority, the real KYC / sanctions-screening vendor integrations, and
bears that jurisdiction's liability -- the software supplies the
governed, spec-cited, audited execution scaffold so that operator does
not have to build the compliance layer from scratch.

### Actuation

**Confirming a real matched deal between two principals and settling a
real commission invoice are never autonomous, at any phase, by
construction.** Two independent layers enforce this
(`commtrade.governor`'s `:deal/confirm`/`:commission/invoice`
high-stakes gate and `commtrade.phase`'s phase table, which never puts
either op in any phase's `:auto` set) -- see `commtrade.phase`'s
docstring and `test/commtrade/phase_test.clj`'s
`deal-confirm-never-auto-at-any-phase`/
`commission-invoice-never-auto-at-any-phase`. The actor may draft,
check and recommend; a human trading supervisor is always the one who
actually confirms a deal or settles a commission invoice. Grounded in
commercial-agency doctrine (the same discipline every regulator in
`commtrade.facts` codifies: a real deal confirmation and a real
commission-invoice settlement are human sign-off acts) -- a genuine
DUAL-actuation shape, applied SEQUENTIALLY to the SAME brokerage-deal
(confirmation first, commission-invoice settlement later), the same
sequential shape the fuel-wholesale and general-trading siblings use.
**Critically, `:deal/confirm` itself never moves goods or money
between the two principals** -- they settle the underlying trade
directly between themselves; the broker's actuation is its OWN record
of arranging/confirming the deal and, later, invoicing its OWN fee.

## The core contract

```
mandate intake + jurisdiction facts (commtrade.facts, spec-cited)
        |
        v
   ┌───────────────────────┐   proposal      ┌───────────────────────┐
   │ CommTradeAdvisor       │ ─────────────▶ │ Commission Broker      │  (independent system)
   │ (sealed)               │  + citations    │ Governor -- spec-basis │
   └───────────────────────┘                 │ · evidence-incomplete ·│
          │                 commit ◀┼ mandate-missing · principal-  │
          │                         │ identity-unverified · conflict-│
    record + ledger        escalate ┼ of-interest-undisclosed ·      │
          │              (ALWAYS for│ counterparty-sanctions-flag-   │
          │       :deal/confirm/    │ unresolved · already-confirmed │
          │       :commission/      │ · already-invoiced             │
          │       invoice)          └───────────────────────┘
          ▼
      human approval
```

**The CommTradeAdvisor never confirms a deal between two principals or
settles a commission invoice the Commission Broker Governor would
reject, and never does so without a human sign-off.** Hard violations
(fabricated regulatory requirements; unsupported evidence; no mandate
on file; either principal's identity unverified; an undisclosed
dual-agency conflict of interest; an unresolved sanctions-screening
flag on either principal; a double confirmation/invoice) force **hold**
and *cannot* be approved past; a clean confirmation/invoice proposal
still always routes to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean deal-confirmation + commission-invoice lifecycle, plus six HARD-hold cases, through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

Like the general-trading sibling (`cloud-itonami-isic-4690`) and
unlike the fuel-wholesale sibling's own loading-rack valve robot, **this
actor sets `:itonami.blueprint/robotics false` and omits `:robotics`
from `:required-technologies` entirely.** A commission broker has an
even weaker physical-domain claim than a general/diversified trading
house: it never even arranges the cross-border shipment itself (that
is the general-trading sibling's `:shipment/dispatch` logistics-
coordination referral) -- a commission broker's entire actuation
surface is arranging and confirming a deal on paper/systems and
invoicing its own fee, with the underlying goods and payment moving
directly between the two principals. There is no fixed physical asset,
no logistics-coordination referral, and no robot this actor's governor
is in a position to gate. This follows real, existing precedent in
this fleet: `cloud-itonami-isic-4690` sets `robotics false` for the
same reasoning shape, itself following `cloud-itonami-isic-6910`
(Global Incorporation Actor) and `cloud-itonami-isic-6310` (HR SaaS)'s
own precedent (ADR-2607011000). See `docs/business-model.md`'s
"Robotics Premise" section and `docs/adr/0001-architecture.md`
Decision 10 for the full reasoning.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Commission Broker Governor, deal-confirmation/commission-invoice draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`4610`). Like the fuel-wholesale and general-trading siblings, this
vertical is NOT backed by a separate bespoke domain capability lib: the
commission-brokerage checks (mandate-on-file, principal-identity,
dual-agency disclosure, sanctions-screening) are direct entity boolean
reads in `commtrade.governor`, on top of the generic identity/forms/
dmn/bpmn/audit-ledger stack -- with NO `:robotics` in the stack at all
(see Robotics premise, above).

## Layout

| File | Role |
|---|---|
| `src/commtrade/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + confirmation AND invoice history (dual history). The double-actuation guard checks dedicated `:confirmed?`/`:invoiced?` booleans rather than a `:status` value |
| `src/commtrade/registry.cljc` | Deal-confirmation/commission-invoice draft records (record construction only -- the Commission Broker Governor's checks are direct entity booleans, so there are no pure range-check functions to host here) |
| `src/commtrade/facts.cljc` | Per-jurisdiction commercial-agency / dual-agency / sanctions catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/commtrade/commtradeadvisor.cljc` | **CommTradeAdvisor** -- `mock-advisor` ‖ `llm-advisor`; intake/mandate-verification/confirm/invoice proposals |
| `src/commtrade/governor.cljc` | **Commission Broker Governor** -- 6 HARD checks (spec-basis · evidence-incomplete · mandate-missing · principal-identity-unverified · conflict-of-interest-undisclosed · counterparty-sanctions-flag-unresolved) + 2 double-actuation guards + 1 soft (confidence/actuation gate) |
| `src/commtrade/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted verify → supervised (confirm/invoice always human; mandate intake is the ONLY auto-eligible op, no direct fiduciary/sanctions risk) |
| `src/commtrade/operation.cljc` | **OperationActor** -- langgraph StateGraph |
| `src/commtrade/sim.cljc` | demo driver |
| `test/commtrade/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers mandate intake through commercial-agency / dual-
agency / sanctions regulatory verification, deal confirmation and
commission-invoice settlement -- the core governed lifecycle:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Mandate intake + per-jurisdiction evidence checklisting, HARD-gated on an official spec-basis citation (`:mandate/intake`/`:mandate/verify`) | Real principal-matching / deal-sourcing optimization and KYC/sanctions-vendor integration |
| Deal confirmation, HARD-gated on full evidence, a mandate on file, both principals' identity verified, disclosed dual-agency consent (when applicable) and a passed sanctions screen on both principals, with no double-confirmation (`:deal/confirm`) | |
| Commission-invoice settlement, HARD-gated on full evidence, a passed sanctions screen on both principals and no double-invoice (`:commission/invoice`) | |
| Immutable audit ledger for every intake/verification/confirmation/invoice decision | |

Extending coverage is additive: add the next gate (e.g. a compliance-
hold reconciliation check) as its own governed op with its own HARD
checks and tests, following the SAME "an independent governor
re-verifies against the actor's own records before any real-world act"
pattern this repo's flagship ops already establish.

## Jurisdiction coverage (honest)

`commtrade.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `commtrade.facts/catalog` --
currently 4 seeded (JPN, GBR, DEU, USA) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. **The USA entry is
honestly scoped**: it cites the Perishable Agricultural Commodities Act
(PACA), which federally licenses "commission merchants, dealers, and
brokers" of perishable agricultural commodities by name -- a real,
verifiable regime for exactly this business model, but for ONE
commodity category, not a claim that all US commission-agent wholesale
trade is federally licensed this way. Adding a jurisdiction is
additive: one map entry in `commtrade.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to
make coverage look bigger.

## Maturity

`:implemented` -- `CommTradeAdvisor` + `Commission Broker Governor` run
as real, tested code (see `Run` above), following the SAME
governed-actor architecture as the other prior actors across this
fleet, with its own distinct, independently-named governor and its own
direct-entity-boolean commission-brokerage checks (including the
conflict-of-interest-undisclosed check with no analog in either
principal-trading sibling). See `docs/adr/0001-architecture.md` for the
design.

## License

Code and implementation templates are AGPL-3.0-or-later.
