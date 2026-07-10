# ADR-0001: CommTradeAdvisor ⊣ Commission Broker Governor architecture

## Status

Accepted. `cloud-itonami-isic-4610` published `:implemented` in this
fleet's governed-actor architecture.

## Context

`cloud-itonami-isic-4610` publishes an OSS business blueprint for
wholesale on a fee or contract basis (mandate intake, per-jurisdiction
commercial-agency / dual-agency / sanctions regulatory verification,
deal confirmation between two principals, and commission-invoice
settlement) -- commission agents / trade brokers / wholesale
intermediaries who arrange the sale or purchase of goods on behalf of
others for a fee or commission, **WITHOUT ever taking ownership/title
of the goods themselves**. This is the single structural fact that
distinguishes ISIC 4610 from the two siblings already registered in
this fleet: `cloud-itonami-isic-4671` (FuelTradeAdvisor, wholesale of
solid/liquid/gaseous fuels, ISIC 4671) and `cloud-itonami-isic-4690`
(ShoshaAdvisor, non-specialized wholesale trade, ISIC 4690) -- BOTH of
those take PRINCIPAL positions (buy then resell; invoice settlement is
their own money). Like every prior actor in this fleet, the blueprint
alone is not an implementation: this ADR records the governed-actor
architecture that promotes it to real, tested code, following the same
langgraph StateGraph + independent Governor + Phase 0->3 rollout
pattern established by `cloud-itonami-isic-6511` (life insurance) and
applied by the fuel-wholesale and general-trading siblings.

Like both principal-trading siblings, this vertical has NO bespoke
domain capability library in `kotoba-lang` to wrap (verified: no
`kotoba-lang/commtrade`-style repo exists). This build therefore uses
self-contained domain logic -- the same pattern the majority of this
fleet's actors use. The commission-brokerage checks (mandate-on-file,
principal-identity verification, dual-agency-disclosure, sanctions-
screening) are direct entity boolean reads in `commtrade.governor`,
off dedicated `:mandate-terms` / `:buyer-kyc-cleared?` / `:seller-kyc-
cleared?` / `:dual-agency-disclosed?` / `:buyer-sanctions-screened?` /
`:seller-sanctions-screened?` facts on the `brokerage-deal` record --
NO pure range-check functions are needed.

This blueprint's own `:itonami.blueprint/governor` keyword,
`:commission-broker-governor`, is grep-verified UNIQUE fleet-wide (both
locally against every checked-out sibling's `blueprint.edn` and via the
GitHub API against the full `cloud-itonami` org) -- no naming-collision
precedent question, a fresh independent build.

## Decision

### Decision 1: fresh governor identity, no reuse precedent needed

`:commission-broker-governor` is grep-verified unique across every
`blueprint.edn` in this fleet. This build follows the SAME
governed-actor architecture as every prior actor, but with its own
distinct governor identity.

### Decision 2: self-contained domain logic, direct entity booleans (no `kotoba-lang/commtrade` to wrap, and no range-check functions to host)

Unlike `cloud-itonami-isic-4920` (freight, which delegates tracking-
number validation to a real, pre-existing `kotoba-lang/logistics`
capability library), and unlike the crude-extraction sibling (which
hosts pure physical range-check functions in its registry because its
governor re-verifies measured physical values), this commission-
brokerage vertical needs NEITHER: there is no pre-existing brokerage
capability library to delegate to, AND the governor's domain checks
(mandate-on-file, principal-identity, dual-agency disclosure,
sanctions-screening) are direct entity boolean reads off the
`brokerage-deal` record's own dedicated facts -- not measured-value-
vs-limit range comparisons. So `commtrade.registry` is RECORD
CONSTRUCTION ONLY (no range-check functions), and `commtrade.governor`
reads the deal's booleans directly. No literal code is shared with any
sibling (different domain), but the 'governor re-verifies against the
actor's own records before any real-world act' discipline is the same.

### Decision 3: dual-actuation shape, SEQUENTIAL on the SAME `brokerage-deal` entity, WITHOUT either actuation moving the underlying goods or money

