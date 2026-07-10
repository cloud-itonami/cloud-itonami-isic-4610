(ns commtrade.store
  "SSoT for the commission-brokerage actor, behind a `Store` protocol
  so the backend is a swap, not a rewrite -- the same seam every prior
  `cloud-itonami-isic-*` actor in this fleet uses.

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/commtrade/store_contract_test.clj), which is the whole point:
  the actor, the Commission Broker Governor and the audit ledger never
  know which SSoT they run on.

  Unlike the fuel-wholesale sibling's `fuel-order` or the general-
  trading sibling's `trade-order` -- both PRINCIPAL entities where the
  actor's OWN organization is a party to the trade -- a
  `brokerage-deal` here names TWO separate principals
  (`:principal-buyer`/`:principal-seller`) the broker was engaged to
  arrange a deal between, plus its own `:mandate-side` (which
  principal(s) actually engaged the broker, and on what commission
  terms). `:deal/confirm` and `:commission/invoice` apply SEQUENTIALLY
  to the SAME `brokerage-deal` -- a deal confirmation happens first
  (the broker's own record that the two principals' deal has been
  arranged; the underlying goods and money move directly between the
  two principals, never through the broker), commission-invoice
  settlement happens later (the broker invoices its OWN fee), on the
  same deal record. Dedicated double-actuation-guard booleans
  (`:confirmed?`/`:invoiced?`, never a `:status` value) enforce the
  same discipline every sibling governor's guards establish.

  The ledger stays append-only on every backend: 'which brokerage-deal
  was verified for a jurisdiction with no official spec-basis, which
  deal had no mandate on file, which deal had an unverified principal,
  which deal was an undisclosed dual-agency, which deal had an
  unresolved sanctions-screening flag on either principal, which deal
  was confirmed, which commission invoice was settled, on what
  jurisdictional basis, approved by whom' is always a query over an
  immutable log -- the audit trail a regulator, a principal, or an
  operator trusting a commission-brokerage actor needs, and the
  evidence an operator needs if a confirmation or a commission invoice
  is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [commtrade.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (brokerage-deal [s id])
  (all-brokerage-deals [s])
  (assessment-of [s deal-id] "committed mandate assessment, or nil")
  (ledger [s])
  (confirmation-history [s] "the append-only deal-confirmation history (commtrade.registry drafts)")
  (invoice-history [s] "the append-only commission-invoice history (commtrade.registry drafts)")
  (next-confirmation-sequence [s jurisdiction] "next confirmation-number sequence for a jurisdiction")
  (next-invoice-sequence [s jurisdiction] "next invoice-number sequence for a jurisdiction")
  (deal-already-confirmed? [s deal-id] "has this deal already been confirmed?")
  (deal-already-invoiced? [s deal-id] "has this deal's commission already been invoiced?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-brokerage-deals [s deals] "replace/seed the brokerage-deal directory (map id->deal)"))

;; ----------------------------- demo data -----------------------------

(defn- base-deal
  "The neutral, clean brokerage-deal shape (every field in its safe
  state), so each demo deal below isolates exactly ONE failure mode by
  overriding a single field."
  [overrides]
  (merge {:id "bd-1" :deal-id "BD-2026-0001" :subject-matter "Steel coil, 5000 MT"
          :principal-buyer "Northgate Steel Buyers KK" :principal-seller "Sendai Rolling Mills Co"
          :commission-rate "2% of transaction value, payable by the mandating principal on confirmation"
          :mandate-side :seller-side :mandate-terms "Sole-mandate engagement letter, seller-side, dated 2026-01-05"
          :buyer-kyc-cleared? true :seller-kyc-cleared? true
          :buyer-sanctions-screened? true :seller-sanctions-screened? true
          :dual-agency-disclosed? false
          :confirmed? false :invoiced? false
          :jurisdiction "JPN" :status :intake
          :confirmation-number nil :invoice-number nil}
         overrides))

(defn demo-data
  "A small, self-contained brokerage-deal set covering both actuation
  lifecycles (deal confirmation, commission-invoice settlement) plus
  the Commission Broker Governor's own checks, so the actor + tests run
  offline. Each violation deal isolates exactly ONE failure mode (the
  rest stay clean) following the 'exercise the failure mode directly,
  never only via a happy-path actuation' discipline every sibling
  governor's demo data establishes."
  []
  {:brokerage-deals
   (into {}
         (for [d [(base-deal {:id "bd-1" :deal-id "BD-2026-0001"})
                  (base-deal {:id "bd-2" :deal-id "BD-2026-0002"
                              :subject-matter "Industrial fasteners, mixed lot"
                              :principal-buyer "Atlantis Fasteners Ltd" :principal-seller "Cedar Metalworks Co"
                              :jurisdiction "ATL"})
                  (base-deal {:id "bd-3" :deal-id "BD-2026-0003"
                              :subject-matter "Recycled aluminium ingot, 200 MT"
                              :principal-buyer "Delta Alloys BV" :principal-seller "Eagle Smelting SA"
                              :mandate-terms nil})
                  (base-deal {:id "bd-4" :deal-id "BD-2026-0004"
                              :subject-matter "Machine tool spares, mixed lot"
                              :principal-buyer "Falcon Precision KK" :principal-seller "Granite Toolworks Co"
                              :buyer-kyc-cleared? false})
                  (base-deal {:id "bd-5" :deal-id "BD-2026-0005"
                              :subject-matter "Frozen provisions, 40 MT"
                              :principal-buyer "Harbor Provisions Ltd" :principal-seller "Ironwood Foods Co"
                              :seller-sanctions-screened? false})
                  (base-deal {:id "bd-6" :deal-id "BD-2026-0006"
                              :subject-matter "Specialty chemicals, 10 MT"
                              :principal-buyer "Juniper Chemicals SA" :principal-seller "Kestrel Compounds BV"
                              :mandate-side :dual :mandate-terms "Dual-mandate engagement letters, both principals, dated 2026-01-09"
                              :dual-agency-disclosed? false})]]
           [(:id d) d]))})

;; ----------------------------- shared commit logic -----------------------------

(defn- confirm-deal!
  "Backend-agnostic `:deal/mark-confirmed` -- looks up the brokerage-
  deal via the protocol and drafts the deal-confirmation record, and
  returns {:result .. :deal-patch ..} for the caller to persist."
  [s deal-id]
  (let [bd (brokerage-deal s deal-id)
        seq-n (next-confirmation-sequence s (:jurisdiction bd))
        result (registry/register-confirmation-record deal-id (:jurisdiction bd) seq-n)]
    {:result result
     :deal-patch {:confirmed? true
                  :confirmation-number (get result "confirmation_number")}}))

(defn- invoice-deal!
  "Backend-agnostic `:deal/mark-invoiced` -- looks up the brokerage-deal
  via the protocol and drafts the commission-invoice record, and
  returns {:result .. :deal-patch ..} for the caller to persist."
  [s deal-id]
  (let [bd (brokerage-deal s deal-id)
        seq-n (next-invoice-sequence s (:jurisdiction bd))
        result (registry/register-invoice-record deal-id (:jurisdiction bd) seq-n)]
    {:result result
     :deal-patch {:invoiced? true
                  :invoice-number (get result "invoice_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (brokerage-deal [_ id] (get-in @a [:brokerage-deals id]))
  (all-brokerage-deals [_] (sort-by :id (vals (:brokerage-deals @a))))
  (assessment-of [_ deal-id] (get-in @a [:assessments deal-id]))
  (ledger [_] (:ledger @a))
  (confirmation-history [_] (:confirmations @a))
  (invoice-history [_] (:invoices @a))
  (next-confirmation-sequence [_ jurisdiction] (get-in @a [:confirmation-sequences jurisdiction] 0))
  (next-invoice-sequence [_ jurisdiction] (get-in @a [:invoice-sequences jurisdiction] 0))
  (deal-already-confirmed? [_ deal-id] (boolean (get-in @a [:brokerage-deals deal-id :confirmed?])))
  (deal-already-invoiced? [_ deal-id] (boolean (get-in @a [:brokerage-deals deal-id :invoiced?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :mandate/upsert
      (swap! a update-in [:brokerage-deals (:id value)] merge value)

      :mandate-assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :deal/mark-confirmed
      (let [deal-id (first path)
            {:keys [result deal-patch]} (confirm-deal! s deal-id)
            jurisdiction (:jurisdiction (brokerage-deal s deal-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:confirmation-sequences jurisdiction] (fnil inc 0))
                       (update-in [:brokerage-deals deal-id] merge deal-patch)
                       (update :confirmations registry/append result))))
        result)

      :deal/mark-invoiced
      (let [deal-id (first path)
            {:keys [result deal-patch]} (invoice-deal! s deal-id)
            jurisdiction (:jurisdiction (brokerage-deal s deal-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:invoice-sequences jurisdiction] (fnil inc 0))
                       (update-in [:brokerage-deals deal-id] merge deal-patch)
                       (update :invoices registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-brokerage-deals [s deals] (when (seq deals) (swap! a assoc :brokerage-deals deals)) s))

(defn seed-db
  "A MemStore seeded with the demo brokerage-deal set. The deterministic default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {}
                           :ledger [] :confirmation-sequences {} :confirmations []
                           :invoice-sequences {} :invoices []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (assessment payloads, ledger facts, confirmation/
  invoice records) are stored as EDN strings so `langchain.db` doesn't
  expand them into sub-entities -- the same convention every sibling
  actor's store uses."
  {:brokerage-deal/id                    {:db/unique :db.unique/identity}
   :assessment/deal-id                   {:db/unique :db.unique/identity}
   :ledger/seq                           {:db/unique :db.unique/identity}
   :confirmation/seq                     {:db/unique :db.unique/identity}
   :invoice/seq                          {:db/unique :db.unique/identity}
   :confirmation-sequence/jurisdiction   {:db/unique :db.unique/identity}
   :invoice-sequence/jurisdiction        {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

;; Every brokerage-deal field is stored as its own Datomic attr so a
;; governor pull reads the exact ground truth (no blob decode). Boolean
;; fields are coerced on read so a missing attr reads back as false
;; (parity with MemStore). Keyword fields (`:mandate-side`) are stored
;; as their `str` and read back with `keyword` so a pull round-trips
;; the same value MemStore holds natively. [field-key tx-attr kind]
(def ^:private deal-fields
  [[:id :brokerage-deal/id :string]
   [:deal-id :brokerage-deal/deal-id :string]
   [:subject-matter :brokerage-deal/subject-matter :string]
   [:principal-buyer :brokerage-deal/principal-buyer :string]
   [:principal-seller :brokerage-deal/principal-seller :string]
   [:commission-rate :brokerage-deal/commission-rate :string]
   [:mandate-side :brokerage-deal/mandate-side :keyword]
   [:mandate-terms :brokerage-deal/mandate-terms :string]
   [:buyer-kyc-cleared? :brokerage-deal/buyer-kyc-cleared? :bool]
   [:seller-kyc-cleared? :brokerage-deal/seller-kyc-cleared? :bool]
   [:buyer-sanctions-screened? :brokerage-deal/buyer-sanctions-screened? :bool]
   [:seller-sanctions-screened? :brokerage-deal/seller-sanctions-screened? :bool]
   [:dual-agency-disclosed? :brokerage-deal/dual-agency-disclosed? :bool]
   [:confirmed? :brokerage-deal/confirmed? :bool]
   [:invoiced? :brokerage-deal/invoiced? :bool]
   [:jurisdiction :brokerage-deal/jurisdiction :string]
   [:status :brokerage-deal/status :keyword]
   [:confirmation-number :brokerage-deal/confirmation-number :string]
   [:invoice-number :brokerage-deal/invoice-number :string]])

(defn- deal->tx [bd]
  (reduce (fn [tx [k attr kind]]
            (let [v (get bd k)]
              (cond-> tx (some? v) (assoc attr (if (= kind :keyword) (str v) v)))))
          {:brokerage-deal/id (:id bd)}
          deal-fields))

(def ^:private deal-pull (mapv second deal-fields))

(defn- pull->deal [m]
  (when (:brokerage-deal/id m)
    (reduce (fn [bd [k attr kind]]
              (let [v (get m attr)]
                (cond
                  (= kind :bool)    (assoc bd k (boolean v))
                  (and (= kind :keyword) (some? v)) (assoc bd k (keyword (subs v 1)))
                  (some? v)         (assoc bd k v)
                  :else             bd)))
            {:id (:brokerage-deal/id m)}
            deal-fields)))

(defrecord DatomicStore [conn]
  Store
  (brokerage-deal [_ id]
    (pull->deal (d/pull (d/db conn) deal-pull [:brokerage-deal/id id])))
  (all-brokerage-deals [_]
    (->> (d/q '[:find [?id ...] :where [?e :brokerage-deal/id ?id]] (d/db conn))
         (map #(pull->deal (d/pull (d/db conn) deal-pull [:brokerage-deal/id %])))
         (sort-by :id)))
  (assessment-of [_ deal-id]
    (dec* (d/q '[:find ?p . :in $ ?did
                :where [?a :assessment/deal-id ?did] [?a :assessment/payload ?p]]
              (d/db conn) deal-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (confirmation-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :confirmation/seq ?s] [?e :confirmation/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (invoice-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :invoice/seq ?s] [?e :invoice/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-confirmation-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :confirmation-sequence/jurisdiction ?j] [?e :confirmation-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-invoice-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :invoice-sequence/jurisdiction ?j] [?e :invoice-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (deal-already-confirmed? [s deal-id]
    (boolean (:confirmed? (brokerage-deal s deal-id))))
  (deal-already-invoiced? [s deal-id]
    (boolean (:invoiced? (brokerage-deal s deal-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :mandate/upsert
      (d/transact! conn [(deal->tx value)])

      :mandate-assessment/set
      (d/transact! conn [{:assessment/deal-id (first path) :assessment/payload (enc payload)}])

      :deal/mark-confirmed
      (let [deal-id (first path)
            {:keys [result deal-patch]} (confirm-deal! s deal-id)
            jurisdiction (:jurisdiction (brokerage-deal s deal-id))
            next-n (inc (next-confirmation-sequence s jurisdiction))]
        (d/transact! conn
                     [(deal->tx (assoc deal-patch :id deal-id))
                      {:confirmation-sequence/jurisdiction jurisdiction :confirmation-sequence/next next-n}
                      {:confirmation/seq (count (confirmation-history s)) :confirmation/record (enc (get result "record"))}])
        result)

      :deal/mark-invoiced
      (let [deal-id (first path)
            {:keys [result deal-patch]} (invoice-deal! s deal-id)
            jurisdiction (:jurisdiction (brokerage-deal s deal-id))
            next-n (inc (next-invoice-sequence s jurisdiction))]
        (d/transact! conn
                     [(deal->tx (assoc deal-patch :id deal-id))
                      {:invoice-sequence/jurisdiction jurisdiction :invoice-sequence/next next-n}
                      {:invoice/seq (count (invoice-history s)) :invoice/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-brokerage-deals [s deals]
    (when (seq deals) (d/transact! conn (mapv deal->tx (vals deals)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:brokerage-deals ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [brokerage-deals]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-brokerage-deals s brokerage-deals))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo brokerage-deal set -- the
  Datomic-backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
