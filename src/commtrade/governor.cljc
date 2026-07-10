(ns commtrade.governor
  "Commission Broker Governor -- the independent compliance layer that
  earns the CommTradeAdvisor the right to commit. The LLM has no
  notion of jurisdictional commission-brokerage / commercial-agency
  law, whether both principals' identity has actually been verified,
  whether a mandate/engagement letter with commission terms is
  actually on file, whether acting for BOTH sides of the same deal has
  actually been disclosed and consented to, whether OFAC / equivalent
  sanctions screening has actually been passed for BOTH principals, or
  when an act stops being a draft and becomes a real deal confirmation
  or a real commission-invoice settlement, so this MUST be a separate
  system able to *reject* a proposal and fall back to HOLD.

  Like the fuel-wholesale and general-trading siblings, this
  commission-brokerage vertical has NO pre-existing brokerage
  capability library to delegate to -- so the domain checks (mandate-
  on-file, principal-identity-verification, dual-agency disclosure,
  sanctions-screening) are direct entity boolean reads off the
  `brokerage-deal` record, evaluated directly here, NOT delegated to a
  separate library's validated function.

  `:itonami.blueprint/governor` is `:commission-broker-governor`, grep-
  verified UNIQUE fleet-wide -- no naming-collision precedent
  question, a fresh independent build following the SAME governed-
  actor architecture (langgraph StateGraph + independent Governor +
  Phase 0->3 rollout) established by `cloud-itonami-isic-6511` and
  applied by the fuel-wholesale (`cloud-itonami-isic-4671`) and
  general-trading (`cloud-itonami-isic-4690`) siblings.

  CRITICAL STRUCTURAL DIFFERENCE from both principal-trading siblings:
  a commission broker never takes ownership/title of the goods and is
  never a party to the underlying sale's settlement -- it is engaged by
  one or both principals to ARRANGE a deal for a fee. `:deal/confirm`
  therefore does NOT move goods or money between the two principals (it
  is the broker's own record that a matched deal has been arranged and
  confirmed); `:commission/invoice` invoices the broker's OWN fee,
  never the underlying trade's settlement. This is why
  `credit-uncleared` (the fuel-wholesale/general-trading siblings'
  counterparty-credit check, relevant because THEY extend goods/credit
  as a principal) has NO analog here: a broker never extends credit to
  a principal, so there is nothing to clear. In its place, this
  vertical's OWN defining regulatory content is fiduciary: has the
  broker actually verified BOTH principals' identity (not just one
  counterparty, since the broker owes duties to both sides of a deal
  it did not originate), and -- the check with NO analog in either
  principal-trading sibling -- has acting for BOTH sides of the SAME
  deal (dual agency) been disclosed and consented to?

  Six checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them. The confidence/actuation gate is SOFT: it asks
  a human to look (low confidence / actuation), and the human may
  approve -- but see `commtrade.phase`: for `:stake :deal/confirm`/
  `:commission/invoice` (a real deal confirmation or commission-invoice
  settlement) NO phase ever allows auto-commit either. Two independent
  layers agree that actuation is always a human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source
                                       (`commtrade.facts`), or invent one?
    2. Evidence incomplete         -- for `:deal/confirm`/`:commission/
                                       invoice`, has the jurisdiction
                                       actually been verified with a full
                                       counterparty-diligence + dual-
                                       agency evidence checklist on file?
    3. Mandate missing             -- for `:deal/confirm`, no mandate /
                                       engagement-letter (commission-
                                       terms) is on file for the deal.
                                       Evaluated before confirmation.
    4. Principal identity
       unverified                    -- for `:deal/confirm`, BOTH
                                       principals' KYC has NOT been
                                       cleared (not just one -- a broker
                                       owes duties to both sides of a
                                       deal it did not originate).
                                       Evaluated before confirmation.
    5. Conflict of interest
       undisclosed                   -- for `:deal/confirm`, the mandate
                                       is `:dual` (the broker was engaged
                                       by BOTH the buyer and the seller
                                       for the SAME deal) and dual-agency
                                       disclosure/consent has NOT been
                                       recorded -- a HARD, un-overridable
                                       hold. THIS check has NO analog in
                                       either principal-trading sibling:
                                       it is this vertical's own defining
                                       fiduciary-conflict content, not a
                                       rename of an existing check.
    6. Counterparty sanctions flag
       unresolved                    -- for `:deal/confirm` and
                                       `:commission/invoice`, EITHER
                                       principal has NOT passed OFAC /
                                       equivalent sanctions screening --
                                       a HARD, un-overridable hold,
                                       evaluated on BOTH principals (not
                                       just one counterparty, unlike the
                                       principal-trading siblings).
                                       Evaluated UNCONDITIONALLY at both
                                       actuation ops.
    7. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:deal/confirm`/
                                       `:commission/invoice` (REAL acts)
                                       -> escalate.

  Two more guards, double-confirmation/double-invoice prevention, are
  enforced but NOT listed as numbered HARD checks above because they
  need no upstream comparison at all -- `already-confirmed-violations`/
  `already-invoiced-violations` refuse to confirm/invoice the SAME
  brokerage-deal twice, off dedicated `:confirmed?`/`:invoiced?` facts
  (never a `:status` value) -- the SAME 'check a dedicated boolean, not
  status' discipline every prior governor's guards establish, informed
  by `cloud-itonami-isic-6492`'s status-lifecycle bug (ADR-2607071320)."
  (:require [commtrade.facts :as facts]
            [commtrade.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Confirming a real matched deal between two principals (the broker's
  own record of arranging the deal -- NOT a transfer of goods or money
  between the principals, who settle the underlying trade directly
  between themselves) and settling a real commission invoice (the
  broker's OWN fee, real money moving from the mandating principal(s)
  to the broker) are the two real-world actuation events this actor
  performs -- a two-member set, matching every sibling's own
  dual-actuation shape."
  #{:deal/confirm :commission/invoice})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:mandate/verify` (or `:deal/confirm`/`:commission/invoice`)
  proposal with no spec-basis citation is a HARD violation -- never
  invent a jurisdiction's commission-brokerage / sanctions
  requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:mandate/verify :deal/confirm :commission/invoice} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:deal/confirm`/`:commission/invoice`, the jurisdiction's
  required counterparty-diligence + dual-agency evidence (mandate/
  engagement-letter record, both principals' KYC records, sanctions-
  screening record covering both principals, dual-agency disclosure/
  consent record) must actually be satisfied -- do not trust the
  advisor's self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:deal/confirm :commission/invoice} op)
    (let [bd (store/brokerage-deal st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction bd) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(委任状/契約条件書・両当事者KYC記録・両当事者制裁スクリーニング記録・双方代理開示同意記録)が充足していない状態での提案"}]))))

(defn- mandate-missing-violations
  "For `:deal/confirm`, refuses to confirm a deal when no mandate /
  engagement-letter (commission-terms) is on file for the brokerage-
  deal -- a broker never confirms a deal it was never actually engaged
  to arrange, on terms that were never actually agreed."
  [{:keys [op subject]} st]
  (when (= op :deal/confirm)
    (let [bd (store/brokerage-deal st subject)]
      (when (or (nil? (:mandate-terms bd)) (= "" (:mandate-terms bd)))
        [{:rule :mandate-missing
          :detail (str subject " に委任状/契約条件書(mandate-terms)の記録が無い -- 成約確認提案は進められない")}]))))

(defn- principal-identity-unverified-violations
  "For `:deal/confirm`, refuses to confirm a deal unless BOTH
  principals' KYC has been cleared -- not just one counterparty, unlike
  the principal-trading siblings' single-counterparty credit check. A
  commission broker owes fiduciary/diligence duties to BOTH sides of a
  deal it did not originate, so verifying only the mandating principal
  is insufficient."
  [{:keys [op subject]} st]
  (when (= op :deal/confirm)
    (let [bd (store/brokerage-deal st subject)]
      (when-not (and (true? (:buyer-kyc-cleared? bd)) (true? (:seller-kyc-cleared? bd)))
        [{:rule :principal-identity-unverified
          :detail (str subject " の買主・売主いずれかの本人確認(KYC)が未了 -- 成約確認提案は進められない")}]))))

(defn- conflict-of-interest-undisclosed-violations
  "For `:deal/confirm`, refuses to confirm a deal where the broker was
  engaged by BOTH the buyer and the seller (`:mandate-side :dual`)
  unless dual-agency disclosure/consent has been recorded. THIS is the
  check with NO analog in the fuel-wholesale or general-trading
  siblings' governors -- it is this vertical's own defining fiduciary-
  conflict content: acting for both sides of the same deal without each
  principal's informed, disclosed consent is the classic brokerage
  conflict-of-interest failure mode (the whole reason commercial-agency
  law, e.g. the fuel-wholesale sibling's fuel-excise analog here being
  the Commercial Agents Regulations 1993 / HGB Handelsvertreter regime,
  treats undisclosed dual agency as a fiduciary breach)."
  [{:keys [op subject]} st]
  (when (= op :deal/confirm)
    (let [bd (store/brokerage-deal st subject)]
      (when (and (= :dual (:mandate-side bd))
                 (not (true? (:dual-agency-disclosed? bd))))
        [{:rule :conflict-of-interest-undisclosed
          :detail (str subject " は双方代理(mandate-side :dual)だが開示・同意記録が無い -- 成約確認提案は進められない")}]))))

(defn- counterparty-sanctions-flag-unresolved-violations
  "For `:deal/confirm` and `:commission/invoice`, an unresolved
  sanctions-screening flag on EITHER principal -- the buyer or the
  seller has NOT passed OFAC / equivalent sanctions screening -- is a
  HARD, un-overridable hold. Evaluated UNCONDITIONALLY at both
  actuation ops, and on BOTH principals (unlike the principal-trading
  siblings, which screen a single counterparty): a broker introducing a
  sanctioned party to either side of a deal is itself a sanctions
  exposure, even though the broker never takes title to the goods."
  [{:keys [op subject]} st]
  (when (contains? #{:deal/confirm :commission/invoice} op)
    (let [bd (store/brokerage-deal st subject)]
      (when-not (and (true? (:buyer-sanctions-screened? bd)) (true? (:seller-sanctions-screened? bd)))
        [{:rule :counterparty-sanctions-flag-unresolved
          :detail (str subject " の買主・売主いずれかの制裁スクリーニング(OFAC等)が未了 -- 成約確認・請求提案は進められない")}]))))

(defn- already-confirmed-violations
  "For `:deal/confirm`, refuses to confirm the SAME brokerage-deal
  twice, off a dedicated `:confirmed?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :deal/confirm)
    (when (store/deal-already-confirmed? st subject)
      [{:rule :already-confirmed
        :detail (str subject " は既に成約確認済み")}])))

(defn- already-invoiced-violations
  "For `:commission/invoice`, refuses to settle the SAME brokerage-
  deal's commission invoice twice, off a dedicated `:invoiced?` fact
  (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :commission/invoice)
    (when (store/deal-already-invoiced? st subject)
      [{:rule :already-invoiced
        :detail (str subject " は既に請求済み")}])))

(defn check
  "Censors a CommTradeAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (mandate-missing-violations request st)
                           (principal-identity-unverified-violations request st)
                           (conflict-of-interest-undisclosed-violations request st)
                           (counterparty-sanctions-flag-unresolved-violations request st)
                           (already-confirmed-violations request st)
                           (already-invoiced-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
