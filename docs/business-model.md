# Business Model: Wholesale on a Fee or Contract Basis

## Classification
- Repository: `cloud-itonami-isic-4610`
- ISIC Rev.5: `4610` — wholesale on a fee or contract basis
- Domain: `commerce/commission-brokerage`
- Social impact: fiduciary duty, trade compliance, transparency
- Governor: `:commission-broker-governor`
- License: AGPL-3.0-or-later

## Scope
This actor covers mandate intake through per-jurisdiction commercial-
agency / dual-agency / sanctions regulatory verification, deal
confirmation between two principals (the broker's own record that a
matched deal has been arranged -- not a transfer of the underlying
goods or money, which settle directly between the two principals), and
commission-invoice settlement (the broker's OWN fee, invoiced to
whichever principal(s) owe it per the mandate terms) for a commission
broker / trade intermediary. It does **not**, by itself, hold any
brokerage licence or operating authority required to run a commission-
brokerage business in a given jurisdiction, perform or arrange the
underlying trade's physical delivery or payment settlement between the
two principals, or judge deal-matching economics (principal-matching /
deal-sourcing optimization is a follow-up slice, not this R0). Whoever
deploys a live instance supplies the jurisdiction-specific operating
authority, the real KYC / sanctions-screening vendor integrations, and
bears that jurisdiction's liability -- the software supplies the
governed, spec-cited, audited execution scaffold so the operator does
not have to build the compliance layer from scratch.

## Customer
- independent commission brokers, trade intermediaries and merchandise
  brokers
- import/export agents leaving closed brokerage / CRM SaaS
- SME manufacturers and producers who need a broker to find a
  counterparty without surrendering their own deal data
- principals, banks and regulators who need an auditable, spec-cited
  mandate and deal record

## Offer
- mandate intake and directory management -- who engaged the broker
  (buyer-side, seller-side, or both), and on what commission terms
- per-jurisdiction commercial-agency / dual-agency / sanctions
  regulatory verification with an official spec-basis citation
