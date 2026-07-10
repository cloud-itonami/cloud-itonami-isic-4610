(ns commtrade.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean brokerage-deal
  through intake -> mandate verification -> deal confirmation
  (escalate/approve/commit) -> commission-invoice settlement
  (escalate/approve/commit), then shows HARD-hold scenarios: a
  jurisdiction with no spec-basis, a deal with no mandate on file, a
  deal where a principal's identity has not been verified, a deal
  where a principal has not passed sanctions screening, a dual-mandate
  deal with no disclosed dual-agency consent, a double confirmation,
  and a double commission-invoice.

  Like every sibling actor's domain checks, this actor's checks
  (`mandate-missing`, `principal-identity-unverified`,
  `conflict-of-interest-undisclosed`,
  `counterparty-sanctions-flag-unresolved`) are evaluated directly at
  `:deal/confirm` (and sanctions at `:commission/invoice` too) rather
  than via a separate screening op -- a real confirmation decision
  validates the mandate, both principals' identity, dual-agency
  disclosure and sanctions screening at the point of the act itself,
  not as a discrete pre-screening ceremony. Each check is still
  exercised directly and independently below, one deal per HARD-hold
  scenario, following the SAME 'exercise the failure mode directly,
  never only via a happy-path actuation' discipline `parksafety`'s
  ADR-2607071922 Decision 5 and every sibling since establish."
  (:require [langgraph.graph :as g]
            [commtrade.store :as store]
            [commtrade.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :trading-supervisor :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== mandate/intake bd-1 (JPN, seller-side mandate, clean) ==")
    (println (exec-op actor "t1" {:op :mandate/intake :subject "bd-1"
                                  :patch {:id "bd-1" :principal-seller "Sendai Rolling Mills Co"}} operator))

    (println "== mandate/verify bd-1 (escalates -- human approves) ==")
    (println (exec-op actor "t2" {:op :mandate/verify :subject "bd-1"} operator))
    (println (approve! actor "t2"))

    (println "== deal/confirm bd-1 (always escalates -- :deal/confirm) ==")
    (let [r (exec-op actor "t3" {:op :deal/confirm :subject "bd-1"} operator)]
      (println r)
      (println "-- human trading supervisor approves --")
      (println (approve! actor "t3")))

    (println "== commission/invoice bd-1 (always escalates -- :commission/invoice) ==")
    (let [r (exec-op actor "t4" {:op :commission/invoice :subject "bd-1"} operator)]
      (println r)
      (println "-- human trading supervisor approves --")
      (println (approve! actor "t4")))

    (println "== mandate/verify bd-2 (no spec-basis -> HARD hold) ==")
    (println (exec-op actor "t5" {:op :mandate/verify :subject "bd-2"} operator))

    (println "== mandate/verify bd-3 (escalates -- human approves; sets up the mandate-missing test) ==")
    (println (exec-op actor "t6" {:op :mandate/verify :subject "bd-3"} operator))
    (println (approve! actor "t6"))

    (println "== deal/confirm bd-3 (no mandate-terms on file -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :deal/confirm :subject "bd-3"} operator))

    (println "== mandate/verify bd-4 (escalates -- human approves; sets up the principal-identity test) ==")
    (println (exec-op actor "t8" {:op :mandate/verify :subject "bd-4"} operator))
    (println (approve! actor "t8"))

    (println "== deal/confirm bd-4 (buyer KYC not cleared -> HARD hold) ==")
    (println (exec-op actor "t9" {:op :deal/confirm :subject "bd-4"} operator))

    (println "== mandate/verify bd-5 (escalates -- human approves; sets up the sanctions test) ==")
    (println (exec-op actor "t10" {:op :mandate/verify :subject "bd-5"} operator))
    (println (approve! actor "t10"))

    (println "== deal/confirm bd-5 (seller sanctions screening not passed -> HARD hold) ==")
    (println (exec-op actor "t11" {:op :deal/confirm :subject "bd-5"} operator))

    (println "== mandate/verify bd-6 (escalates -- human approves; sets up the conflict-of-interest test) ==")
    (println (exec-op actor "t12" {:op :mandate/verify :subject "bd-6"} operator))
    (println (approve! actor "t12"))

    (println "== deal/confirm bd-6 (dual mandate, no disclosed dual-agency consent -> HARD hold) ==")
    (println (exec-op actor "t13" {:op :deal/confirm :subject "bd-6"} operator))

    (println "== deal/confirm bd-1 AGAIN (double-confirmation -> HARD hold) ==")
    (println (exec-op actor "t14" {:op :deal/confirm :subject "bd-1"} operator))

    (println "== commission/invoice bd-1 AGAIN (double-invoice -> HARD hold) ==")
    (println (exec-op actor "t15" {:op :commission/invoice :subject "bd-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft deal-confirmation records ==")
    (doseq [r (store/confirmation-history db)] (println r))

    (println "== draft commission-invoice records ==")
    (doseq [r (store/invoice-history db)] (println r))))
