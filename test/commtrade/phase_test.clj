(ns commtrade.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:deal/confirm`/`:commission/invoice` must NEVER be a
  member of any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [commtrade.phase :as phase]))

(deftest deal-confirm-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in any future entry, auto-commits a real deal confirmation"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :deal/confirm))
          (str "phase " n " must not auto-commit :deal/confirm")))))

(deftest commission-invoice-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in any future entry, auto-commits a real commission-invoice settlement"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :commission/invoice))
          (str "phase " n " must not auto-commit :commission/invoice")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-risk-ops
  (testing ":mandate/intake carries no direct fiduciary/sanctions risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:mandate/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :mandate/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :deal/confirm} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :commission/invoice} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :mandate/intake} :commit)))))