- deal confirmation (the broker's own record of a matched deal) gated
  on full evidence, a mandate on file, both principals' identity
  verified, disclosed dual-agency consent when applicable, and a
  passed sanctions screen on both principals
- commission-invoice settlement (the broker's own fee) with
  double-invoice prevention
- evidence checklisting (mandate/engagement-letter record, both
  principals' KYC records, sanctions-screening record covering both
  principals, dual-agency disclosure/consent record)
- conflict-of-interest and sanctions exception workflows
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per brokerage desk
- support retainer with SLA
- KYC / sanctions-screening vendor integration
- commission-schedule / mandate-template configuration service (out of
  scope for this R0's governed actuation, but a natural monetization
  surface on top of the audited mandate/deal book)

## The `:commission-broker-governor` Decision Rule

This blueprint's `:itonami.blueprint/governor` is `:commission-broker-
governor`. It is the single authority that stands between "a deal
between two principals could be confirmed" and "it is allowed to be
recorded as confirmed," and between "a commission invoice could be
settled" and "it is allowed to settle." Every rule it enforces is
traceable to the domain (Wholesale on a Fee or Contract Basis, ISIC
4610) and to the three `:social-impact` tags in `blueprint.edn`
(`:fiduciary-duty`, `:trade-compliance`, `:transparency`).

This is the rule the companion contract test
(`test/commtrade/governor_contract_test.clj`) encodes end-to-end: the
CommTradeAdvisor never confirms a deal between two principals or
settles a commission invoice the Commission Broker Governor would
reject, `:deal/confirm` and `:commission/invoice` NEVER auto-commit at
any phase, `:mandate/intake` (no direct fiduciary/sanctions risk) MAY
auto-commit when clean, and every decision (commit OR hold) leaves
exactly one ledger fact.

**Authorizes a deal confirmation (`:deal/confirm`) or commission-
invoice settlement (`:commission/invoice`) only when ALL of the
following hold:**

1. **An official spec-basis citation exists for the jurisdiction** -- the
   governor will not authorize any `:mandate/verify`, `:deal/confirm`,
   or `:commission/invoice` proposal whose jurisdiction has no entry in
   the `commtrade.facts` catalog (`:no-spec-basis`). This is the direct
   enforcement of `:transparency`: a jurisdiction whose commercial-
   agency / sanctions requirements cannot be traced to an OFFICIAL
   public source is never guessed. The advisor must not fabricate a
   jurisdiction's requirements.
2. **The jurisdiction's required evidence is fully on file** -- for a
   confirmation or invoice the deal's jurisdiction must have been
   verified with a complete counterparty-diligence + dual-agency
   evidence checklist on record: the mandate/engagement-letter record,
   both principals' KYC records, the sanctions-screening record
   covering both principals, and the dual-agency disclosure/consent
   record (`:evidence-incomplete`). This protects `:trade-compliance`:
   a deal that cannot prove principal and fiduciary diligence never
   confirms.
3. **A mandate/engagement-letter is on file** -- the governor refuses
   to confirm a deal the broker was never actually engaged to arrange,
   on terms never actually agreed (`:mandate-missing`). Evaluated at
   `:deal/confirm`.
4. **Both principals' identity has been verified** -- the governor
   reads the dedicated `:buyer-kyc-cleared?` and `:seller-kyc-cleared?`
   facts and refuses to confirm a deal unless BOTH are true, not just
   one counterparty (`:principal-identity-unverified`). Evaluated at
   `:deal/confirm`. This is the fiduciary analog of the principal-
   trading siblings' single-counterparty credit-clearance check, but
   evaluated on BOTH sides -- a broker owes duties to both principals
   of a deal it did not originate, unlike a principal trader who only
   needs to clear ITS OWN counterparty's credit.
5. **Dual agency (if any) has been disclosed and consented to** -- the
   governor reads the dedicated `:mandate-side` and `:dual-agency-
   disclosed?` facts and refuses to confirm a deal where the broker was
   engaged by BOTH the buyer and the seller (`:mandate-side :dual`)
   without a disclosure/consent record on file
   (`:conflict-of-interest-undisclosed`). Evaluated at `:deal/confirm`.
   **This is the check with NO analog in either principal-trading
   sibling** -- it is the classic brokerage-specific risk: acting for
   both sides of the same deal without disclosed dual-agency consent is
   the defining fiduciary-conflict failure mode of commission-agency
   law, with no equivalent in a principal trader who only ever
   represents its own book.
6. **Both principals have passed OFAC / equivalent sanctions
   screening** -- the governor reads the dedicated `:buyer-sanctions-
   screened?` and `:seller-sanctions-screened?` facts and treats an
   unresolved sanctions-screening flag on EITHER principal as a HARD,
   un-overridable hold (`:counterparty-sanctions-flag-unresolved`).
   Evaluated UNCONDITIONALLY at both `:deal/confirm` and `:commission/
   invoice`, and on BOTH principals -- unlike the principal-trading
   siblings, which screen a single counterparty, a broker introducing a
   sanctioned party to either side of a deal is itself a sanctions
   exposure even though it never takes title to the goods.
7. **The deal has not already been confirmed, and the commission has
   not already been invoiced** -- a double confirmation of the same
   deal is refused off a dedicated `:confirmed?` fact, and a double
   invoice off a dedicated `:invoiced?` fact (never a `:status` value),
   the double-actuation guard every sibling actor in this fleet
   enforces (`:already-confirmed` / `:already-invoiced`).

**Rejects (HOLD, un-overridable, never even reaches a human) when any of
the above fail.** A proposal with no spec-basis, incomplete evidence, no
mandate on file, an unverified principal, an undisclosed dual-agency
conflict of interest, an unresolved sanctions-screening flag on either
principal, or a double confirmation/invoice is held at the governor
node -- a human approver cannot override these, by construction.

**Always escalates to a human (never auto-commits) for `:deal/confirm`
and `:commission/invoice`**, even when every check above is clean.
Confirming a real matched deal between two principals and settling a
real commission invoice (real money moving from the mandating
principal(s) to the broker) are the two real-world actuation events
this actor performs; both are always a human trading supervisor's
call. This is enforced by TWO independent layers that agree on
purpose: the governor's confidence / actuation SOFT gate (a `:deal/
confirm` / `:commission/invoice` stake always escalates) and
`commtrade.phase`'s phase table, which never puts either op in any
phase's `:auto` set. The `:fiduciary-duty` tag is enforced upstream of
the governor, in the mandate-verification evidence step -- the
governor's job is confirmation/invoice authorization integrity, not
deal-sourcing optimization.

## Required Technologies

`blueprint.edn`'s `:itonami.blueprint/required-technologies` for this business,
and what each one is actually load-bearing for here (not a generic capability
list):

| Technology | What it is FOR in Wholesale on a Fee or Contract Basis |
|---|---|
| `:identity` | Broker, trading-supervisor and BOTH principals' identity plus role-based access, so the governor's sign-off is tied to *who* authorized a confirmation or invoice, not just *that* someone did -- and so `:buyer-kyc-cleared?`/`:seller-kyc-cleared?` are actually grounded in verified identity, not self-report. |
| `:forms` | Structured intake for mandate booking (who engaged the broker, on what commission terms), per-jurisdiction evidence capture (mandate/engagement-letter record, both principals' KYC records, sanctions-screening record, dual-agency disclosure/consent record), and conflict-of-interest / sanctions exception submission -- the data the Decision Rule above actually evaluates comes in through these forms. |
| `:dmn` | Encodes the `:commission-broker-governor` Decision Rule itself (spec-basis, evidence completeness, mandate-on-file, principal-identity verification, dual-agency disclosure, sanctions-screening, the double-actuation guards, the actuation gate) as an evaluable decision table rather than code buried in application logic -- this is what makes the governor auditable and swappable per-deployment. |
| `:bpmn` | Orchestrates the intake -> verify -> confirm -> invoice -> audit loop end-to-end (see `docs/operator-guide.md`) across mandate intake, mandate verification, deal confirmation, and commission-invoice settlement, including the dual-agency / sanctions escalation gate. |
| `:audit-ledger` | The immutable record of every verification, confirmation, invoice, conflict-of-interest flag, sanctions flag, and hold -- this is what "an auditable, spec-cited mandate and deal record for every confirmation and invoice" (Trust Controls, below) actually means in practice, and the evidence an operator needs if a confirmation or a commission invoice is later disputed by a principal or regulator. |
| `:optimization` | Principal-matching and deal-sourcing optimization -- finding the best counterparty match for a mandate. This R0 build deliberately scopes optimization OUT (see README `Business-process coverage`); the capability is correctly marked required, the integration is a follow-up slice. |

There is NO bespoke `:commtrade` capability library in this stack
(unlike the freight sibling's `:logistics`), and like the general-
trading sibling this vertical also carries NO `:robotics` technology
at all (see Robotics Premise, below): the commission-brokerage checks
(mandate-on-file, principal-identity, dual-agency disclosure,
sanctions-screening) are direct entity boolean reads in
`commtrade.governor`, on top of the generic identity/forms/dmn/bpmn/
audit-ledger stack (see Capability layer).

## Trust Controls
- a jurisdiction with no official spec-basis can never be verified,
  confirmed, or invoiced against
- a deal confirmation never starts with incomplete counterparty-
  diligence + dual-agency evidence
- a deal confirmation never starts with no mandate on file, either
  principal's identity unverified, an undisclosed dual-agency conflict
  of interest, or an unresolved sanctions-screening flag on either
  principal
- a commission invoice never settles against an unresolved sanctions-
  screening flag on either principal
- conflict-of-interest / sanctions / KYC flags cannot be silently
  suppressed
- the same deal can never be confirmed or invoiced twice
- a deal confirmation or commission invoice never auto-commits; both
  always need a human trading supervisor
- the broker never takes ownership/title of the goods it arranges deals
  for -- `:deal/confirm` is the broker's own record, never a transfer
  of the underlying goods or money between the two principals
- every confirmation and invoice (commit OR hold) leaves exactly one
  immutable ledger fact
- principal, mandate, credit, KYC and sanctions data stays outside Git

## Implementation notes (`:implemented`)

The Decision Rule above is implemented faithfully by
`commtrade.governor` as eight HARD checks (a human approver cannot
override them) plus one SOFT gate:

- `spec-basis-violations` -- the spec-basis check above, evaluated on
  every `:mandate/verify`, `:deal/confirm`, and `:commission/invoice`.
- `evidence-incomplete-violations` -- the evidence-completeness check
  above, for `:deal/confirm` / `:commission/invoice`.
- `mandate-missing-violations` -- the mandate-on-file check above;
  evaluated on every `:deal/confirm`.
- `principal-identity-unverified-violations` -- the both-principals
  identity-verification check above; evaluated on every `:deal/
  confirm`.
- `conflict-of-interest-undisclosed-violations` -- the dual-agency
  disclosure check above; evaluated on every `:deal/confirm`. This
  check has NO analog in the fuel-wholesale or general-trading
  siblings' governors -- it is this vertical's own domain content,
  reflecting a commission broker's defining fiduciary-conflict
  exposure.
- `counterparty-sanctions-flag-unresolved-violations` -- the sanctions-
  screening check above (the same open-flag-unresolved discipline the
  freight sibling's delivery-exception-unresolved check establishes),
  evaluated on BOTH principals; evaluated unconditionally on both
  `:deal/confirm` and `:commission/invoice`.
- `already-confirmed-violations` / `already-invoiced-violations` -- the
  double-actuation guards above, off dedicated `:confirmed?` /
  `:invoiced?` booleans (never a `:status` value), the same discipline
  every sibling governor's guards establish.
- the confidence floor / actuation SOFT gate -- low confidence, OR a
  `:deal/confirm` / `:commission/invoice` stake, escalates to a human;
  and `commtrade.phase` independently never auto-commits either op at
  any phase.

Unlike the crude-extraction sibling's governor (which calls pure
physical range-check functions in its registry), this governor needs no
range-check functions at all: its domain checks read the
`brokerage-deal` record's own dedicated booleans directly. `:deal/
confirm` and `:commission/invoice` are the two real-world actuation
events (`#{:deal/confirm :commission/invoice}`), applied SEQUENTIALLY
to the SAME brokerage-deal (confirmation first, commission-invoice
settlement later) rather than the retail sibling's `:kind`-
distinguished alternative-action shape -- the same sequential
dual-actuation shape the fuel-wholesale and general-trading siblings
use. Neither ever auto-commits at any phase. Principal-matching /
deal-sourcing optimization (the `:optimization` line above) is a
follow-up slice, not in this R0 build -- see README `Business-process
coverage`.

## Capability layer

Like the fuel-wholesale and general-trading siblings, this vertical is
SELF-CONTAINED: there is no `kotoba-lang/commtrade` to delegate
commission-brokerage validation to. The mandate-on-file / principal-
identity / dual-agency-disclosure / sanctions-screening checks live as
direct entity boolean reads in `commtrade.governor` (off dedicated
`:mandate-terms` / `:buyer-kyc-cleared?` / `:seller-kyc-cleared?` /
`:dual-agency-disclosed?` / `:buyer-sanctions-screened?` / `:seller-
sanctions-screened?` facts on the `brokerage-deal` record) -- this
vertical's governor needs no pure range-check functions at all
(contrast the crude sibling, whose registry hosts its physical range
checks), because its domain checks ARE direct boolean reads.

## Jurisdiction coverage (honest)

`commtrade.facts/catalog` currently seeds 4 jurisdictions with an
official spec-basis, each a REAL regime built on COMMERCIAL-AGENCY /
BROKERAGE law (not customs/excise or cross-border export control, the
fuel-wholesale and general-trading siblings' respective bases):

- **Japan** -- 商法 (Commercial Code, Act No. 48 of 1899) 仲立営業
  (brokerage business), Articles 543-550, whose Article 543 directly
  defines 仲立人 (nakadachinin) as "one who, as a business, mediates
  commercial transactions between others" -- a direct statutory
  definition of a commission broker/intermediary; economic-sanctions
  payment restrictions separately administered under 外国為替及び外国
  貿易法 (FEFTA) by MOF's International Bureau (verified against
  `https://laws.e-gov.go.jp/law/132AC0000000048` and MOF's own
  economic-sanctions pages).
- **United Kingdom** -- The Commercial Agents (Council Directive)
  Regulations 1993 (SI 1993/3053), implementing EU Council Directive
  86/653/EEC, governing a self-employed intermediary with continuing
  authority to negotiate (or negotiate and conclude, in the principal's
  name) the sale or purchase of goods on behalf of a principal --
  preserved as UK assimilated law post-Brexit and still in force
  (verified against `https://www.legislation.gov.uk/uksi/1993/3053/contents`).
- **Germany** -- Handelsgesetzbuch (HGB) §§84-92c (Handelsvertreter --
  commercial agent), also implementing Directive 86/653/EEC, governing
  a self-employed trader continuously engaged in mediating or
  concluding business for another entrepreneur -- the same commission-
  agency shape (verified against
  `https://www.gesetze-im-internet.de/hgb/__84.html`).
- **United States** -- the Perishable Agricultural Commodities Act
  (PACA), 7 U.S.C. §499a et seq., administered by USDA's Agricultural
  Marketing Service (AMS), which federally LICENSES "commission
  merchants, dealers, and brokers" of perishable agricultural
  commodities by name -- PACA's own "broker" definition ("a person
  engaged in negotiating sales and purchases ... either for or on
  behalf of the seller or buyer") is close to an exact match for ISIC
  4610 (verified against `https://www.ams.usda.gov/rules-regulations/paca`
  and 7 U.S.C. §499a on Cornell LII). **This entry is honestly scoped**:
  PACA covers only ONE commodity category (perishable agricultural
  commodities), not a general federal licence for every US
  commission-agent wholesale trade -- much of the rest is state-
  regulated or unregulated by license, a genuine, honestly-flagged
  coverage gap, not a claim that this entry covers all US commission
  brokerage.

This is a starting catalog to prove the governor contract end-to-end,
not a claim of global coverage (4 of ~194 jurisdictions worldwide).
Adding a jurisdiction is additive: one map entry in
`commtrade.facts/catalog`, citing a real official source -- never
fabricate a jurisdiction's requirements to make coverage look bigger.

## Maturity

`:implemented` -- `CommTradeAdvisor` + `Commission Broker Governor` run
as real, tested code (`clojure -M:dev:test`: see repository test
output for current counts; lint clean), following the SAME
governed-actor architecture as the other prior actors across this
fleet, with its own distinct, independently-named governor and its own
direct-entity-boolean commission-brokerage checks (including the
conflict-of-interest-undisclosed check with no analog in any sibling).
See `docs/adr/0001-architecture.md` for the design.

## Robotics Premise

`blueprint.edn` sets `:itonami.blueprint/robotics false`, and
`:itonami.blueprint/required-technologies` does NOT list `:robotics`
at all -- a deliberate, honest departure from the fuel-wholesale
sibling's `robotics true`, following the SAME reasoning shape as the
general-trading sibling's own `robotics false`. A commission broker
has an even weaker physical-domain claim than a general/diversified
trading house: it does not operate a fixed physical asset comparable
to a fuel-wholesale rack, AND it does not even perform the general-
trading sibling's own `:shipment/dispatch` logistics-coordination
referral (handing goods to a freight forwarder) -- a commission
broker's entire actuation surface is arranging and confirming a deal
on paper/systems and invoicing its own fee, with the underlying goods
and payment moving directly between the two principals, entirely
outside this actor's operating boundary. There is no loading-rack
valve, no cargo-handling apparatus, and no logistics-coordination
referral this actor's governor could gate a robot command against.

This follows real, existing precedent in this fleet rather than
inventing an exception: `cloud-itonami-isic-4690` (non-specialized
wholesale trade) sets `robotics false` with closely analogous
reasoning ("a `:shipment/dispatch` here is a logistics-coordination
referral ... there is no robot this actor's governor is in a position
to gate"), itself explicitly following `cloud-itonami-isic-6910`
(Global Incorporation Actor) and `cloud-itonami-isic-6310` (HR SaaS)'s
own precedent of being out of scope for the robotics-premise retrofit
(ADR-2607011000). A commission broker's core governed lifecycle here
-- mandate intake, principal verification, dual-agency disclosure,
sanctions screening, commission-invoice settlement -- is the same
category of digital/paperwork compliance work as those precedents,
with BOTH physical acts (delivery of the goods, and the underlying
trade's payment settlement) explicitly happening directly between the
two principals, entirely outside this actor's operating boundary.