Like the fuel-wholesale sibling's `fuel-order` entity and the
general-trading sibling's `trade-order` entity, this vertical's
`confirm` and `invoice` actuation events apply SEQUENTIALLY to the
SAME `brokerage-deal` -- a deal confirmation happens first, commission-
invoice settlement happens later, on the same deal record. `high-
stakes` is `#{:deal/confirm :commission/invoice}`; neither ever
auto-commits at any phase.

**The structural difference from both principal-trading siblings**:
`:deal/confirm` here does NOT correspond to goods leaving a rack
(`:delivery/dispatch`) or a shipment being handed to a freight
forwarder (`:shipment/dispatch`) -- it is the broker's OWN record that
a matched deal between two principals has been arranged and confirmed.
The underlying goods and the underlying trade's payment move directly
between the two principals, entirely outside this actor's operating
boundary; the broker was never a party to that settlement and never
takes ownership/title of the goods. `:commission/invoice` likewise
does NOT settle the underlying trade -- it invoices the broker's OWN
fee, payable by whichever principal(s) engaged the broker per the
mandate terms. This distinction (agency vs. principal trading) is
ISIC 4610's entire reason to exist as a SEPARATE classification from
4610-4669 and 4690, and this build encodes it structurally rather than
only in prose: `commtrade.registry/register-confirmation-record`'s own
docstring states explicitly that it does not move goods or money
between the principals.

### Decision 4: the commission-brokerage checks -- direct entity booleans, documented as such, PLUS one check with no analog in EITHER principal-trading sibling

The four domain checks the governor runs on `:deal/confirm` (mandate-
missing, principal-identity-unverified, conflict-of-interest-
undisclosed, and -- at both actuation ops -- counterparty-sanctions-
flag-unresolved) are each a direct boolean read off a dedicated fact
on the `brokerage-deal` record, documented as such rather than as
measured-value range comparisons:

- `mandate-missing` reads the dedicated `:mandate-terms` fact and
  refuses to confirm a deal when no mandate/engagement-letter is on
  file -- a broker never confirms a deal it was never actually engaged
  to arrange, on terms never actually agreed. This is the loose analog
  of the principal-trading siblings' `contract-missing` check, but the
  underlying fact is a MANDATE (an agency engagement), not a sale
  contract the actor's own organization is a party to.
