(ns commtrade.registry
  "Pure-function deal-confirmation + commission-invoice record
  construction -- an append-only commission-brokerage book-of-record
  draft.

  Like the fuel-wholesale and general-trading siblings' own registries,
  this commission-brokerage vertical's Commission Broker Governor needs
  NO registry range-check functions at all: its domain checks
  (mandate-missing, principal-identity-unverified, conflict-of-
  interest-undisclosed, counterparty-sanctions-flag-unresolved) are
  direct entity boolean reads in `commtrade.governor`, off dedicated
  `:mandate-terms` / `:buyer-kyc-cleared?` / `:seller-kyc-cleared?` /
  `:dual-agency-disclosed?` / `:buyer-sanctions-screened?` / `:seller-
  sanctions-screened?` facts on the `brokerage-deal` record. So this
  namespace is RECORD CONSTRUCTION ONLY -- no pure range checks to host
  here.

  Like every sibling actor's registry, there is no single international
  reference-number standard for a deal-confirmation or a commission-
  invoice record -- every operator/jurisdiction assigns its own
  reference format. This namespace does NOT invent one beyond a
  jurisdiction-scoped sequence number; it validates the record's
  required fields, the same honest, non-fabricating discipline
  `commtrade.facts` uses.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real settlement or billing system. It builds the RECORD
  an operator would keep, not the act of confirming a real matched deal
  or settling a real commission invoice itself (that is `commtrade.
  operation`'s `:deal/confirm`/`:commission/invoice`, always human-
  gated -- see README `Actuation`). CRITICALLY, `register-confirmation-
  record` does NOT move goods or money between the two principals --
  they settle the underlying trade directly between themselves; it is
  only the broker's own record that a matched deal has been arranged
  and confirmed."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the operator's act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

;; ----------------------------- record construction -----------------------------

(defn register-confirmation-record
  "Validate + construct the DEAL-CONFIRMATION registration DRAFT -- the
  broker's own legal act of recording that a matched deal between the
  two principals has been arranged and confirmed. Pure function -- does
  NOT touch any real settlement, delivery or payment system between the
  two principals (they settle the underlying trade directly between
  themselves); it builds the RECORD an operator would keep.
  `commtrade.governor` independently re-verifies the mandate-on-file,
  both principals' KYC, dual-agency-disclosure and sanctions-screening
  ground truth, and blocks a double-confirmation of the same brokerage-
  deal, before this is ever allowed to commit."
  [deal-id jurisdiction sequence]
  (when-not (and deal-id (not= deal-id ""))
    (throw (ex-info "deal-confirmation: deal_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "deal-confirmation: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "deal-confirmation: sequence must be >= 0" {})))
  (let [confirmation-number (str (str/upper-case jurisdiction) "-CONFIRM-" (zero-pad sequence 6))
        record {"record_id" confirmation-number
                "kind" "deal-confirmation-draft"
                "deal_id" deal-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "confirmation_number" confirmation-number
     "certificate" (unsigned-certificate "DealConfirmation" confirmation-number confirmation-number)}))

(defn register-invoice-record
  "Validate + construct the COMMISSION-INVOICE registration DRAFT --
  the broker's own legal act of invoicing its OWN fee (never the
  underlying trade's settlement, which happens directly between the two
  principals). Pure function -- does not touch any real billing or
  accounts-receivable system; it builds the RECORD an operator would
  keep. `commtrade.governor` independently re-verifies the sanctions-
  screening (both principals) and evidence-completeness ground truth,
  and blocks a double-invoice of the same brokerage-deal, before this
  is ever allowed to commit."
  [deal-id jurisdiction sequence]
  (when-not (and deal-id (not= deal-id ""))
    (throw (ex-info "commission-invoice: deal_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "commission-invoice: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "commission-invoice: sequence must be >= 0" {})))
  (let [invoice-number (str (str/upper-case jurisdiction) "-COMMISSION-" (zero-pad sequence 6))
        record {"record_id" invoice-number
                "kind" "commission-invoice-draft"
                "deal_id" deal-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "invoice_number" invoice-number
     "certificate" (unsigned-certificate "CommissionInvoice" invoice-number invoice-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
