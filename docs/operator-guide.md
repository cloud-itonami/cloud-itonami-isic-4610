# Operator Guide

## First Deployment
1. Register brokers, trading supervisors, and both principals'
   directories.
2. Import mandate, principal, KYC, credit and sanctions history.
3. Seed the per-jurisdiction spec-basis catalog (`commtrade.facts`) for
   the jurisdictions you actually broker deals in, citing real official
   sources only.
4. Run read-only spec-basis validation per jurisdiction.
5. Configure dual-agency and sanctions escalation and commission
   accounts-receivable accounts.
6. Publish a dry-run confirmation/commission-invoice and audit export.

## Minimum Trading Controls
- spec-basis validation before any verification, confirmation, or
  invoice
- full counterparty-diligence + dual-agency evidence (mandate/
  engagement-letter record, both principals' KYC records, sanctions-
  screening record covering both principals, dual-agency disclosure/
  consent record) before any confirmation
- mandate-on-file, both-principals-identity and dual-agency-disclosure
  checks before any confirmation; sanctions-screening (both principals)
  before any confirmation AND before any invoice
- conflict-of-interest / sanctions escalation gate
- audit export for every confirmation, invoice, and hold
- backup manual confirmation and invoicing process

## A Day in the Life: Intake → Verify → Confirm → Invoice → Audit

Wholesale on a Fee or Contract Basis (ISIC 4610,
`cloud-itonami-isic-4610`) runs on the same intake / advise / govern /
decide / commit-or-hold loop as every itonami blueprint, but here the
loop is concrete: a commission broker needs to bring a brokerage-deal
(say, a 5,000-MT steel-coil sale, brokered on a seller-side mandate
between a buyer in Japan and a seller also in Japan) from intake
through mandate verification to a deal confirmation and a commission-
invoice settlement. Walking through one deal, end to end:

1. **Intake.** The broker books the brokerage-deal through `:forms`:
   deal-id, subject-matter, both principals, commission-rate,
   mandate-side (who engaged the broker: buyer-side, seller-side, or
   dual), jurisdiction, and the deal's own diligence record (buyer-
   kyc-cleared?, seller-kyc-cleared?, sanctions-screened flags for
   both principals). This creates a brokerage-deal record at
   `:mandate/intake` status. The CommTradeAdvisor only normalizes the
   patch; it does not invent the deal-id, either principal, the
   jurisdiction, the mandate side, or any commercial/diligence value.
2. **Verify.** The CommTradeAdvisor drafts a per-jurisdiction
   commercial-agency / dual-agency / sanctions evidence checklist
   (`:mandate/verify`) from `commtrade.facts`, citing the
   jurisdiction's official spec-basis (owner authority, legal basis,
   provenance) and listing the required evidence (mandate/engagement-
   letter record, both principals' KYC records, sanctions-screening
   record covering both principals, dual-agency disclosure/consent
   record). The `:commission-broker-governor` sign-off gate must
   clear: it checks the jurisdiction actually has an official
   spec-basis on file (never invent one). A jurisdiction with no
   spec-basis is a HARD hold at the governor node -- it never even
   reaches a human. This verification always escalates to a human for
   approval; it is never auto.
3. **Confirm.** Before a deal can be recorded as confirmed, the
   `:commission-broker-governor` sign-off gate runs the full HARD
   check set against the deal's own ground truth: the spec-basis
   exists, the evidence checklist is complete, a mandate/engagement-
   letter is on file, BOTH principals' identity has been verified, a
   dual-agency mandate (if any) has been disclosed and consented to,
   BOTH principals have passed sanctions screening, and the deal has
   not already been confirmed. Any failure is a HARD hold that a human
   cannot override. If every check is clean, the proposal STILL always
   escalates to a human trading supervisor -- a `:deal/confirm` never
   auto-commits at any phase. **Confirming a deal does NOT move the
   underlying goods or the principals' money -- the two principals
   settle the underlying trade directly between themselves.** On
   approval, the deal-confirmation record is drafted
   (`<JURISDICTION>-CONFIRM-000001`) and the deal's `:confirmed?` flag
   is set.
4. **Invoice.** Once a deal has actually been confirmed, the broker's
   OWN commission is invoiced (`:commission/invoice`): the broker's own
   fee, payable by whichever principal(s) engaged it per the mandate
   terms -- never the underlying trade's own settlement. The governor
   re-checks the spec-basis, the evidence completeness, sanctions
   screening on both principals, and that this deal's commission has
   not already been invoiced. As with the confirmation, a clean
   commission invoice STILL always escalates to a human trading
   supervisor -- `:commission/invoice` never auto-commits. On approval
   the commission-invoice record is drafted
   (`<JURISDICTION>-COMMISSION-000001`) and the deal's `:invoiced?`
   flag is set.
5. **Audit.** The verification, the confirmation sign-off, the
   confirmation record, the invoice sign-off, and the invoice record
   are all appended to the `:audit-ledger` -- immutable and
   exportable, so a principal or regulatory dispute can be traced back
   to the exact spec-basis citation, evidence checklist, and
   supervisor sign-off that authorized the confirmation and invoice.
   If something is wrong with either principal (an unverified
   identity, a sanctions hit, an undisclosed dual-agency conflict of
   interest), that gets raised as a flag and routed through the
   escalation gate instead of being silently suppressed -- a
   confirmation for that deal then waits on governor sign-off of the
   flag's resolution.

Any deviation from this loop is exactly what the Trust Controls in
`docs/business-model.md` exist to catch: a deal verified against a
fabricated spec-basis, a confirmation started with incomplete
evidence, no mandate on file, either principal's identity unverified,
an undisclosed dual-agency conflict of interest, a sanctions screening
suppressed to force a confirmation through, or a commission invoice
posted without a human sign-off.

## Feel the Decision Gate: `clojure -M:dev:run`

This vertical has no companion playable prototype. The fastest hands-on
way to feel why the `:commission-broker-governor` gate exists is the
bundled demo, which walks one clean brokerage-deal through intake →
verify → confirm → invoice (each confirm/invoice pausing for human
approval) and then exercises every HARD-hold failure mode in
isolation:

- a jurisdiction with no official spec-basis → HOLD (`:no-spec-basis`),
- a deal with no mandate/engagement-letter on file → HOLD
  (`:mandate-missing`),
- a deal where a principal's identity has not been verified → HOLD
  (`:principal-identity-unverified`),
- a deal where a principal has not passed sanctions screening → HOLD
  (`:counterparty-sanctions-flag-unresolved`),
- a dual-mandate deal with no disclosed dual-agency consent → HOLD
  (`:conflict-of-interest-undisclosed`),
- a double confirmation of the same deal → HOLD (`:already-confirmed`),
- a double commission-invoice of the same deal → HOLD
  (`:already-invoiced`).

Each HOLD settles at the governor node and never reaches a human
approver -- the same failure mode the audit ledger is built to catch and
the minimum trading controls above are built to prevent. It is not a
substitute for those controls, but it is the fastest way for a new
operator (or a reviewer) to feel, hands-on, why the gate exists before
touching a real deployment.

## Certification
Certified operators must prove spec-basis-grounded verification,
evidence-backed confirmation readiness (mandate-on-file, both-
principals identity verification, dual-agency disclosure, sanctions-
screening), and human review for every confirmation- and
invoice-affecting action.