- `principal-identity-unverified` reads the dedicated `:buyer-kyc-
  cleared?` AND `:seller-kyc-cleared?` facts and refuses to confirm a
  deal unless BOTH are true. **This is deliberately NOT a rename of the
  principal-trading siblings' single-counterparty `credit-uncleared`
  check** -- a commission broker never extends credit to a principal
  (it is not a party to the underlying trade's settlement), so there is
  nothing analogous to "credit clearance" to check. What the broker
  DOES owe is a duty to verify the identity of BOTH principals of a
  deal it did not originate (neither principal is "its own"
  counterparty the way a principal trader's single counterparty is) --
  a genuinely different regulatory concern (KYC/AML identity diligence
  on two independent parties), not a relabeled credit check.
- `conflict-of-interest-undisclosed` reads the dedicated `:mandate-
  side` and `:dual-agency-disclosed?` facts and refuses to confirm a
  deal where the broker was engaged by BOTH the buyer and the seller
  (`:mandate-side :dual`) without a disclosure/consent record on file.
  **This check has NO analog in EITHER principal-trading sibling's
  governor** -- it is this vertical's own domain content, not a find/
  replace rename of an existing check. A principal trader (fuel-
  wholesale or general-trading) only ever represents its OWN book: it
  buys, then it sells, and there is no "other side" it could also be
  secretly representing. A commission broker, by contrast, can be
  engaged by either or both principals to the SAME deal -- and acting
  for both sides without each principal's informed, disclosed consent
  is the CLASSIC brokerage-specific conflict-of-interest failure mode,
  the defining regulatory concern of commercial-agency / brokerage law
  that simply does not arise for a principal trader.
- `counterparty-sanctions-flag-unresolved` reads the dedicated
  `:buyer-sanctions-screened?` AND `:seller-sanctions-screened?` facts
  and treats an unresolved sanctions-screening flag on EITHER principal
  as a HARD hold, evaluated UNCONDITIONALLY at both `:deal/confirm` and
  `:commission/invoice`. Unlike the principal-trading siblings (which
  screen a single counterparty -- their own trading partner), this
  vertical screens BOTH principals, because a broker introducing a
  sanctioned party to either side of a deal is itself a sanctions
  exposure even though it never takes title to the goods. This reapplies
  the SAME open-flag-unresolved discipline the freight sibling's
  `delivery-exception-unresolved` check (and the fuel-wholesale /
  general-trading siblings' own sanctions checks) establish, extended
  to a two-party evaluation.

Each fires when the fact is provably in its unsafe state; missing /
false reads as a violation (cannot verify safe to confirm). No new
unconditional-evaluation ordinals are claimed beyond the sanctions
check: it is a discipline-reapplication, documented per Decision 5.

### Decision 5: `counterparty-sanctions-flag-unresolved?` -- the open-flag-unresolved discipline, extended to BOTH principals

An unresolved sanctions-screening flag on either principal -- the
buyer or the seller has not passed OFAC / equivalent sanctions
screening -- is a HARD, un-overridable hold. This reuses the SAME
open-flag-unresolved discipline the freight sibling's `delivery-
exception-unresolved?` check (and the fuel-wholesale / general-trading
siblings' own sanctions checks) establish -- an open concern cannot be
silently suppressed to force a confirmation or invoice through. The
EXTENSION to this build (evaluating BOTH principals rather than a
single counterparty) is documented explicitly as an honest structural
consequence of the agency model, not a new unconditional-evaluation
ordinal claim. Evaluated UNCONDITIONALLY at both `:deal/confirm` and
`:commission/invoice`.

### Decision 6: dedicated double-actuation-guard booleans

`:confirmed?` / `:invoiced?` are dedicated booleans on the
`brokerage-deal` record, never a single `:status` value -- the same
discipline every prior governor's guards establish, informed by
`cloud-itonami-isic-6492`'s real status-lifecycle bug
(ADR-2607071320).

### Decision 7: Store protocol, MemStore + DatomicStore parity

`commtrade.store/Store` is implemented by both `MemStore` (atom-backed,
default for dev/tests/demo) and `DatomicStore` (`langchain.db`-backed),
proven to satisfy the same contract in
`test/commtrade/store_contract_test.clj`. The `DatomicStore` also
round-trips the `:mandate-side`/`:status` KEYWORD fields (`:buyer-
side`/`:seller-side`/`:dual`, `:intake`) via a `str`/`keyword` codec,
since `langchain.db` transacts scalar attribute values and a keyword
needs an explicit encode/decode step to survive the EAV round-trip --
the one structural addition to the store contract this vertical needed
beyond the string/boolean fields the fuel-wholesale and general-trading
siblings' stores already handle. The ledger stays append-only on every
backend: which brokerage-deal was verified for a jurisdiction with no
official spec-basis, which deal had no mandate on file, which deal had
an unverified principal, which deal was an undisclosed dual-agency,
which deal had an unresolved sanctions-screening flag on either
principal, which deal was confirmed, which commission invoice was
settled, on what jurisdictional basis, approved by whom -- always a
query over an immutable log.

### Decision 8: Phase 0->3 with `:deal/confirm`/`:commission/invoice` NEVER auto

`commtrade.phase`'s phase table puts `:mandate/intake` (no direct
fiduciary/sanctions risk) in phase 3's `:auto` set as its only member;
`:deal/confirm` and `:commission/invoice` are deliberately ABSENT from
every phase's `:auto` set, including phase 3 -- a permanent structural
fact. `commtrade.governor`'s high-stakes gate enforces the same
invariant independently: two layers agree that actuation is always a
human trading supervisor's call.

### Decision 9: mock + LLM advisor pair

`commtrade.commtradeadvisor` provides a deterministic `mock-advisor`
(default, runs offline) and an `llm-advisor` backed by a
`langchain.model/ChatModel`. The LLM advisor's EDN proposal is parsed
defensively: any parse/shape failure yields a safe low-confidence noop
so the governor escalates/holds -- an LLM hiccup can never auto-confirm
a deal or auto-settle a commission invoice. The system prompt also
explicitly instructs the model that the broker never takes ownership of
the underlying goods and that a deal confirmation is a record, not a
transfer -- so the advisor cannot even draft language that implies
otherwise.

### Decision 10: `:robotics false`, following the ISIC 4690 / ISIC 6910 / ISIC 6310 precedent, NOT the fuel-wholesale sibling's `robotics true`

Like the general-trading sibling and unlike the fuel-wholesale
sibling, a commission broker does not operate a fixed physical asset
this actor's governor could gate a robot command against. This build
goes one step further than the general-trading sibling's own reasoning:
a commission broker does not even perform the general-trading
sibling's own `:shipment/dispatch` logistics-coordination referral
(handing goods to a licensed freight forwarder) -- ISIC 4610's entire
actuation surface is arranging and confirming a deal on paper/systems
and invoicing its own fee, with BOTH the underlying goods' delivery and
the underlying trade's payment settlement happening directly between
the two principals, entirely outside this actor's operating boundary.
`blueprint.edn` therefore sets `:itonami.blueprint/robotics false` and
OMITS `:robotics` from `:required-technologies` altogether, following
the REAL, existing precedent set by `cloud-itonami-isic-4690` (itself
following `cloud-itonami-isic-6910` and `cloud-itonami-isic-6310`)
rather than inventing a new exception. See `docs/business-model.md`'s
Robotics Premise section for the full reasoning.

## Alternatives considered

- **Wrapping a bespoke `kotoba-lang/commtrade` capability library.**
  Considered and explicitly ruled out: no such library exists. Forcing
  a false capability-library integration would be dishonest; this
  build correctly uses self-contained domain logic instead.
- **Hosting pure range-check functions in the registry** (as the crude
  sibling does). Considered and ruled out: the commission-brokerage
  domain checks are direct entity booleans (mandate on file? both
  principals verified? dual agency disclosed? both principals
  screened?), not measured-value-vs-limit range comparisons, so there
  are no range checks to host. `commtrade.registry` is record
  construction only.
- **Relabeling the principal-trading siblings' `credit-uncleared` check
  as this vertical's principal-identity check.** Rejected: a commission
  broker never extends credit to a principal (it is not a party to the
  underlying trade's settlement), so there is nothing analogous to
  "credit clearance" to check. The honest replacement is a genuinely
  different concern -- identity/KYC verification of BOTH principals of
  a deal the broker did not originate -- not a find/replace rename.
  Named `principal-identity-unverified` to make this explicit.
- **Folding dual-agency disclosure into the evidence checklist only,
  with no dedicated HARD check.** Considered: `commtrade.facts` already
  lists a dual-agency disclosure/consent record as required evidence,
  so `evidence-incomplete-violations` would catch a missing disclosure
  indirectly. Rejected as insufficient on its own, for the SAME reason
  the general-trading sibling's `export-license-uncleared` check was
  kept separate from its own evidence checklist: this fleet's
  convention is to surface each materially distinct compliance failure
  as its OWN named HARD check (with its own `:rule` keyword and its own
  test), not folding it silently into the generic evidence-completeness
  check -- and this genuinely new domain concern (dual-agency conflict
  of interest, unique to the agency model) most of all earns a
  genuinely new named check, `conflict-of-interest-undisclosed`.
- **A `:kind`-distinguished entity** (matching the retail sibling's
  `order` shape). Rejected: deal confirmation and commission-invoice
  settlement happen SEQUENTIALLY on the SAME brokerage-deal in this
  domain, not as alternative actions -- the fuel-wholesale / general-
  trading siblings' sequential shape is the honest match here.
- **Retaining `robotics true` or the general-trading sibling's
  `:shipment/dispatch`-style logistics-coordination referral by
  analogy.** Rejected: this vertical has neither a fixed physical
  asset NOR a logistics-coordination role -- the underlying goods'
  delivery and the underlying trade's payment settlement both happen
  directly between the two principals, entirely outside this actor's
  operating boundary. Retrofitting either premise would misrepresent
  an act this actor does not perform and cannot gate. The honest
  precedent is `cloud-itonami-isic-4690`/`6910`/`6310`'s `robotics
  false`.
- **Building principal-matching / deal-sourcing optimization in this
  R0.** Rejected in favor of a scoped R0 slice (the `:optimization`
  capability is correctly marked required, the integration is a
  follow-up), consistent with this fleet's 'extending coverage is
  additive' convention.

## Consequences

- Fresh independent actor in this fleet, following the SAME
  governed-actor architecture as every prior sibling.
- Establishes the commission-brokerage checks as direct entity boolean
  reads (no pure range-check functions needed), an honest structural
  differentiator from the crude-extraction sibling's registry-hosted
  physical range checks.
- Adds `conflict-of-interest-undisclosed` as a genuinely new HARD check
  with no analog in EITHER principal-trading sibling, reflecting this
  vertical's own defining regulatory exposure (dual-agency fiduciary
  conflict, the reason commission-brokerage/commercial-agency law
  exists as its own body of doctrine).
- Structurally encodes, in both code and docstrings (not only prose),
  that a commission broker never takes ownership/title of the goods
  and that `:deal/confirm` never moves goods or money between the two
  principals -- the single fact that distinguishes ISIC 4610 from ISIC
  4610-4669/4690's principal-trading siblings.
- Sets `robotics false` (omitting `:robotics` from
  `:required-technologies` entirely), following the `cloud-itonami-
  isic-4690`/`6910`/`6310` precedent rather than the fuel-wholesale
  sibling's `robotics true` -- an honest, precedent-grounded departure,
  not an oversight.
- `MemStore` || `DatomicStore` parity is proven by
  `test/commtrade/store_contract_test.clj`, including the keyword-field
  codec `:mandate-side`/`:status` needed for Datomic round-tripping.
- Lint is clean; the demo (`clojure -M:dev:run`) walks one clean
  confirmation + commission-invoice lifecycle, plus six HARD-hold
  scenarios (no spec-basis, mandate-missing, principal-identity-
  unverified, sanctions, conflict-of-interest-undisclosed, double
  confirmation, double invoice), end-to-end. See the repository's own
  `clojure -M:dev:test` output for current pass counts.

## References

- `cloud-itonami-isic-6511/docs/adr/0001-architecture.md` (origin of the
  general governed-actor architecture pattern)
- `cloud-itonami-isic-4671/docs/adr/0001-architecture.md` (fuel-
  wholesale sibling; contrast: `robotics true`, PRINCIPAL trading
  (takes ownership of the fuel), single-commodity excise basis, no
  dual-agency check)
- `cloud-itonami-isic-4690/docs/adr/0001-architecture.md` (general-
  trading sibling; this build's closest architectural precedent for
  the `robotics false` reasoning shape -- contrast: still PRINCIPAL
  trading (takes ownership across unrelated commodity categories),
  cross-border export-control basis, no dual-agency check)
- `cloud-itonami-isic-4920/docs/adr/0001-architecture.md` (freight
  sibling; contrast: wraps a pre-existing `kotoba-lang/logistics`
  capability library)
- `cloud-itonami-isic-0610/docs/adr/0001-architecture.md` (crude-
  extraction sibling; contrast: hosts pure physical range-check
  functions in its registry, which this vertical does NOT need)
- `cloud-itonami-isic-6910` / `cloud-itonami-isic-6310` (origin of the
  `robotics false` precedent this build follows, for digital/paperwork
  verticals with no actor-controlled physical asset)
- `cloud-itonami-isic-0162/docs/adr/0001-architecture.md` (origin of
  the 'honest reapplication, documented as such' convention this build
  follows for its sanctions open-flag-unresolved check)
- ADR-2607011000 (cloud-itonami robotics premise, and its documented
  ISIC 6310 exemption)
- 商法 (Commercial Code, Act No. 48 of 1899) 仲立営業 (543条-550条)
  (Japan, 法務省 / MOF 国際局 for FEFTA sanctions)
- The Commercial Agents (Council Directive) Regulations 1993 (SI
  1993/3053, implementing EU Council Directive 86/653/EEC) (UK,
  Department for Business and Trade / OFSI)
- Handelsgesetzbuch (HGB) §§84-92c (Handelsvertreter, implementing EU
  Council Directive 86/653/EEC) (Germany, Bundesministerium der Justiz
  / Deutsche Bundesbank)
- Perishable Agricultural Commodities Act (PACA), 7 U.S.C. §499a et
  seq. (USA, USDA Agricultural Marketing Service / OFAC)
