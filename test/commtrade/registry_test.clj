(ns commtrade.registry-test
  (:require [clojure.test :refer [deftest is]]
            [commtrade.registry :as r]))

;; The commission-brokerage domain checks (mandate-on-file, principal-
;; identity, dual-agency disclosure, sanctions-screening) are direct
;; entity booleans in the governor, NOT pure registry range functions --
;; so this registry has NO range-check suite to test (unlike the crude-
;; extraction sibling's reservoir/annular/water-cut/H2S functions). Only
;; record construction is here.

;; ----------------------------- register-confirmation-record -----------------------------

(deftest confirmation-is-a-draft-not-a-real-settlement
  (let [result (r/register-confirmation-record "bd-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest confirmation-assigns-confirmation-number
  (let [result (r/register-confirmation-record "bd-1" "JPN" 7)]
    (is (= (get result "confirmation_number") "JPN-CONFIRM-000007"))
    (is (= (get-in result ["record" "deal_id"]) "bd-1"))
    (is (= (get-in result ["record" "kind"]) "deal-confirmation-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest confirmation-validation-rules
  (is (thrown? Exception (r/register-confirmation-record "" "JPN" 0)))
  (is (thrown? Exception (r/register-confirmation-record "bd-1" "" 0)))
  (is (thrown? Exception (r/register-confirmation-record "bd-1" "JPN" -1))))

;; ----------------------------- register-invoice-record -----------------------------

(deftest invoice-is-a-draft-not-a-real-invoice
  (let [result (r/register-invoice-record "bd-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest invoice-assigns-invoice-number
  (let [result (r/register-invoice-record "bd-1" "JPN" 7)]
    (is (= (get result "invoice_number") "JPN-COMMISSION-000007"))
    (is (= (get-in result ["record" "deal_id"]) "bd-1"))
    (is (= (get-in result ["record" "kind"]) "commission-invoice-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest invoice-validation-rules
  (is (thrown? Exception (r/register-invoice-record "" "JPN" 0)))
  (is (thrown? Exception (r/register-invoice-record "bd-1" "" 0)))
  (is (thrown? Exception (r/register-invoice-record "bd-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-confirmation-record "bd-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-confirmation-record "bd-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-CONFIRM-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-CONFIRM-000001" (get-in hist2 [1 "record_id"])))))
