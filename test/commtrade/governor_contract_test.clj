(ns commtrade.governor-contract-test
  "The governor contract as executable tests. The single invariant
  under test:

    CommTradeAdvisor never confirms a deal between two principals or
    settles a commission invoice the Commission Broker Governor would
    reject, `:deal/confirm`/`:commission/invoice` NEVER auto-commit at
    any phase, `:mandate/intake` (no direct fiduciary/sanctions risk)
    MAY auto-commit when clean, and every decision (commit OR hold)
    leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [commtrade.store :as store]
            [commtrade.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :trading-supervisor :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through mandate verify -> approve, leaving a mandate
  assessment on file. Uses distinct thread-ids per call site by
  suffixing `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :mandate/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :mandate/intake :subject "bd-1"
                   :patch {:id "bd-1" :principal-seller "Sendai Rolling Mills Co"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Sendai Rolling Mills Co" (:principal-seller (store/brokerage-deal db "bd-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest mandate-verify-always-needs-approval
  (testing "mandate verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :mandate/verify :subject "bd-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "bd-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a mandate/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :mandate/verify :subject "bd-2"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "bd-2")) "no assessment written"))))

(deftest confirm-without-assessment-is-held
  (testing "deal/confirm before any mandate verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :deal/confirm :subject "bd-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest mandate-missing-is-held-and-unoverridable
  (testing "a deal with no mandate/engagement-letter on file -> HOLD, and never reaches request-approval"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "bd-3")
          res (exec-op actor "t5" {:op :deal/confirm :subject "bd-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:mandate-missing} (-> (store/ledger db) last :basis)))
      (is (empty? (store/confirmation-history db))))))

(deftest principal-identity-unverified-is-held-and-unoverridable
  (testing "a deal where the buyer's identity has not been verified -> HOLD, and never reaches request-approval -- checked on BOTH principals, unlike a single-counterparty credit check"
    (let [[db actor] (fresh)
          _ (verify! actor "t6pre" "bd-4")
          res (exec-op actor "t6" {:op :deal/confirm :subject "bd-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:principal-identity-unverified} (-> (store/ledger db) last :basis)))
      (is (empty? (store/confirmation-history db))))))

(deftest counterparty-sanctions-flag-unresolved-is-held-and-unoverridable
  (testing "a deal where a principal has not passed OFAC / equivalent sanctions screening -> HOLD, and never reaches request-approval (evaluated at both confirm and invoice, on both principals)"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "bd-5")
          res (exec-op actor "t7" {:op :deal/confirm :subject "bd-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:counterparty-sanctions-flag-unresolved} (-> (store/ledger db) last :basis)))
      (is (empty? (store/confirmation-history db))))))

(deftest conflict-of-interest-undisclosed-is-held-and-unoverridable
  (testing "a dual-mandate deal with no disclosed dual-agency consent -> HOLD, and never reaches request-approval -- the check with NO analog in either principal-trading sibling"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "bd-6")
          res (exec-op actor "t8" {:op :deal/confirm :subject "bd-6"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:conflict-of-interest-undisclosed} (-> (store/ledger db) last :basis)))
      (is (empty? (store/confirmation-history db))))))

(deftest deal-confirm-always-escalates-then-human-decides
  (testing "a clean, fully-verified, both-principals-KYC-cleared, mandate-on-file, sanctions-screened deal still ALWAYS interrupts for human approval -- :deal/confirm is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "bd-1")
          r1 (exec-op actor "t9" {:op :deal/confirm :subject "bd-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, confirmation record drafted"
        (let [r2 (approve! actor "t9")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:confirmed? (store/brokerage-deal db "bd-1"))))
          (is (= 1 (count (store/confirmation-history db))) "one draft confirmation record"))))))

(deftest commission-invoice-always-escalates-then-human-decides
  (testing "a clean, fully-verified, already-confirmed deal still ALWAYS interrupts for human approval -- :commission/invoice is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t10pre" "bd-1")
          _ (exec-op actor "t10confirm" {:op :deal/confirm :subject "bd-1"} operator)
          _ (approve! actor "t10confirm")
          r1 (exec-op actor "t10" {:op :commission/invoice :subject "bd-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, commission-invoice record drafted"
        (let [r2 (approve! actor "t10")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:invoiced? (store/brokerage-deal db "bd-1"))))
          (is (= 1 (count (store/invoice-history db))) "one draft commission-invoice record"))))))

(deftest deal-confirm-double-confirmation-is-held
  (testing "confirming the same brokerage-deal twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t11pre" "bd-1")
          _ (exec-op actor "t11a" {:op :deal/confirm :subject "bd-1"} operator)
          _ (approve! actor "t11a")
          res (exec-op actor "t11" {:op :deal/confirm :subject "bd-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-confirmed} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/confirmation-history db))) "still only the one earlier confirmation"))))

(deftest commission-invoice-double-invoice-is-held
  (testing "settling the same brokerage-deal's commission invoice twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t12pre" "bd-1")
          _ (exec-op actor "t12confirm" {:op :deal/confirm :subject "bd-1"} operator)
          _ (approve! actor "t12confirm")
          _ (exec-op actor "t12a" {:op :commission/invoice :subject "bd-1"} operator)
          _ (approve! actor "t12a")
          res (exec-op actor "t12" {:op :commission/invoice :subject "bd-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-invoiced} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/invoice-history db))) "still only the one earlier commission invoice"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :mandate/intake :subject "bd-1"
                          :patch {:id "bd-1" :principal-seller "Sendai Rolling Mills Co"}} operator)
      (exec-op actor "b" {:op :mandate/verify :subject "bd-2"} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
